package com.blindtechnexus.freeeloquencetts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * An engine of its own for the settings screen, and somewhere to hear it.
 *
 * Tuning a voice by writing a setting down, leaving, and waiting for the
 * screen reader to say something else in it is not tuning. So the settings
 * screen keeps an instance of its own and speaks into an AudioTrack, which is
 * the one place in this program that plays audio itself: the engine that
 * TalkBack drives hands its samples to Android and never sees a device.
 *
 * Everything runs on one background thread, so a preview asked for while
 * another is speaking replaces it rather than overlapping.
 */
class EvvPreview(context: android.content.Context? = null) {
    private val app=context?.applicationContext

	private val thread = HandlerThread("evv-preview").apply { start() }
	private val work = Handler(thread.looper)

	@Volatile
	private var engine: EvvEngine? = null

	@Volatile
	private var track: AudioTrack? = null

	@Volatile
	private var generation = 0

	/** Which English the samples are spoken in. One instance at a time here,
	 *  because a screen that is tuning a voice is listening to one voice -- and
	 *  the choice is the settings screen's, so changing it is a new instance
	 *  and not a second one. */
	@Volatile
	private var english = Eci.ENGLISH_US

	/** How many bytes of audio have gone to the track. Nothing in the app reads
	 *  it; it is what lets a test say the preview really played, which is worth
	 *  having because a preview that quietly plays nothing looks exactly like
	 *  one that works. */
	@Volatile
	var bytesPlayed: Long = 0L
		private set

	/** Whether a sample is still being spoken, and whether the last one was
	 *  heard all the way out. Nothing in the app reads either; they are what
	 *  lets a test say the tail was not cut off, which is invisible from
	 *  outside because every byte was handed over either way. */
	@Volatile
	var playing: Boolean = false
		private set

	@Volatile
	var drained: Boolean = false
		private set

	/** Reads what a preset is set to. Answers an empty map until the engine has
	 *  opened, which is the first thing the thread does. */
	fun presetShape(index: Int): Map<Int, Int> = engine?.presetShape(index) ?: emptyMap()

	fun open(language: Int = Eci.ENGLISH_US, then: (() -> Unit)? = null) {
		work.post {
			english = language
			if (engine == null || engine?.language != language) {
				// Opening in the other English means giving the instance
				// back: an engine speaks the language it was built with, so
				// the sample in the new voice is a sample from a new
				// instance. It happens while the screen is being set up, so
				// the second of opening is paid once and off the main
				// thread.
				runCatching { engine?.close() }
				engine = EvvEngine.open(language)
			}
			then?.let { Handler(android.os.Looper.getMainLooper()).post(it) }
		}
	}

	/** Says [text] in [voice] with [shape] on it, stopping whatever was being
	 *  said. [abbreviations] and [optimization] are the same switches the
	 *  service reads, so the sample hears what a caller hears. */
	fun say(
		text: String,
		voice: Int,
		shape: Map<Int, Int>,
		rateHz: Int,
		abbreviations: Boolean = false,
		optimization: Int = AudioOptimization.BALANCED,
        equalizer: EqualizerConfig = EqualizerConfig(),
        resampling: Int = -1,
        synthesisMode: Int = 0,
        pauseMode: Int = 1
	) {
		// Whatever is being said stops first, and the turn this utterance
		// belongs to is taken after that -- stopping is what moves the count
		// on, so reading it first would leave this utterance holding a number
		// that is already stale and it would never be spoken at all.
		stopSpeaking()
        WirelessMaintenance.stop()
		val mine = generation
		work.post {
			if (mine != generation) return@post
            val idleSettings=app?.let { Settings(it) }
            if(idleSettings!=null) WirelessMaintenance.speechStarted()
            try {
			val e = try {
				engine ?: EvvEngine.open(english)?.also { engine = it } ?: return@post
			} catch (_: Throwable) {
				return@post
			}
			try {
				e.setSampleRate(rateHz)
				e.setUpsampleMethod(Resampling.method(resampling, optimization))
                e.setSynthesisMode(synthesisMode)
                e.setPauseMode(pauseMode)
				e.setAbbreviations(abbreviations)
				e.applyVoice(voice, shape)
			} catch (_: Throwable) {
			}
			if (!e.speak(text, isStopped = { mine != generation })) {
				if (mine == generation) {
					Log.e(TAG, "the engine refused the sample")
					// A refusal nothing explains is a wedged instance, and
					// this preview keeps its engine for ever -- so the
					// instance is given back here, and the next sample opens
					// a fresh one instead of queuing behind whatever went
					// wrong. The service does the same after an utterance
					// its engine went quiet in; this is the preview's half
					// of that promise.
					try {
						e.close()
					} catch (_: Throwable) {
					}
					if (engine === e) engine = null
				}
				return@post
			}
			play(e, mine, AudioOptimizer(optimization, e.sampleRateHz, equalizer))
            } finally {
                if(idleSettings!=null) {
                    try { WirelessMaintenance.speechEnded(app!!,idleSettings) } finally { idleSettings.close() }
                }
            }
		}
	}

