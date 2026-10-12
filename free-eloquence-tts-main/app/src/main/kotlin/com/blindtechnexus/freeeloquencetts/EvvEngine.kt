package com.blindtechnexus.freeeloquencetts

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * One engine instance and the settings in force on it.
 *
 * Everything but [stop] is called from the synthesis thread. [stop] comes from
 * whichever thread asked, which the native side is written for.
 */
class EvvEngine private constructor(@Volatile private var handle: Long, val language: Int) {

	private val guard = Any()

	@Volatile
	private var closed = false

	/** How many threads are inside the native read right now. */
	private val reading = AtomicInteger()

	var sampleRateHz: Int = Eci.sampleRateHz(1)
		private set

	/** The upsampling method in force on this instance, or -1 when nothing
	 *  has named one yet. The engine's own default answers until then, which
	 *  keeps a bare instance -- the tests' -- exactly as it always was. */
	private var upsampleMethod = -1

	/** What the voice was set to before Android's rate and pitch were scaled
	 *  onto it, so that a rate of 100% is the voice as it stands rather than
	 *  whatever the last utterance left behind. */
	private var baseSpeed = -1
	private var basePitch = -1

	private var currentRate = -1
	private var currentPitch = -1

	companion object {
		private const val TAG = "freeeloquencetts"

		/** How long a [read] waits for samples when the caller has not named a
		 *  wait of its own. The service passes the fixed figure; this is the
		 *  number for anybody else -- the settings screen's own preview --
		 *  and it is the same length of silence because there is no second
		 *  sensible answer. Five seconds is still a thousand times the gap
		 *  this engine ever legitimately leaves between two frames. */
		const val READ_TIMEOUT_MS = 3000 // low latency

		/** How long [close] waits for a thread that is inside [read] to get
		 *  out before it leaves the handle to that thread and takes the rest
		 *  of its own business. Two seconds is a long time for a read that is
		 *  coming back, and a short one for a UI thread to be held. */
		const val CLOSE_WAIT_MS = 500L // fast close

		/** How long the orphaned handle is then waited for, on a thread of its
		 *  own, before it is destroyed regardless: past this the instance is
		 *  presumed wedged and the memory matters more than the risk. */
		const val CLOSE_HARD_WAIT_MS = 30000L

		/** What the two waits look between one glance and the next. */
		const val CLOSE_POLL_MS = 5L // fast poll

		val version: String by lazy {
			if (!EvvNative.loaded) "" else runCatching { EvvNative.version() }.getOrDefault("")
		}

		/**
		 * Whether [text] carries nothing the engine could say: whitespace
		 * and backtick annotations only. A piece like that is skipped by the
		 * caller rather than handed over, because an utterance of nothing is
		 * a wait for audio that is never coming, not speech.
		 */
		fun isBlankSpeech(text: String): Boolean {
			var i = 0
			val n = text.length
			while (i < n) {
				val c = text[i]
				if (c == '`') {
					i++
					while (i < n && !text[i].isWhitespace()) i++
					continue
				}
				if (!c.isWhitespace()) return false
				i++
			}
			return true
		}

		/**
		 * The longest text one [speak] call takes. Pieces are hundreds of
		 * characters; anything past this is a caller that lost its way, and
		 * refusing it is cheaper than queueing it.
		 */
		const val MAX_SPEAK_CHARS = 256 * 1024

		/** Makes an instance speaking [language], one of the [Eci.LANGUAGES]
		 *  this build carries modules for; the engine opens nothing else.
		 *  Answers null rather than throwing, whatever went wrong: a
		 *  caller that cannot have an engine must get that answer, not lose
		 *  the thread -- and on the framework's synthesis thread, losing it
		 *  is losing the voice. */
		fun open(language: Int): EvvEngine? {
			if (!EvvNative.loaded) {
				Log.e(TAG, "the native library did not load", EvvNative.loadError)
				return null
			}
			val handle = try {
				EvvNative.create(language)
			} catch (broken: Throwable) {
				Log.e(TAG, "the native instance would not open", broken)
				return null
			}
			if (handle == 0L) {
				Log.e(TAG, "no engine instance for language 0x%08x".format(language))
				return null
			}
			return try {
				EvvEngine(handle, language).apply {
                    configure()
                    Eci.PRESET_NAMES.indices.forEach { index -> VoiceDefaults.remember(language,index,presetShape(index)) }
                }
			} catch (broken: Throwable) {
				Log.e(TAG, "the engine instance would not configure", broken)
				try {
					EvvNative.destroy(handle)
				} catch (forgotten: Throwable) {
					// The instance is being given up on; there is nothing
					// left that wants it either way.
				}
				null
			}
		}
	}

