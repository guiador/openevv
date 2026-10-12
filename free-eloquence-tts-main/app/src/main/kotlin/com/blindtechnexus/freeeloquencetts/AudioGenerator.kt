package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Text turned into an audio file, in the voice and with the settings.
 *
 * The settings screen can play a sample, but a sample evaporates; sometimes
 * the words are wanted as a file -- a greeting recorded in the voice, a
 * passage kept for a phone with no engine on it. This is that: the text
 * through the same pipeline the service reads (see [TextPipeline]), spoken
 * by an engine of its own on a thread of its own, and the samples written
 * out as a WAV, 16-bit mono at the listener's sample rate.
 *
 * One generation at a time. A second [render] while one is running stops
 * the first and starts over, because two engines rendering at once is two
 * phones' worth of memory for one file. [cancel] stops the work without
 * starting any, and everything answers on the main thread, where the screen
 * that asked is waiting.
 *
 * What comes back is a file under the cache, in the directory the file
 * provider publishes (see file_paths.xml), so the screen can share it with
 * whoever wants it or save it wherever the listener says. The cache is the
 * system's to empty, so a file nobody saved costs nothing to forget.
 */
class AudioGenerator(context: Context) {

	private val app = context.applicationContext
	private val main = Handler(Looper.getMainLooper())

	@Volatile
	private var engine: EvvEngine? = null

	@Volatile
	private var generation = 0

	@Volatile
	private var working = false

	/** Whether a generation is running right now. */
	val busy: Boolean get() = working

	/**
	 * Says [text] into a file and hands it to [done], or nothing when the
	 * text held nothing to say, the engine refused it, or the generation
	 * was cancelled. [progress] hears how many of the text's characters
	 * have been spoken so far, on the calling thread.
	 */
	fun render(
		text: String,
		settings: Settings,
		progress: (doneChars: Int, totalChars: Int) -> Unit,
		done: (file: File?) -> Unit
	) {
		cancel()
		val mine = ++generation
		val total = text.length
		working = true
		Thread({
			var file: File? = null
			try {
				file = renderOnThread(text, total, settings, mine, progress)
			} catch (broken: Throwable) {
				Log.e(TAG, "the generation failed", broken)
				file = null
			} finally {
				if (mine == generation) working = false
				val made = if (mine == generation) file else null
				main.post { done(made) }
			}
		}, "evv-generate").also { it.isDaemon = true }.start()
	}