	private fun play(e: EvvEngine, mine: Int, optimizer: AudioOptimizer) {
		playing = true
		drained = false
		// Everything below runs on the preview's own thread, where an
		// exception is not a failed sample but a dead thread -- and a dead
		// thread with an uncaught exception is a dead process. The audio
		// device is the part that throws: building the track, starting it,
		// and writing to it can all refuse on a phone whose audio is
		// otherwise held, so every one of them is guarded and the track is
		// always released. A sample that cannot be heard is silence, not a
		// crash.
		var t: AudioTrack? = null
		try {
			val rate = e.sampleRateHz
			val minimum = try {
				AudioTrack.getMinBufferSize(
					rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
				)
			} catch (_: Throwable) {
				0
			}
			val size = if (minimum > 0) maxOf(minimum, CHUNK * 4) else CHUNK * 4
			t = try {
				AudioTrack.Builder()
					.setAudioAttributes(
						AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_ASSISTANT)
							.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
							.build()
					)
					.setAudioFormat(
						AudioFormat.Builder()
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(rate)
							.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
							.build()
					)
					.setBufferSizeInBytes(size)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.build()
			} catch (broken: Throwable) {
				Log.e(TAG, "no audio track for the sample", broken)
				null
			} ?: return
			track = t
			try {
				t.play()
			} catch (broken: Throwable) {
				Log.e(TAG, "the sample would not play", broken)
				return
			}
			val buffer = ByteArray(CHUNK)
			var written = 0L
			while (mine == generation) {
				val n = try {
					e.read(buffer)
				} catch (_: Throwable) {
					-1
				}
				if (n <= 0) break
				try {
					optimizer.process(buffer, n)
				} catch (_: Throwable) {
				}
				var at = 0
				while (at < n && mine == generation) {
					val wrote = try {
						t.write(buffer, at, n - at)
					} catch (_: Throwable) {
						break
					}
					if (wrote <= 0) break
					at += wrote
					bytesPlayed += wrote
					written += wrote / BYTES_PER_FRAME
				}
			}
			if (mine == generation) {
				// Waited out before stopping rather than after. The track holds
				// most of a second, which is the last few words of a short sample,
				// and release() cuts that off; stop() would let it play but also
				// puts the playback head back to nought, so there would be nothing
				// left to watch. So this waits for the head to reach the end of
				// what was written while the track is still running.
				drained = waitToDrain(t, mine, written, rate)
				runCatching { t.stop() }
			} else {
				runCatching { t.pause(); t.flush(); t.stop() }
			}
		} finally {
			val held = t
			if (held != null) {
				runCatching { held.release() }
				if (track === held) track = null
			}
			playing = false
		}
	}

	/** Answers whether everything written was actually heard. */
	private fun waitToDrain(t: AudioTrack, mine: Int, frames: Long, rate: Int): Boolean {
		if (frames <= 0L) return true
		// However long the audio is, plus a margin, so a track that stops
		// reporting cannot hold this thread for ever.
		val giveUp = System.currentTimeMillis() + frames * 1000L / rate + MARGIN_MS
		while (mine == generation && System.currentTimeMillis() < giveUp) {
			val head = runCatching { t.playbackHeadPosition.toLong() and 0xFFFFFFFFL }
				.getOrElse { return false }
			if (head >= frames) return true
			try {
				Thread.sleep(SIP_MS)
			} catch (stopping: InterruptedException) {
				Thread.currentThread().interrupt()
				return false
			}
		}
		return false
	}

	fun stopSpeaking() {
		generation++
		engine?.stop()
		runCatching { track?.pause(); track?.flush() }
	}

	fun close() {
		stopSpeaking()
		work.post {
			engine?.close()
			engine = null
			thread.quitSafely()
		}
	}

	private companion object {
		const val TAG = "freeeloquencetts"
		const val CHUNK = 1024 // low latency
		const val BYTES_PER_FRAME = 2
		const val MARGIN_MS = 300L
		const val SIP_MS = 5L
	}
}