	private fun configure() {
		// Each call guarded on its own rather than the block as a whole: a
		// refusal in the middle must not take the sample rate -- the one
		// the framework is told about -- down with it.
		try { EvvNative.setParam(handle, Eci.PARAM_LANGUAGE_DIALECT, language) } catch (_: Throwable) {}
		try { EvvNative.setParam(handle, Eci.PARAM_SYNTH_MODE, 0) } catch (_: Throwable) {}
		try { EvvNative.setParam(handle, Eci.PARAM_REAL_WORLD_UNITS, 0) } catch (_: Throwable) {}
		// Always on. The engine is the only thing that can act on an annotation,
		// nothing in this app speaks without wanting pauses shortened, and one
		// that is malformed is spoken rather than obeyed, so ordinary text
		// carrying a backtick is read out as it stands.
		try { setAnnotations(true) } catch (_: Throwable) {}
		try {
			setSampleRate(Eci.sampleRateHz(EvvNative.getParam(handle, Eci.PARAM_SAMPLE_RATE)))
		} catch (_: Throwable) {
		}
	}

    fun setSynthesisMode(mode: Int) = synchronized(guard) {
        if (closed) return
        check(EvvNative.setParam(handle, 32, mode.coerceIn(0, 2)) >= 0) { "Synthesis mode rejected" }
    }

    fun setPauseMode(mode: Int) = synchronized(guard) {
        if (closed) return
        check(EvvNative.setParam(handle, 33, mode.coerceIn(0, 2)) >= 0) { "Pause mode rejected" }
    }

	fun setSampleRate(hz: Int) = synchronized(guard) {
		if (closed) return
		val index = Eci.sampleRateIndex(hz)
		// Adopted only when the engine took it. eciSetParam answers -1 on a
		// refusal, and a rate the framework is told about that the engine is
		// not synthesising at is the wrong speed under the right label.
		// A JNI call that throws -- the library gone from under the process,
		// a binder of a caller long dead -- is a refusal of the same kind:
		// the rate stays what it was rather than taking the caller with it.
		val took = try {
			EvvNative.setParam(handle, Eci.PARAM_SAMPLE_RATE, index)
		} catch (_: Throwable) {
			-1
		}
		if (took == -1) return
		// The rate the engine is now at is asked for rather than assumed:
		// an engine that took the call and settled on another index -- an
		// older native build that knows the first four -- is still an
		// engine whose true rate is the one the framework must be told.
		val now = try {
			EvvNative.getParam(handle, Eci.PARAM_SAMPLE_RATE)
		} catch (_: Throwable) {
			index
		}
		sampleRateHz = Eci.sampleRateHz(if (now in 0..6 || now in Eci.SAMPLE_RATES) now else index)
	}

	/** Which of the engine's upsampling methods this instance runs on: one of
	 *  the [Eci.UPSAMPLE_HOLD], [Eci.UPSAMPLE_LINEAR], [Eci.UPSAMPLE_CUBIC] or
	 *  [Eci.UPSAMPLE_SINC] values. Read the [AudioOptimization] mode it came
	 *  from for why one is louder and another cleaner. A no-op when nothing
	 *  moved, so calling it per utterance costs one comparison. */
	fun setUpsampleMethod(method: Int) = synchronized(guard) {
		if (closed || method == upsampleMethod) return
		try {
			EvvNative.setUpsampleMethod(method)
		} catch (_: Throwable) {
			return
		}
		// The method is read when a converter is built, so the converter is
		// rebuilt now -- by re-setting the rate it already has -- and the new
		// method is in force on the next piece rather than on the next run.
		// The call is recorded only when the rebuild took: a refused rebuild
		// would otherwise read back as done while the old method still runs,
		// and the retry that a fresh utterance brings would be skipped.
		val took = try {
			EvvNative.setParam(handle, Eci.PARAM_SAMPLE_RATE, Eci.sampleRateIndex(sampleRateHz))
		} catch (_: Throwable) {
			-1
		}
		if (took != -1) {
			upsampleMethod = method
		}
	}

