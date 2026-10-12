/* The bridge between the ECI engine and Android's synthesis callback.
 *
 * The engine hands samples over on a thread of its own and blocks in the
 * callback until the caller has taken them, so the shape here is a ring buffer
 * with a lock either side of it: the engine's thread fills it and Android's
 * synthesis thread drains it. That blocking is the point rather than a cost.
 * It paces the engine to whatever the audio track is taking, so a long
 * utterance does not have to be held in memory before any of it is heard.
 *
 * No JNI call is ever made from the engine's thread, which is why there is no
 * thread to attach and none to detach.
 */

#include <jni.h>
#include <stdint.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <android/log.h>

#include "eci.h"

#define TAG "freeeloquencetts"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* What the engine writes at a time, in samples, and what the ring holds, in
   bytes. The ring has to be bigger than one frame or the callback would wait
   for room that can never arrive.

   The frame is the whole of what a callback costs: the engine fills its output
   buffer and hands it over only when it is exactly full, so every FRAME_SAMPLES
   samples is one lock, one copy and one wake-up on the other side. It is also
   how much of the utterance has to be made before the first of it can be heard,
   and how often a stop is answered, since the abort is only noticed when the
   engine offers the next buffer. Measured at eleven thousand and twenty five
   hertz, two thousand and forty eight samples is under a fifth of a second of
   speech and about two milliseconds of this engine's time, so the first audio
   and the answer to a swipe both arrive sooner, and the callbacks over one
   utterance are still in the dozens. */
#define FRAME_SAMPLES 2048
#define FRAME_MAX     4096
#define RING_BYTES    (256 * 1024)

/* How much of a frame one read will carry, and so how big the buffer that
   carries it has to be. The ring is the ceiling: whatever is asked for beyond
   what the ring can hold is asked for in vain. */
#define READ_MAX      RING_BYTES

/* How big the buffer the text of an utterance is read into starts out. It grows
   beyond this only for a piece that needs more. */
#define TEXT_INITIAL  (64 * 1024)

/* How many milliseconds the end-of-utterance wait will spend watching for the
   engine to take the work up before it stops waiting for that. */
#define START_SPINS 200

/* How long speak waits for the previous utterance to leave the engine before
   it gives up and answers failure. The wait is normally milliseconds -- the
   worker is either already idle or unwinding an aborted utterance, which
   takes tens of milliseconds with the rules compiled -- so reaching this
   means the engine is wedged rather than busy, and the caller answers by
   giving the instance back instead of holding the synthesis thread. Short on
   purpose: this wait holds the caller's lock, so a stop landing inside it
   waits it out, and a swipe that waits is a swipe that feels late. Nothing is
   reset on the way out -- see the timeout below -- so a slow-but-healthy
   engine that did reach this would only cost its piece, never the instance. */
#define SPEAK_IDLE_MS 1500

/* How long destroy waits for the worker to come out of the engine before it
   stops waiting and leaks the instance instead. The wait is normally nothing
   at all -- the abort is what brings the worker out -- so reaching this means
   the engine is wedged inside eciSynchronize and joining it would never
   return. This call runs wherever the instance is given back, which includes
   the main thread, where a join that never returns is an app that stops
   responding and is killed. A leaked instance is memory; a hung main thread
   is the voice gone. */
#define DESTROY_JOIN_MS 5000

typedef struct {
	ECIHand         eci;
	pthread_mutex_t lock;
	pthread_cond_t  room;
	pthread_cond_t  filled;
	unsigned char*  ring;
	size_t          head;
	size_t          tail;
	size_t          count;
    size_t          ringbytes;
    int             framesamples;
	int             aborted;
	int             started;
	int             done;
	pthread_t       worker;
	pthread_cond_t  work;
	int             running;
	int             exited;
	int             mono;
	int             pending;
	int             busy;
	int             quitting;
	unsigned char*  text;
	size_t          textroom;
	unsigned char*  scratch;
	int             waiters;
	ECIDictHand     dict;
	short           frame[FRAME_MAX];
} instance;

static instance* unwrap(jlong handle) {
	return (instance*)(intptr_t)handle;
}