	private fun renderOnThread(
		text: String,
		total: Int,
		settings: Settings,
		mine: Int,
		progress: (doneChars: Int, totalChars: Int) -> Unit
	): File? {
		if (text.isBlank() || total > MAX_CHARS) return null
		if (!EvvNative.loaded) return null
		// The language the settings screen chose, so the file that comes out
		// is the voice that would have been heard rather than the American
		// one regardless: the two are different modules and different
		// accents, and a generator that ignored the setting would be a
		// generator that saved somebody the wrong voice.
		val e = EvvEngine.open(settings.language)?.also { engine = it } ?: return null
		try {
			e.setSampleRate(settings.sampleRateHz)
			e.setUpsampleMethod(Resampling.method(settings.resampling, settings.audioOptimization))
            e.setSynthesisMode(settings.synthesisMode)
            e.setPauseMode(settings.pauses)
			e.setAbbreviations(settings.abbreviations)
			e.applyVoice(settings.voice, settings.shape(settings.voice))
			val index = try {
				UserDictionary.snapshot(app).index
			} catch (broken: Throwable) {
				WordIndex.EMPTY
			}
			val abbreviations = try {
				Abbreviations.entries(app)
			} catch (broken: Throwable) {
				emptyList()
			}
			val prosody = Prosody.prefix(settings.phrasePrediction)
			val optimizer = AudioOptimizer(settings.audioOptimization, e.sampleRateHz, settings.equalizer)
			val samples = ByteArrayOutputStream(65536)
			val buffer = ByteArray(READ_CHUNK)
			var spokenChars = 0
			val pieces = TextPipeline.pieces(text, settings, index, abbreviations, e.language).iterator()
			var held: TextPieces.Piece? = try {
				if (pieces.hasNext()) pieces.next() else null
			} catch (broken: Throwable) {
				null
			}
			if (held == null) return null
			while (mine == generation) {
				val piece = held ?: break
				val next = try {
					if (pieces.hasNext()) pieces.next() else null
				} catch (broken: Throwable) {
					null
				}
				val last = next == null
				var audio = try {
					prosody + Pauses.apply(piece.text, settings.pauses, last)
				} catch (broken: Throwable) {
					prosody + piece.text
				}
				if (piece.forced && !last) audio += Pauses.AT_A_BOUNDARY
				spokenChars += piece.text.length
				try {
					progress(spokenChars.coerceAtMost(total), total)
				} catch (broken: Throwable) {
					// Progress is a courtesy, not a contract.
				}
				if (EvvEngine.isBlankSpeech(audio)) {
					held = next
					continue
				}
				if (!e.speak(audio, isStopped = { mine != generation })) {
					Log.e(TAG, "the engine refused ${audio.length} chars of the generation")
					return null
				}
				while (mine == generation) {
					val n = e.read(buffer)
					if (n == EvvTtsService.TIMED_OUT) {
						Log.e(TAG, "the engine went silent in the middle of the generation")
						return null
					}
					if (n <= 0) break
					optimizer.process(buffer, n)
					samples.write(buffer, 0, n)
				}
				held = next
			}
			if (mine != generation) return null
			if (samples.size() == 0) return null
			return writeWav(samples.toByteArray(), e.sampleRateHz)
		} finally {
			engine = null
			try {
				e.close()
			} catch (broken: Throwable) {
			}
		}
	}

	/** Stops the generation running now, if there is one. */
	fun cancel() {
		generation++
		try {
			engine?.stop()
		} catch (broken: Throwable) {
		}
	}

	/** [samples] as a WAV file under the cache, or nothing when it could
	 *  not be written. */
	private fun writeWav(samples: ByteArray, rateHz: Int): File? {
		return try {
			val into = File(app.cacheDir, AUDIO_DIR)
			if (!into.isDirectory) into.mkdirs()
			val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
			val file = File(into, "eloquence-$stamp.wav")
			DataOutputStream(FileOutputStream(file)).use { out ->
				val frames = samples.size / 2
				val data = frames * 2
				out.writeBytes("RIFF")
				out.writeIntLE(36 + data)
				out.writeBytes("WAVE")
				out.writeBytes("fmt ")
				out.writeIntLE(16)
				out.writeShortLE(1) // PCM
				out.writeShortLE(1) // mono
				out.writeIntLE(rateHz)
				out.writeIntLE(rateHz * 2)
				out.writeShortLE(2) // block align
				out.writeShortLE(16) // bits per sample
				out.writeBytes("data")
				out.writeIntLE(data)
				out.write(samples, 0, data)
			}
			file
		} catch (broken: Throwable) {
			Log.e(TAG, "the file could not be written", broken)
			null
		}
	}

	private fun DataOutputStream.writeIntLE(v: Int) {
		write(v and 0xFF)
		write((v ushr 8) and 0xFF)
		write((v ushr 16) and 0xFF)
		write((v ushr 24) and 0xFF)
	}

	private fun DataOutputStream.writeShortLE(v: Int) {
		write(v and 0xFF)
		write((v ushr 8) and 0xFF)
	}

	companion object {
		const val TAG = "freeeloquencetts"

		/** Where generated files live under the cache, published by the
		 *  file provider so they can be shared. */
		const val AUDIO_DIR = "audio"

		/** The longest text one generation takes. Fifty thousand
		 *  characters is the better part of an hour of speech; past that
		 *  the text wants cutting, not rendering. */
		const val MAX_CHARS = 50_000

		private const val READ_CHUNK = 8192
	}
}