	fun setAnnotations(on: Boolean) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				EvvNative.setParam(handle, Eci.PARAM_INPUT_TYPE, if (on) 1 else 0)
			} catch (_: Throwable) {
			}
		}
	}

	/** The abbreviation dictionary, which is what eciDictionary switches.
	 *  Nought turns it on: that is the engine's own sense of the setting and
	 *  not a mistake here. */
	fun setAbbreviations(on: Boolean) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				EvvNative.setParam(handle, Eci.PARAM_DICTIONARY, if (on) 0 else 1)
			} catch (_: Throwable) {
			}
		}
	}

	/** What preset [index] (0 based, so 0 is Reed) is set to, without changing
	 *  the voice being spoken in. This is what the settings screen shows, so a
	 *  slider starts at the voice's own number rather than at one of ours.
	 *  Luna answers her base preset's numbers with her own shape laid over
	 *  them, which is what she is spoken in. */
	fun presetShape(index: Int): Map<Int, Int> = synchronized(guard) {
		if (closed) return emptyMap()
		if (index == Eci.LUNA_INDEX) {
			val base = readPresetLocked(Eci.LUNA_BASE_PRESET)
			if (base.isEmpty()) return Eci.LUNA_SHAPE
			val out = LinkedHashMap<Int, Int>(base)
			out.putAll(Eci.LUNA_SHAPE)
			return out
		}
		val preset = (index + Eci.FIRST_PRESET).coerceIn(Eci.FIRST_PRESET, Eci.LAST_PRESET)
		return readPresetLocked(preset)
	}

	private fun readPresetLocked(preset: Int): Map<Int, Int> {
		val out = LinkedHashMap<Int, Int>()
		for (param in 0 until Eci.NUM_VOICE_PARAMS) {
			val value = try {
				EvvNative.getVoiceParam(handle, preset, param)
			} catch (_: Throwable) {
				-1
			}
			if (value >= 0) out[param] = value
		}
		return out
	}

	/**
	 * Speaks in preset [index] with [shape] laid over it.
	 *
	 * The presets are read only, so the way to a voice of your own is to copy
	 * one into an editable number, change it there, and copy that onto the
	 * voice being spoken in. Anything [shape] does not name keeps what the
	 * preset had, which is why an empty map is the right thing to pass when
	 * nobody has chosen otherwise. Luna is copied from her base preset with
	 * her own shape laid under the listener's, so she is herself until
	 * somebody tunes her and keeps the tuning afterwards.
	 */
	fun applyVoice(index: Int, shape: Map<Int, Int> = emptyMap()) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				val preset = if (index == Eci.LUNA_INDEX) Eci.LUNA_BASE_PRESET
				else (index + Eci.FIRST_PRESET).coerceIn(Eci.FIRST_PRESET, Eci.LAST_PRESET)
				EvvNative.copyVoice(handle, preset, Eci.SCRATCH_VOICE)
				if (index == Eci.LUNA_INDEX) {
					for ((param, value) in Eci.LUNA_SHAPE) {
						if (param == Eci.VOICE_SPEED) continue
						EvvNative.setVoiceParam(handle, Eci.SCRATCH_VOICE, param, Eci.clampVoice(param, value))
					}
				}
				// The preset's own speed is not kept. Two of the eight ship faster than
				// the rest, so leaving it would make changing voice change the pace.
				EvvNative.setVoiceParam(
					handle, Eci.SCRATCH_VOICE, Eci.VOICE_SPEED,
					shape[Eci.VOICE_SPEED] ?: Eci.DEFAULT_SPEED
				)
				for ((param, value) in shape) {
					EvvNative.setVoiceParam(handle, Eci.SCRATCH_VOICE, param, Eci.clampVoice(param, value))
				}
				EvvNative.copyVoice(handle, Eci.SCRATCH_VOICE, Eci.VOICE_CURRENT)
				baseSpeed = EvvNative.getVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_SPEED)
				basePitch = EvvNative.getVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_PITCH_BASELINE)
				currentRate = -1
				currentPitch = -1
			} catch (_: Throwable) {
				// A voice that would not apply leaves the one before it in
				// force. The utterance still speaks -- in the wrong voice,
				// which is what the log is for -- rather than taking the
				// service down with it.
				Log.e(TAG, "the voice would not apply")
			}
		}
	}

	/** Android hands rate and pitch over as a percentage of normal, where the
	 *  engine wants its own numbers. Normal is the voice as [applyVoice] left
	 *  it, so 100% changes nothing. */
	fun setRatePercent(percent: Int) = synchronized(guard) {
		if (closed || percent == currentRate || baseSpeed < 0) return
		// Not baseSpeed * percent. The engine's speed scale is nothing like
		// linear in how fast it speaks, so SpeechRate does the conversion.
		val want = SpeechRate.speedForPercent(baseSpeed, percent)
		try {
			EvvNative.setVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_SPEED, want)
		} catch (_: Throwable) {
			return
		}
		currentRate = percent
	}

	fun setPitchPercent(percent: Int) = synchronized(guard) {
		if (closed || percent == currentPitch || basePitch < 0) return
		val want = (basePitch.toLong() * percent / 100).toInt().coerceIn(0, Eci.PERCENT_MAX)
		try {
			EvvNative.setVoiceParam(handle, Eci.VOICE_CURRENT, Eci.VOICE_PITCH_BASELINE, want)
		} catch (_: Throwable) {
			return
		}
		currentPitch = percent
	}

	/** Puts the voice's own volume back on the voice being spoken in. Nothing
	 *  a client can ask for reaches the volume -- Android applies a client's
	 *  volume to the stream after the speech has left the engine -- so this is
	 *  the whole of what forcing the volume can be: re-asserting this app's
	 *  own number each time, the way [setAbbreviations] is re-sent. */
	fun setVolume(value: Int) {
		synchronized(guard) {
			if (closed) return@synchronized
			try {
				EvvNative.setVoiceParam(
					handle, Eci.VOICE_CURRENT, Eci.VOICE_VOLUME, Eci.clampVoice(Eci.VOICE_VOLUME, value)
				)
			} catch (_: Throwable) {
			}
		}
	}

	fun getVoiceParam(param: Int): Int = synchronized(guard) {
		if (closed) return -1
		return try {
			EvvNative.getVoiceParam(handle, Eci.VOICE_CURRENT, param)
		} catch (_: Throwable) {
			-1
		}
	}

	/** Hands a file to the engine's own dictionary loader. Nought is success. */
	fun loadDictionary(volume: Int, path: String): Int = synchronized(guard) {
		if (closed) return -1
		return try {
			EvvNative.loadDictionary(handle, volume, path)
		} catch (_: Throwable) {
			-1
		}
	}

	fun teachWord(volume: Int, key: String, say: String): Int = synchronized(guard) {
		if (closed) return -1
		return try {
			EvvNative.teachWord(
				handle, volume, WesternText.encode(key), WesternText.encode(say)
			)
		} catch (_: Throwable) {
			-1
		}
	}

	fun lookUpWord(volume: Int, key: String): String? = synchronized(guard) {
		if (closed) return null
		return try {
			EvvNative.lookUpWord(handle, volume, WesternText.encode(key))
		} catch (_: Throwable) {
			null
		}
	}

	fun forgetDictionaries() = synchronized(guard) {
		if (closed) return
		try {
			EvvNative.forgetDictionaries(handle)
		} catch (_: Throwable) {
		}
	}

	/**
	 * Queues [text] and starts the engine. The samples come out of [read].
	 *
	 * [isStopped] is asked under the same lock [stop] takes, so a stop that
	 * lands as a piece is being handed over either refuses the piece or aborts
	 * it cleanly -- never half of each. Without that fence the stop slips
	 * between the caller's check and the engine's queueing, which is an
	 * utterance nobody reads while the next one queues behind it, and that
	 * wait is exactly what a fast swipe used to feel like.
	 *
	 * Total: blank text, absurdly long text and anything the native side
	 * throws are all answered with false rather than with an exception,
	 * because a caller that hands over a bad piece must get its utterance
	 * back, not lose the service.
	 */
	fun speak(text: String, isStopped: () -> Boolean = { false }): Boolean = synchronized(guard) {
		if (closed || isStopped()) return false
		return try {
			if (text.isBlank() || text.length > MAX_SPEAK_CHARS) return false
			val bytes = WesternText.encode(text)
			if (bytes.isEmpty()) return false
			EvvNative.speak(handle, bytes)
		} catch (refused: Throwable) {
			Log.e(TAG, "the engine refused the text", refused)
			false
		}
	}

    internal fun configureOutput(config: ResponseExperiment) = synchronized(guard) {
        if (!closed && config.frameSamples == configuredFrameSamples && config.ringKiB == configuredRingKiB) return@synchronized
        check(!closed && EvvNative.configureOutput(handle, config.frameSamples, config.ringKiB * 1024)) {
            "The engine refused the experimental output buffer"
        }
        configuredFrameSamples = config.frameSamples
        configuredRingKiB = config.ringKiB
    }
    private var configuredFrameSamples = 2048
    private var configuredRingKiB = 256

	/**
	 * Bytes of PCM, blocking until there are some. 0 ends the utterance and
	 * -1 says it was stopped or that nothing came for [timeoutMs].
	 *
	 * The timeout is not a courtesy. The engine cannot be made to abandon an
	 * utterance from outside, and this call is the one place the framework's
	 * synthesis thread waits for it, so a wait that never ends is a phone that
	 * has stopped speaking for the rest of the day rather than one that has
	 * lost the tail of a sentence. Whoever is speaking is asking to be heard
	 * as fast as the engine can manage, which is a hundred times faster than
	 * real time: anything still silent after a second of this is not slow, it
	 * is stuck.
	 */
	fun read(into: ByteArray, timeoutMs: Int = READ_TIMEOUT_MS): Int {
		// Counted before anything is asked, and the handle is only touched
		// once counted. [close] destroys the native instance as soon as the
		// count reads nought, so a read that checked first and counted after
		// would leave a window -- between the check and the count -- in
		// which the instance could be destroyed under it, and the JNI call
		// would then run on memory that is no longer an engine. Counting
		// first closes it: whatever [close] sees, a read that might touch
		// the handle is one it waits for.
		reading.incrementAndGet()
		try {
			if (closed) return -1
			val h = handle
			if (h == 0L) return -1
			return try {
				EvvNative.read(h, into, timeoutMs)
			} catch (broken: Throwable) {
				Log.e(TAG, "the read failed", broken)
				-1
			}
		} finally {
			reading.decrementAndGet()
		}
	}

	/**
	 * Asks for silence. Never throws: this is called from the framework's
	 * stop path, from the notification's button, and from the settings
	 * screen, and none of them can do anything with a failure except die
	 * of it. A stop that did not reach the engine is still answered --
	 * every caller that cares also watches its own flag.
	 */
	fun stop() = synchronized(guard) {
		if (closed) return
		try {
			EvvNative.stop(handle)
		} catch (_: Throwable) {
		}
	}

	/**
	 * Puts the instance away.
	 *
	 * [read] is deliberately not guarded by the same lock, because the thread
	 * sitting in it is the one the abort has to reach, and a close that waited
	 * on the lock would be a close that waited for speech that will never
	 * come. What it waits on instead is the count: the handle cannot go away
	 * under a reader that is still using it, so the abort goes first -- which
	 * is what makes that read return at once -- and the destroy follows the
	 * reader out.
	 *
	 * A reader that has not come out by [CLOSE_WAIT_MS] is left alone rather
	 * than waited for for ever: the handle is kept by a thread of its own until
	 * it does, and if it never does the instance is destroyed after
	 * [CLOSE_HARD_WAIT_MS] all the same. A leak would be the smaller of the two
	 * wrongs, and neither should ever happen.
	 */
	fun close() = synchronized(guard) {
		if (closed) return
		closed = true
		try {
			EvvNative.stop(handle)
		} catch (_: Throwable) {
		}
		if (awaitReader()) {
			val h = handle
			handle = 0
			try {
				EvvNative.destroy(h)
			} catch (_: Throwable) {
			}
			return
		}
		val orphan = handle
		handle = 0
		Thread({
			var waited = 0
			while (reading.get() > 0 && waited < CLOSE_HARD_WAIT_MS) {
				try {
					Thread.sleep(CLOSE_POLL_MS)
				} catch (stopping: InterruptedException) {
					Thread.currentThread().interrupt()
					break
				}
				waited += CLOSE_POLL_MS.toInt()
			}
			try {
				EvvNative.destroy(orphan)
			} catch (_: Throwable) {
			}
		}, "evv-close").also { it.isDaemon = true }.start()
	}

	private fun awaitReader(): Boolean {
		var waited = 0
		while (reading.get() > 0 && waited < CLOSE_WAIT_MS) {
			try {
				Thread.sleep(CLOSE_POLL_MS)
			} catch (stopping: InterruptedException) {
				Thread.currentThread().interrupt()
				return reading.get() == 0
			}
			waited += CLOSE_POLL_MS.toInt()
		}
		return reading.get() == 0
	}
}