/* The moment, on the clock this instance's waits run on. Monotonic wherever
   the condition variables were made for it, because the wall clock jumps --
   daylight saving, the network stepping it backwards -- and a wait measured
   on a clock that jumps either never ends or ends at once, which is a hang
   or a lost utterance made out of nothing. */
static void now_for(instance* in, struct timespec* ts) {
	clock_gettime(in->mono ? CLOCK_MONOTONIC : CLOCK_REALTIME, ts);
}

static void add_millis(struct timespec* ts, long millis) {
	ts->tv_sec += millis / 1000;
	ts->tv_nsec += (millis % 1000) * 1000000L;
	if (ts->tv_nsec >= 1000000000L) {
		ts->tv_nsec -= 1000000000L;
		ts->tv_sec += 1;
	}
}

static void put(instance* in, const unsigned char* src, size_t n) {
	size_t first = in->ringbytes - in->head;
	if (first > n)
		first = n;
	memcpy(in->ring + in->head, src, first);
	memcpy(in->ring, src + first, n - first);
	in->head = (in->head + n) % in->ringbytes;
	in->count += n;
}

static void take(instance* in, unsigned char* dst, size_t n) {
	size_t first = in->ringbytes - in->tail;
	if (first > n)
		first = n;
	memcpy(dst, in->ring + in->tail, first);
	memcpy(dst + first, in->ring, n - first);
	in->tail = (in->tail + n) % in->ringbytes;
	in->count -= n;
}

/* What says an utterance is over, which took three goes to get right.
 *
 * eciSpeaking cannot be it. It answers whether anything is outstanding right
 * now, and a reader that drains the ring faster than the engine fills it sees
 * nought between two of the engine's own messages and calls an utterance
 * finished part way through.
 *
 * eciSynchronize alone cannot be it either. eciSynthesize only queues the work
 * and answers; asked before the synthesis thread has taken the utterance up,
 * eciSynchronize finds nothing outstanding and answers at once, and then the
 * utterance is over before it began. That is not a rare window -- it cost a
 * whole utterance about one time in four.
 *
 * So the wait is in two parts: first until the engine has visibly taken the
 * work, then eciSynchronize, which is exact once there is something for it to
 * wait on. Having taken it shows either as a buffer already delivered or as
 * eciSpeaking answering yes. Text that yields no audio at all shows as
 * neither, which is what the spin limit is for; it is the only case that waits
 * it out, and it waits a fifth of a second rather than for ever.
 *
 * It has to be its own thread because the thread that reads cannot block.
 *
 * It is one thread for the life of the instance rather than one per utterance,
 * and that is not tidiness. The engine has no thread of its own: asking it
 * anything is what makes it run, so whichever thread calls eciSynchronize is
 * the thread the engine runs on, and it took the callback with it. Handing it
 * a thread made fresh each time faults after about fifty-five utterances, on
 * the phone and in a plain C harness alike, with the machine jumping into its
 * own data. The same loop on one kept thread runs for hundreds. */
static void* worker_loop(void* data) {
	instance* in = (instance*)data;
	for (;;) {
		int spins;
		pthread_mutex_lock(&in->lock);
		while (!in->pending && !in->quitting)
			pthread_cond_wait(&in->work, &in->lock);
		if (in->quitting) {
			/* Said out loud, because destroy is waiting on exactly this:
			   the flag is what turns its join into one that returns at
			   once rather than one that might never return. */
			in->exited = 1;
			pthread_cond_broadcast(&in->work);
			pthread_mutex_unlock(&in->lock);
			return NULL;
		}
		in->pending = 0;
		pthread_mutex_unlock(&in->lock);
		/* Waiting to be told is better than being asked. The loop is the same
		   one it always was -- however long it takes, in milliseconds -- but
		   the first thing the callback hands over wakes it out of the wait
		   rather than the next poll finding it there, so the utterance is not
		   started on up to a millisecond late every single time. The engine
		   is asked first and the wait second, because the work is usually
		   already taken up by the time this runs and the first turn would
		   otherwise spend its millisecond waiting for an answer it already
		   had. */
		for (spins = 0; spins < START_SPINS; spins++) {
			int begun;
			if (eciSpeaking(in->eci))
				break;
			pthread_mutex_lock(&in->lock);
			begun = in->started || in->aborted;
			if (!begun) {
				struct timespec until;
				now_for(in, &until);
				add_millis(&until, 1);
				/* Counted as a waiter, because that is what the callback looks
				   for before it goes to the trouble of a wake-up. The read is
				   not the only thread that sleeps on this condition, and a
				   worker that is not counted is a worker that has to wait out
				   the millisecond instead. */
				in->waiters++;
				pthread_cond_timedwait(&in->filled, &in->lock, &until);
				in->waiters--;
			}
			pthread_mutex_unlock(&in->lock);
			if (begun)
				break;
		}
		eciSynchronize(in->eci);
		pthread_mutex_lock(&in->lock);
		in->done = 1;
		in->busy = 0;
		pthread_cond_broadcast(&in->filled);
		pthread_cond_broadcast(&in->work);
		pthread_mutex_unlock(&in->lock);
	}
}

