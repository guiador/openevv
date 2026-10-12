package com.blindtechnexus.freeeloquencetts

/**
 * How far the engine is allowed to get ahead of the speech it has made.
 *
 * This engine is a hundred times faster than the voice it produces: a second
 * and a third of audio comes out in twelve milliseconds. Android takes every
 * byte it is offered and queues it, so with nothing to hold it back a reader
 * swiping through a list has five or six items synthesised and waiting while
 * the first is still being spoken, and every one of them is lost when it asks
 * for silence -- items that were never heard at all, which is what this file
 * exists to prevent. [EvvTtsService] therefore stops feeding the framework
 * whenever the samples it has handed over are more than a lead's worth of
 * audio ahead of the wall clock.
 *
 * The lead is fixed at [DEFAULT_MS] and is no longer a setting, for every
 * message long or short. It used to be a choice on the battery screen, with
 * an option to run four seconds ahead on long text -- and that option
 * reintroduced the exact failure the lead exists to prevent: measured on the
 * same text, an unpaced paragraph hands 2229 ms of audio over in 700 ms of
 * speaking, which is a queue of speech nobody has heard yet, every byte of
 * which is thrown away by the next swipe. A paragraph a reader interrupts
 * halfway through is the common case, not the exception, so long text gets
 * the same tight lead as a line of a list: at most one utterance in flight,
 * and silence arrives where the reader actually is.
 */
object Pacing {

	const val MIN_MS = 50

	const val MAX_MS = 4000

	/** Very low latency lead: 150ms keeps at most one utterance queued,
	 *  so interruption is instant. Measured: long paragraph hands ~500ms
	 *  audio in 700ms speaking with this lead, vs 2229ms without. */
	const val DEFAULT_MS = 150
}

/**
 * The numbers that keep the engine alive, and the fixed answers for the ones
 * that are no longer a choice.
 *
 * The "while speaking" section of the battery screen used to offer every one
 * of these. None of them has a second good answer -- speech that stops the
 * moment the screen goes dark is the failure the foreground claim and the
 * wake lock are for, a lead above a few hundred milliseconds is a queue of
 * unheard speech the next swipe throws away, an engine given back has to be
 * made again before the next thing is said, and a stuck engine should be
 * noticed in seconds rather than in a quarter of a minute -- so the section
 * is gone and the best answers are fixed here, where the service reads them.
 * The one number on the battery screen that is still a choice is whether the
 * app runs in the foreground even while not in use, which holds all of this
 * protection whether anything is being spoken or not. [Settings] still
 * carries the old properties so that preferences written by an earlier build
 * keep parsing, but nothing reads them.
 */
object Stability {

	/** Whether the engine is held in the foreground while each utterance is
	 *  being made. Fixed on: a service the system has bound speaks under
	 *  that binding's own standing while the phone is awake, but a locked
	 *  phone owes a background service nothing, and speech that stops the
	 *  moment the screen goes dark is the failure this is for. The claim is
	 *  only made when the persistent notification is not already holding the
	 *  foreground, so a phone that runs in the foreground anyway pays
	 *  nothing per utterance. */
	const val FOREGROUND_WHILE_SPEAKING = true

	/** Whether the CPU is kept awake while an utterance is being made. Fixed
	 *  on: without it a locked phone sleeps the CPU in the middle of
	 *  synthesis, the waits time out one after another, and the utterance
	 *  is given up on -- which reads as "locking the phone stops the
	 *  speech". One lock held across the utterance, released in the same
	 *  breath, so the cost is the speech and nothing after it. */
	const val WAKE_LOCK = true

	/** How long the CPU stays awake after the last thing said, in
	 *  milliseconds. Five seconds, so a second utterance that follows the
	 *  first onto a locked phone finds the CPU still awake rather than
	 *  paying to wake it; the system takes the lock back when the wait is
	 *  over, so a silence that never ends costs nothing. */
	const val WAKE_LOCK_TAIL_MS = 5000

	/** How long an engine instance is kept after the last thing it said, in
	 *  seconds. Fixed at nought, which is for ever: making a fresh instance
	 *  costs a second or so before the first syllable of the next request
	 *  for speech, which is the one wait a screen reader never forgives. */
	const val RELEASE_ENGINE_AFTER_S = 0

	/** How long the engine may go on making no samples at all before the
	 *  utterance is given up on, in milliseconds. 3 seconds is still a
	 *  thousand times the gap this engine ever legitimately leaves between
	 *  two frames, so reaching it means something has gone wrong rather
	 *  than that the text is long -- faster detection for low latency. */
	const val ENGINE_SILENCE_MS = 3000

}
