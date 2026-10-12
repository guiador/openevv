package com.blindtechnexus.freeeloquencetts

/** The JNI surface. Every call here is thread safe except [speak] and [read],
 *  which belong to whichever thread is driving one utterance. */
internal object EvvNative {

	@Volatile
	var loadError: Throwable? = null
		private set

	val loaded: Boolean

	init {
		var ok = false
		try {
			System.loadLibrary("evvjni")
			ok = true
		} catch (e: UnsatisfiedLinkError) {
			loadError = e
		} catch (e: Throwable) {
			// Anything else the loader can throw -- a security refusal, an
			// initialization failure from inside the library -- is the same
			// answer: the engine is unavailable. An exception escaping this
			// block would poison every later use of this object with an
			// ExceptionInInitializerError, which is how a missing library
			// becomes a crash instead of a voice that is not there.
			loadError = e
		}
		loaded = ok
	}

	external fun create(language: Int): Long

	external fun destroy(handle: Long)

	external fun speak(handle: Long, text: ByteArray): Boolean
    external fun configureOutput(handle: Long, samples: Int, ringbytes: Int): Boolean

	/** Fills [dst] with PCM bytes. Answers 0 at the end of the utterance and
	 *  -1 when it was stopped or something went wrong.
	 *
	 *  [timeoutMs] is how long to wait for samples that are not coming. The
	 *  engine is asked for silence by a flag rather than by a call, so the
	 *  only thing that can bring a hung one back is a wait that ends; without
	 *  one the framework's synthesis thread sits in here for the rest of the
	 *  day, and every later request for speech is behind it. */
	external fun read(handle: Long, dst: ByteArray, timeoutMs: Int): Int

	external fun stop(handle: Long)

	external fun setParam(handle: Long, param: Int, value: Int): Int

	external fun getParam(handle: Long, param: Int): Int

	external fun setVoiceParam(handle: Long, voice: Int, param: Int, value: Int): Int

	external fun getVoiceParam(handle: Long, voice: Int, param: Int): Int

	external fun copyVoice(handle: Long, from: Int, to: Int): Int

	/** Answers one of the eciDict codes, so nought is success. */
	external fun loadDictionary(handle: Long, volume: Int, path: String): Int

	external fun teachWord(handle: Long, volume: Int, key: ByteArray, say: ByteArray): Int

	external fun lookUpWord(handle: Long, volume: Int, key: ByteArray): String?

	external fun forgetDictionaries(handle: Long)

	external fun version(): String

	/** The languages this build of the engine carries, by the numbers
	 *  eciLanguageDialect takes -- the ones the build script named. What
	 *  [Eci.LANGUAGES] offers must be among them, and a test says so. */
	external fun availableLanguages(): IntArray

	/** Which of the engine's upsampling methods new converters run on: one of
	 *  the [Eci.UPSAMPLE_HOLD], [Eci.UPSAMPLE_LINEAR], [Eci.UPSAMPLE_CUBIC] or
	 *  [Eci.UPSAMPLE_SINC] values. Process-wide and read when a converter is
	 *  (re)built, so whoever calls this re-sets the sample rate straight after
	 *  -- see [EvvEngine.setUpsampleMethod] -- which rebuilds the converter and
	 *  puts the new method in force at once instead of on the next restart. */
	external fun setUpsampleMethod(method: Int)
}