/* Marks an utterance over that never started, so that a reader waiting on the
   end condition is not waiting for work nothing handed over. */
static jboolean finished(instance* in) {
	pthread_mutex_lock(&in->lock);
	in->done = 1;
	in->busy = 0;
	pthread_cond_broadcast(&in->filled);
	pthread_cond_broadcast(&in->work);
	pthread_mutex_unlock(&in->lock);
	return JNI_FALSE;
}

/* Waits for the worker to be out of the engine, which is what makes it safe to
   put the next utterance in. The lock must not be held. Answers 1 when the
   worker got out and 0 when the wait ran out first, which is the wedged
   engine the caller then gives back rather than queuing behind for ever. */
static int wait_until_idle(instance* in, long millis) {
	struct timespec until;

	now_for(in, &until);
	add_millis(&until, millis);
	pthread_mutex_lock(&in->lock);
	while (in->busy) {
		if (pthread_cond_timedwait(&in->work, &in->lock, &until) == ETIMEDOUT) {
			pthread_mutex_unlock(&in->lock);
			return 0;
		}
	}
	pthread_mutex_unlock(&in->lock);
	return 1;
}

/* Waits for the worker to have left its loop, which is what makes joining it
   return at once. The lock must not be held. Answers 1 when it left and 0
   when the wait ran out first, in which case the caller detaches the thread
   and leaks the instance rather than joining for ever. */
static int wait_until_exited(instance* in, long millis) {
	struct timespec until;

	now_for(in, &until);
	add_millis(&until, millis);
	pthread_mutex_lock(&in->lock);
	while (!in->exited) {
		if (pthread_cond_timedwait(&in->work, &in->lock, &until) == ETIMEDOUT) {
			pthread_mutex_unlock(&in->lock);
			return 0;
		}
	}
	pthread_mutex_unlock(&in->lock);
	return 1;
}

/* Waits on a condition for at so many milliseconds, and answers 1 when it was
   the clock that let the caller go rather than the condition. The lock has to
   be held, as it is with any wait on a condition. */
static int
wait_millis(instance* in, pthread_cond_t* cond, long millis)
{
	struct timespec until;

	now_for(in, &until);
	add_millis(&until, millis);
	return pthread_cond_timedwait(cond, &in->lock, &until) == ETIMEDOUT;
}

/* Runs on the engine's synthesis thread. It may not call back into the same
   instance, and it does not: everything it touches is ours. */
