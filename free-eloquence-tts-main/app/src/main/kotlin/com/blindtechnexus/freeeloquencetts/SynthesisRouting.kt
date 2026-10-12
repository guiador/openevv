package com.blindtechnexus.freeeloquencetts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Where the sound goes.
 *
 * A speech engine on Android does not play anything. It hands the samples
 * to the framework, and the framework plays them on behalf of the app that
 * asked, on whatever audio channel that app chose: a screen reader on the
 * accessibility channel, a book reader on the media channel, a navigation
 * app on its own. That is [AUTOMATIC], and it is what every engine does and
 * what this one does unless told otherwise.
 *
 * The other three have this app play the speech itself, on a channel of the
 * listener's choosing, because there are phones and setups on which the
 * app's choice is the wrong one -- a car stereo that only carries media, a
 * hearing aid paired to the accessibility stream, a reader whose own
 * channel is ducked or muted by something else. [ACCESSIBILITY] plays every
 * utterance on the accessibility channel and [MEDIA] plays every utterance
 * on the media channel; in both the asking app is handed no samples, only
 * the start and the finish, so it still knows when each utterance began and
 * ended. [ALL] plays every utterance on the accessibility channel and hands
 * the samples to the asking app as well, which is for the setup where one
 * channel reaches one ear and the other the other.
 *
 * The one thing the three cannot do is tell a request to play from a
 * request to write a file: both arrive here the same way. An app that asks
 * this engine to synthesise to a file while one of the three is chosen gets
 * a file of the right length with nothing in it, which is why the setting
 * says so and why [AUTOMATIC] is the default.
 */
object SynthesisRouting {

	const val AUTOMATIC = 0
	const val ACCESSIBILITY = 1
	const val MEDIA = 2
	const val ALL = 3

	val CHOICES = listOf(AUTOMATIC, ACCESSIBILITY, MEDIA, ALL)

	fun clamp(route: Int): Int = if (CHOICES.contains(route)) route else AUTOMATIC

	/** Whether this app plays the speech itself under [route]. */
	fun playsItself(route: Int): Boolean = clamp(route) != AUTOMATIC

	/** Whether the asking app is handed the samples under [route]. */
	fun handsToFramework(route: Int): Boolean = clamp(route) == AUTOMATIC || clamp(route) == ALL

	/** The usage the app's own player carries under [route]: what the
	 *  framework calls the channel. */
	fun usageOf(route: Int): Int = when (clamp(route)) {
		MEDIA -> AudioAttributes.USAGE_MEDIA
		else -> AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
	}

	/**
	 * The app's own player: one streaming [AudioTrack] on one channel, fed
	 * from the synthesis thread as the samples come off the engine.
	 *
	 * One track for the life of the service rather than one per utterance,
	 * because opening one costs tens of milliseconds and a screen reader
	 * says a hundred things a minute. It is remade only when the rate or
	 * the channel moves. Writes block when the track is full, which is the
	 * pacing: the engine makes speech much faster than it is heard and the
	 * track holds a third of a second of it, so an utterance runs no further
	 * ahead of the ear than that and a stop lands within a third of a second
	 * at the worst.
	 */
	class Player {
		@Volatile
		private var track: AudioTrack? = null
		private var rateHz = 0
		private var usage = -1
        private var configuredBufferMs = -1
        private var balance = 0
		private var framesWritten = 0L
		private var headAtBegin = 0L

		@Volatile
		private var cut = false

		/** Makes the track ready for an utterance at [rateHz] on [usage],
		 *  and answers whether there is one. */
		@Synchronized
		fun begin(rateHz: Int, usage: Int, volume: Float, balance: Int = 0, experimental: ResponseExperiment = ResponseExperiment()): Boolean {
            this.balance = balance
			cut = false
			var held = track
            val bufferMs = if (experimental.enabled) experimental.playerBufferMs else -1
			if (held == null || this.rateHz != rateHz || this.usage != usage || configuredBufferMs != bufferMs || held.state != AudioTrack.STATE_INITIALIZED) {
				release()
				held = make(rateHz, usage, bufferMs) ?: return false
				track = held
				this.rateHz = rateHz
				this.usage = usage
                configuredBufferMs = bufferMs
			}
            if (experimental.enabled && android.os.Build.VERSION.SDK_INT >= 31) {
                runCatching { held.setStartThresholdInFrames(maxOf(1, held.bufferSizeInFrames * experimental.startPercent / 100)) }
            }
            ResponseTiming.player = ResponseTiming.PlayerReading(rateHz, held.bufferSizeInFrames,
                if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { held.startThresholdInFrames }.getOrDefault(-1) else -1)
			framesWritten = 0
			// The head is counted from where it stands, because a pause and
			// a flush may or may not have put it back to nought.
			headAtBegin = try {
				held.playbackHeadPosition.toLong() and 0xFFFFFFFFL
			} catch (_: Throwable) {
				0L
			}
			try {
				held.setVolume(volume.coerceIn(0f, 1f))
			} catch (_: Throwable) {
			}
			try {
				if (held.playState != AudioTrack.PLAYSTATE_PLAYING) held.play()
			} catch (broken: Throwable) {
				Log.e(TAG, "the player would not start", broken)
				release()
				return false
			}
			return true
		}