static int ECICALL on_message(ECIHand handle, ECIMessage message, int param, void* data) {
	instance* in = (instance*)data;
	size_t want;
	(void)handle;
	if (message != eciWaveformBuffer)
		return eciDataProcessed;
	want = (size_t)param * sizeof(short);
	/* Nothing the engine was asked for can be bigger than the ring, but a
	   buffer that big would wait for room that can never arrive and wedge
	   the worker on it, so anything out of range is dropped rather than
	   queued. In practice this never fires; if it ever does, the utterance
	   loses a frame rather than the engine losing the thread it runs on. */
	if (want == 0 || want > in->ringbytes)
		return eciDataProcessed;
	pthread_mutex_lock(&in->lock);
	while (!in->aborted && in->ringbytes - in->count < want)
		pthread_cond_wait(&in->room, &in->lock);
	if (in->aborted) {
		pthread_mutex_unlock(&in->lock);
		return eciDataAbort;
	}
	put(in, (const unsigned char*)in->frame, want);
	in->started = 1;
	/* Only wake what is waiting. A reader that has not got to the wait yet is
	   woken by nothing and a reader that has is woken by this, and the syscall
	   in the middle of the engine's own callback is the one thing in here that
	   runs a thousand times an utterance. */
	if (in->waiters > 0)
		pthread_cond_signal(&in->filled);
	pthread_mutex_unlock(&in->lock);
	return eciDataProcessed;
}

JNIEXPORT jlong JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_create(JNIEnv* env, jclass cls, jint language) {
	instance* in;
	(void)env;
	(void)cls;
	in = (instance*)calloc(1, sizeof *in);
	if (in == NULL)
		return 0;
	in->ring = (unsigned char*)malloc(RING_BYTES);
    in->ringbytes = RING_BYTES;
    in->framesamples = FRAME_SAMPLES;
	if (in->ring == NULL) {
		free(in);
		return 0;
	}
	in->text = (unsigned char*)malloc(TEXT_INITIAL);
	in->textroom = (in->text == NULL) ? 0 : TEXT_INITIAL;
	/* One buffer for every read, rather than one per read. It is copied into
	   under the lock and copied out of afterwards, which is the same two memcpys
	   there always were without a malloc and a free between them: at four
	   kilobytes at a time a long paragraph is a thousand of each, and none of
	   them was ever wanted. */
	in->scratch = (unsigned char*)malloc(READ_MAX);
	if (in->scratch == NULL) {
		free(in->ring);
		free(in);
		return 0;
	}
	in->eci = eciNewEx(language);
	if (in->eci == NULL_ECI_HAND) {
		LOGE("eciNewEx(%#x) gave no instance", language);
		free(in->scratch);
		free(in->text);
		free(in->ring);
		free(in);
		return 0;
	}
	pthread_mutex_init(&in->lock, NULL);
	/* The waits run on the monotonic clock, which is the one that never
	   jumps. Where the platform refuses that clock the waits fall back to
	   the wall clock they always ran on, which is still a working engine
	   on a phone whose time never moves under it. */
	{
		pthread_condattr_t attr;
		pthread_condattr_init(&attr);
		in->mono = pthread_condattr_setclock(&attr, CLOCK_MONOTONIC) == 0;
		pthread_cond_init(&in->room, &attr);
		pthread_cond_init(&in->filled, &attr);
		pthread_cond_init(&in->work, &attr);
		pthread_condattr_destroy(&attr);
	}
	eciRegisterCallback(in->eci, on_message, in);
	if (!eciSetOutputBuffer(in->eci, FRAME_SAMPLES, in->frame)) {
		LOGE("the engine refused the output buffer");
		eciDelete(in->eci);
		pthread_cond_destroy(&in->room);
		pthread_cond_destroy(&in->filled);
		pthread_cond_destroy(&in->work);
		pthread_mutex_destroy(&in->lock);
		free(in->scratch);
		free(in->text);
		free(in->ring);
		free(in);
		return 0;
	}
	if (pthread_create(&in->worker, NULL, worker_loop, in) != 0) {
		LOGE("no thread to run the engine on");
		eciDelete(in->eci);
		pthread_cond_destroy(&in->room);
		pthread_cond_destroy(&in->filled);
		pthread_cond_destroy(&in->work);
		pthread_mutex_destroy(&in->lock);
		free(in->scratch);
		free(in->text);
		free(in->ring);
		free(in);
		return 0;
	}
	in->running = 1;
	return (jlong)(intptr_t)in;
}

JNIEXPORT void JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_destroy(JNIEnv* env, jclass cls, jlong handle) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	if (in == NULL)
		return;
	pthread_mutex_lock(&in->lock);
	in->aborted = 1;
	in->quitting = 1;
	pthread_cond_broadcast(&in->room);
	pthread_cond_broadcast(&in->filled);
	pthread_cond_broadcast(&in->work);
	pthread_mutex_unlock(&in->lock);
	/* The abort flag is what brings the worker out of the engine: the next
	   buffer it is offered is answered eciDataAbort and eciSynchronize
	   returns. Nothing is asked of the engine from this thread.
	   The worker is waited for, but not for ever: a wedged engine never
	   comes out of eciSynchronize, and a join that never returns on the
	   thread that gives instances back is an app that stops responding.
	   Past the wait the thread is detached and the instance is leaked --
	   untouched, so that a worker which does come out later still finds
	   its lock and its engine where it left them -- and the next utterance
	   opens a fresh one instead of queuing behind this one for ever. */
	if (in->running) {
		if (wait_until_exited(in, DESTROY_JOIN_MS)) {
			pthread_join(in->worker, NULL);
			in->running = 0;
		} else {
			LOGE("the engine's thread never came out; the instance is leaked, not waited for");
			pthread_detach(in->worker);
			return;
		}
	}
	/* A dictionary that is still in force when the instance goes is what
	   crashed eciDelete: it walks the dictionaries it thinks are attached and
	   walks off the one that has already gone. So the set is taken out of
	   force before it is deleted, the same order forgetDictionaries uses. */
	if (in->dict != NULL_DICT_HAND) {
		eciSetDict(in->eci, NULL_DICT_HAND);
		eciDeleteDict(in->eci, in->dict);
		in->dict = NULL_DICT_HAND;
	}
	eciDelete(in->eci);
	pthread_cond_destroy(&in->room);
	pthread_cond_destroy(&in->filled);
	pthread_cond_destroy(&in->work);
	pthread_mutex_destroy(&in->lock);
	free(in->text);
	free(in->scratch);
	free(in->ring);
	free(in);
}

/* Hands the text over and starts the engine. It answers at once; the samples
   come out of read below.
 *
 * The text is bytes in the language's own code set rather than UTF-16, which
 * the engine allows only for Chinese, Japanese and Korean; Kotlin has already
 * turned it into that set. It is kept until the next utterance because nothing
 * here knows whether the engine copied it. */
/* Configure between requests only, after the persistent worker has left ECI.
   Storage stays at the maximum; changing the bound adds no allocation to reads. */