		/** Hands [n] bytes of the buffer to the track, blocking while it is
		 *  full. Answers false when the utterance was cut or the track is
		 *  gone, which is when the caller should stop feeding it. */
		fun write(mono: ByteArray, count: Int): Boolean {
            val buffer = TtsStereo.convert(mono, count, balance)
            val n = buffer.size
			val held = track ?: return false
			var at = 0
			while (at < n && !cut) {
				val wrote = try {
					held.write(buffer, at, n - at)
				} catch (_: Throwable) {
					return false
				}
				if (wrote < 0) return false
				if (wrote == 0) {
					// A full track that will not block -- it has been paused
					// by a cut on another thread -- is given a moment rather
					// than spun on.
					try { Thread.sleep(5) } catch (_: InterruptedException) { return false }
				}
				at += wrote
			}
			synchronized(this) { framesWritten += (n / BYTES_PER_FRAME).toLong() }
			return !cut
		}

		/**
		 * Waits for what has been written to be heard, so the utterance's
		 * finish is reported when it is finished rather than when it was
		 * made. A cut returns at once; so does a track that stops moving
		 * for long enough that waiting is clearly wrong.
		 */
		fun drain() {
			val held = track ?: return
			val written = synchronized(this) { framesWritten }
			if (written <= 0) return
			val want = headAtBegin + written
			var last = -1L
			var still = 0
			val deadline = System.nanoTime() + ((written * 1000L / maxOf(rateHz, 1)) + 2000L) * 1_000_000L
			while (!cut && System.nanoTime() < deadline) {
				val head = try {
					held.playbackHeadPosition.toLong() and 0xFFFFFFFFL
				} catch (_: Throwable) {
					return
				}
				if (head >= want) return
				if (head == last) {
					if (++still >= STALL_TICKS) return
				} else {
					still = 0
					last = head
				}
				try { Thread.sleep(TICK_MS) } catch (_: InterruptedException) { return }
			}
		}

		/** Stops the sound at once and forgets what has not been heard:
		 *  the half of a stop that [AudioTrack] can be told about. Called
		 *  from whichever thread the stop came on. */
		fun cut() {
			cut = true
			val held = track ?: return
			try {
				held.pause()
				held.flush()
			} catch (_: Throwable) {
			}
			synchronized(this) { framesWritten = 0 }
		}

		@Synchronized
		fun release() {
			val held = track ?: return
			track = null
			try { held.pause() } catch (_: Throwable) {}
			try { held.flush() } catch (_: Throwable) {}
			try { held.release() } catch (_: Throwable) {}
		}

		private fun make(rateHz: Int, usage: Int, bufferMs: Int): AudioTrack? {
			val minimum = try {
				AudioTrack.getMinBufferSize(rateHz, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
			} catch (_: Throwable) {
				-1
			}
			if (minimum <= 0) {
				Log.e(TAG, "no player at $rateHz Hz")
				return null
			}
			// A third of a second, and never less than the system's floor.
			val wanted = if (bufferMs < 0) (rateHz * BYTES_PER_FRAME) / 3 else rateHz * BYTES_PER_FRAME * bufferMs / 1000
            val bytes = maxOf(minimum, wanted).let { ((it + BYTES_PER_FRAME - 1) / BYTES_PER_FRAME) * BYTES_PER_FRAME }
			return try {
				val built = AudioTrack.Builder()
					.setAudioAttributes(
						AudioAttributes.Builder()
							.setUsage(usage)
							.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
							.build()
					)
					.setAudioFormat(
						AudioFormat.Builder()
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(rateHz)
							.setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
							.build()
					)
					.setBufferSizeInBytes(bytes)
					.setTransferMode(AudioTrack.MODE_STREAM)
					.build()
				if (built.state != AudioTrack.STATE_INITIALIZED) {
					Log.e(TAG, "the player would not initialise at $rateHz Hz")
					try { built.release() } catch (_: Throwable) {}
					null
				} else {
					built
				}
			} catch (broken: Throwable) {
				Log.e(TAG, "the player would not build", broken)
				null
			}
		}

		companion object {
			private const val TAG = "freeeloquencetts"
			private const val BYTES_PER_FRAME = 4
			private const val TICK_MS = 10L
			private const val STALL_TICKS = 100
		}
	}
}