JNIEXPORT jboolean JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_configureOutput(JNIEnv* env, jclass cls, jlong handle, jint samples, jint ringbytes) {
    instance* in = unwrap(handle);
    (void)env; (void)cls;
    if (in == NULL || samples < 128 || samples > FRAME_MAX || (samples & (samples - 1)) != 0
        || ringbytes < 16 * 1024 || ringbytes > RING_BYTES || ringbytes < samples * 2)
        return JNI_FALSE;
    if (in->framesamples == samples && in->ringbytes == (size_t)ringbytes)
        return JNI_TRUE;
    if (!wait_until_idle(in, SPEAK_IDLE_MS)) return JNI_FALSE;
    if (!eciSetOutputBuffer(in->eci, samples, in->frame)) return JNI_FALSE;
    pthread_mutex_lock(&in->lock);
    in->framesamples = samples;
    in->ringbytes = (size_t)ringbytes;
    in->head = in->tail = in->count = 0;
    pthread_mutex_unlock(&in->lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_speak(JNIEnv* env, jclass cls, jlong handle, jbyteArray text) {
	instance*      in = unwrap(handle);
	jsize          n;
	unsigned char* buf;
	(void)cls;
	if (in == NULL || text == NULL)
		return JNI_FALSE;
	n = (*env)->GetArrayLength(env, text);
	if (n <= 0)
		return JNI_FALSE;
	/* A piece is hundreds of bytes; anything past a quarter of a megabyte is
	   a caller that lost its way, and refusing it is cheaper than feeding the
	   engine a text that long -- not least because the buffer below grows to
	   hold whatever it is given, and would keep the room afterwards. */
	if (n > 256 * 1024) {
		LOGE("the text is too long to speak (%d bytes)", (int)n);
		return JNI_FALSE;
	}
	if (!wait_until_idle(in, SPEAK_IDLE_MS)) {
		/* The worker is still inside the engine from the utterance before,
		   which means the engine is wedged rather than busy: unwinding an
		   aborted utterance takes milliseconds. Nothing here is reset --
		   marking this utterance over while that thread is still inside the
		   engine would let the next one queue behind it, and two threads in
		   eci calls at once is how an instance faults. The caller answers by
		   giving the instance back, which is what unblocks the next
		   utterance instead of holding it behind this one. */
		LOGE("the engine never left the previous utterance");
		return JNI_FALSE;
	}
	pthread_mutex_lock(&in->lock);
	in->aborted = 0;
	in->started = 0;
	in->done = 0;
	in->busy = 1;
	in->head = in->tail = in->count = 0;
	pthread_mutex_unlock(&in->lock);
	/* The text is kept until the next utterance, so the buffer is kept too and
	   the bytes are read into it where they lie: a paragraph is a thousand
	   pieces and a thousand mallocs, a thousand frees and a thousand copies
	   that bought nothing. It can only be done after wait_until_idle, since the
	   engine may still be reading the text it was given last. */
	if (in->textroom < (size_t)n + 1) {
		free(in->text);
		in->text = (unsigned char*)malloc((size_t)n + 1);
		in->textroom = (in->text == NULL) ? 0 : (size_t)n + 1;
	}
	if (in->text == NULL) {
		LOGE("no room for the text");
		return finished(in);
	}
	buf = in->text;
	(*env)->GetByteArrayRegion(env, text, 0, n, (jbyte*)buf);
	buf[n] = 0;
	if (!in->running) {
		LOGE("there is no worker to run the engine");
		return finished(in);
	}
	if (!eciAddText(in->eci, buf)) {
		LOGE("eciAddText refused the text");
		return finished(in);
	}
	if (!eciSynthesize(in->eci)) {
		LOGE("eciSynthesize refused");
		return finished(in);
	}
	pthread_mutex_lock(&in->lock);
	in->pending = 1;
	pthread_cond_broadcast(&in->work);
	pthread_mutex_unlock(&in->lock);
	return JNI_TRUE;
}

/* Bytes out, blocking until there are some or until the caller has waited long
   enough. Answers 0 at the end of the utterance, -1 when it was stopped, and
   -2 when timeoutMs ran out first -- which is the one answer the caller has to
   be able to tell from an end, because an engine with nothing left to say and
   an engine that has stopped saying anything look the same from here.
   timeoutMs of zero or less means wait as long as it takes. */
JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_read(JNIEnv* env, jclass cls, jlong handle, jbyteArray dst, jint timeoutMs) {
	instance* in = unwrap(handle);
	size_t    room;
	size_t    got;
	int       timed_out = 0;
	(void)cls;
	if (in == NULL || dst == NULL)
		return -1;
	room = (size_t)(*env)->GetArrayLength(env, dst);
	/* Whole samples only. A read that carried an odd number of bytes
	   would leave half a sample in the ring, and every sample after it in
	   the next read would be the two bytes of two different samples -- a
	   burst of noise rather than speech. */
	room &= ~(size_t)1;
	if (room == 0)
		return -1;
	if (room > READ_MAX)
		room = READ_MAX;
	pthread_mutex_lock(&in->lock);
	if (in->count == 0 && !in->done && !in->aborted) {
		in->waiters++;
		while (in->count == 0 && !in->done && !in->aborted) {
			if (timeoutMs <= 0)
				pthread_cond_wait(&in->filled, &in->lock);
			else if (wait_millis(in, &in->filled, (long)timeoutMs) != 0) {
				timed_out = 1;
				break;
			}
		}
		in->waiters--;
	}
	if (in->aborted) {
		pthread_mutex_unlock(&in->lock);
		return -1;
	}
	if (in->count == 0) {
		pthread_mutex_unlock(&in->lock);
		return timed_out ? -2 : 0;
	}
	/* Everything that is there rather than what was asked for, in one go. A
	   frame of four kilobytes read a kilobyte at a time is four of these, and
	   each one is a lock, a wake-up on the other side and a copy. */
	got = in->count < room ? in->count : room;
	take(in, in->scratch, got);
	pthread_cond_signal(&in->room);
	pthread_mutex_unlock(&in->lock);
	(*env)->SetByteArrayRegion(env, dst, 0, (jsize)got, (const jbyte*)in->scratch);
	return (jint)got;
}

/* Wakes the callback before asking the engine to stop, since the callback may
   be waiting for room that a stopped reader will never make. */
JNIEXPORT void JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_stop(JNIEnv* env, jclass cls, jlong handle) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	if (in == NULL)
		return;
	pthread_mutex_lock(&in->lock);
	in->aborted = 1;
	in->head = in->tail = in->count = 0;
	pthread_cond_broadcast(&in->room);
	pthread_cond_broadcast(&in->filled);
	pthread_mutex_unlock(&in->lock);
	/* eciStop is deliberately not called, here or anywhere else on this path.
	   The flag makes the callback answer eciDataAbort the next time the engine
	   offers samples, and that unwinds it from inside its own call and leaves
	   it fit for the next utterance. eciStop does not: about eighty of them on
	   one instance and the engine either goes silent or jumps into its own
	   data. openevv's own harnesses show both halves of that -- the one that
	   aborts from the callback runs clean for hundreds of turns, the one that
	   calls eciStop dies around turn eighty. */
}

/* The instance's own dictionary set, made the first time one is wanted. An
   instance has one set in force at a time and the three volumes live in it. */
static int ensure_dict(instance* in) {
	if (in->dict != NULL_DICT_HAND)
		return 1;
	in->dict = eciNewDict(in->eci);
	if (in->dict == NULL_DICT_HAND) {
		LOGE("the engine would not make a dictionary set");
		return 0;
	}
	/* An error code rather than a flag, so nought is success. */
	if (eciSetDict(in->eci, in->dict) != 0) {
		LOGE("the engine would not take the dictionary set");
		return 0;
	}
	return 1;
}

/* Hands a file to the engine's own dictionary loader. Answers one of the
   eciDict codes, so nought is success. */
JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_loadDictionary(JNIEnv* env, jclass cls, jlong handle, jint volume, jstring path) {
	instance*   in = unwrap(handle);
	const char* name;
	jint        answer;
	(void)cls;
	if (in == NULL || path == NULL || !ensure_dict(in))
		return -1;
	name = (*env)->GetStringUTFChars(env, path, NULL);
	if (name == NULL)
		return -1;
	answer = eciLoadDict(in->eci, in->dict, volume, name);
	(*env)->ReleaseStringUTFChars(env, path, name);
	return answer;
}

/* Teaches one word, which is the other way in and the one that does not depend
   on the engine agreeing about a file format. */
JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_teachWord(JNIEnv* env, jclass cls, jlong handle, jint volume, jbyteArray key, jbyteArray say) {
	instance*      in = unwrap(handle);
	jsize          keyLen;
	jsize          sayLen;
	unsigned char* pair;
	jint           answer;
	(void)cls;
	if (in == NULL || key == NULL || say == NULL || !ensure_dict(in))
		return -1;
	keyLen = (*env)->GetArrayLength(env, key);
	sayLen = (*env)->GetArrayLength(env, say);
	pair = (unsigned char*)malloc((size_t)keyLen + (size_t)sayLen + 2);
	if (pair == NULL)
		return -1;
	(*env)->GetByteArrayRegion(env, key, 0, keyLen, (jbyte*)pair);
	pair[keyLen] = 0;
	(*env)->GetByteArrayRegion(env, say, 0, sayLen, (jbyte*)(pair + keyLen + 1));
	pair[keyLen + 1 + sayLen] = 0;
	answer = eciUpdateDict(in->eci, in->dict, volume, pair, pair + keyLen + 1);
	free(pair);
	return answer;
}

/* What a key was taught, or nothing. This is how a test says a dictionary
   really went in rather than that a call answered nought. */
JNIEXPORT jstring JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_lookUpWord(JNIEnv* env, jclass cls, jlong handle, jint volume, jbyteArray key) {
	instance*      in = unwrap(handle);
	jsize          keyLen;
	unsigned char* want;
	const char*    found;
	jstring        answer;
	(void)cls;
	if (in == NULL || key == NULL || in->dict == NULL_DICT_HAND)
		return NULL;
	keyLen = (*env)->GetArrayLength(env, key);
	want = (unsigned char*)malloc((size_t)keyLen + 1);
	if (want == NULL)
		return NULL;
	(*env)->GetByteArrayRegion(env, key, 0, keyLen, (jbyte*)want);
	want[keyLen] = 0;
	found = eciDictLookup(in->eci, in->dict, volume, want);
	answer = found == NULL ? NULL : (*env)->NewStringUTF(env, found);
	free(want);
	return answer;
}

/* Puts the set aside and takes it away, so the engine is back to the language's
   own dictionary. */
JNIEXPORT void JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_forgetDictionaries(JNIEnv* env, jclass cls, jlong handle) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	if (in == NULL || in->dict == NULL_DICT_HAND)
		return;
	eciSetDict(in->eci, NULL_DICT_HAND);
	eciDeleteDict(in->eci, in->dict);
	in->dict = NULL_DICT_HAND;
}

JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_setParam(JNIEnv* env, jclass cls, jlong handle, jint param, jint value) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	return in == NULL ? -1 : eciSetParam(in->eci, param, value);
}

JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_getParam(JNIEnv* env, jclass cls, jlong handle, jint param) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	return in == NULL ? -1 : eciGetParam(in->eci, param);
}

JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_setVoiceParam(JNIEnv* env, jclass cls, jlong handle, jint voice, jint param, jint value) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	return in == NULL ? -1 : eciSetVoiceParam(in->eci, voice, param, value);
}

JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_getVoiceParam(JNIEnv* env, jclass cls, jlong handle, jint voice, jint param) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	return in == NULL ? -1 : eciGetVoiceParam(in->eci, voice, param);
}

JNIEXPORT jint JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_copyVoice(JNIEnv* env, jclass cls, jlong handle, jint from, jint to) {
	instance* in = unwrap(handle);
	(void)env;
	(void)cls;
	return in == NULL ? -1 : eciCopyVoice(in->eci, from, to);
}

/* Which of the engine's upsampling methods new converters run on. Ours rather
   than upstream's: see native/patches/0001-upsample-method-override.patch,
   which is what declares it. The link fails loudly without that patch, which
   is the point -- a build that silently kept the default while the settings
   screen offered a choice would be the worse failure. */
extern void evv_setUpsampleMethod(int method);

JNIEXPORT void JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_setUpsampleMethod(JNIEnv* env, jclass cls, jint method) {
	(void)env;
	(void)cls;
	evv_setUpsampleMethod((int)method);
}

JNIEXPORT jstring JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_version(JNIEnv* env, jclass cls) {
	char buffer[ECI_VERSION_LENGTH];
	(void)cls;
	memset(buffer, 0, sizeof buffer);
	eciVersion(buffer);
	buffer[sizeof buffer - 1] = 0;
	return (*env)->NewStringUTF(env, buffer);
}

/* The languages this build of the engine was compiled with, by the numbers
   eciLanguageDialect takes, so the front end can check that what it offers
   is what it has. Asked once with no room for the count and once with room
   for the list, which is how the engine's own call works. An empty array
   when the engine will not say. */
JNIEXPORT jintArray JNICALL Java_com_blindtechnexus_freeeloquencetts_EvvNative_availableLanguages(JNIEnv* env, jclass cls) {
	int          count = 0;
	unsigned int found[32];
	jintArray    out;
	jint         values[32];
	int          i;
	(void)cls;
	eciGetAvailableLanguages(NULL, &count);
	if (count <= 0) {
		return (*env)->NewIntArray(env, 0);
	}
	if (count > 32)
		count = 32;
	memset(found, 0, sizeof found);
	eciGetAvailableLanguages(found, &count);
	if (count < 0)
		count = 0;
	if (count > 32)
		count = 32;
	out = (*env)->NewIntArray(env, count);
	if (out == NULL)
		return NULL;
	for (i = 0; i < count; i++)
		values[i] = (jint)found[i];
	(*env)->SetIntArrayRegion(env, out, 0, count, values);
	return out;
}
