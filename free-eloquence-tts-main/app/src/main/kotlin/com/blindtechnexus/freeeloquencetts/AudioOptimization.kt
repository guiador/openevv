package com.blindtechnexus.freeeloquencetts

import kotlin.math.abs
import kotlin.math.tanh

/**
 * How the samples are finished before they are handed over.
 *
 * The engine makes the voice; this decides how loud and how clean it arrives.
 * Three modes, one setting, and every one of them is a fixed, documented
 * chain rather than a pile of knobs:
 *
 * Balanced is the default: a light touch that keeps the voice as Eloquence
 * made it, only sitting it at a healthy level with nothing clipping.
 *
 * Optimized for Volume is the loudest this voice gets: the engine's own
 * upsampling holds every sample instead of filtering the band (see
 * [Eci.UPSAMPLE_HOLD]), and the level rides up to just under full scale
 * behind a limiter, so a phone in a noisy pocket is still heard.
 *
 * Optimized for Quality is the cleanest: the engine's true resampling (see
 * [Eci.UPSAMPLE_SINC]), direct current removed, headroom kept, and a soft
 * ceiling nothing crosses, so what arrives is the speech and only the speech.
 *
 * Two halves. The resampling method is the engine's business and travels down
 * through [EvvEngine.setUpsampleMethod]; the level, the direct-current
 * removal and the limiting happen here, on the PCM on its way to the
 * framework, in [AudioOptimizer]. Both halves read the same mode, so the
 * setting is one choice with one name and not two that can disagree.
 */
object AudioOptimization {

	/** The default: clear, honest, and cheap. */
	const val BALANCED = 0

	/** The loudest: held upsampling and the level ridden high. */
	const val VOLUME = 1

	/** The cleanest: true resampling, headroom, and a soft ceiling. */
	const val QUALITY = 2

	val MODES = listOf(BALANCED, VOLUME, QUALITY)

	/** Which of the engine's upsampling methods [mode] runs on. */
	fun methodFor(mode: Int): Int = when (mode) {
		VOLUME -> Eci.UPSAMPLE_HOLD
		QUALITY -> Eci.UPSAMPLE_SINC
		else -> Eci.UPSAMPLE_CUBIC
	}

	/**
	 * Where the level rides, as a fraction of full scale. Volume sits
	 * nearest the ceiling because that is the whole of what it is for;
	 * quality sits six decibels down, which is headroom a limiter never
	 * has to apologise for; balanced sits between the two.
	 */
	fun targetPeak(mode: Int): Float = when (mode) {
		VOLUME -> 0.891f // -1 dBFS.
		QUALITY -> 0.501f // -6 dBFS.
		else -> 0.708f // -3 dBFS.
	}

	/**
	 * How far the level may be turned up, as a multiplier. Volume is
	 * allowed the most because a quiet voice in a loud place is the case
	 * it exists for; quality the least, because turning a noise floor up
	 * is not making anything better.
	 */
	fun maxGain(mode: Int): Float = when (mode) {
		VOLUME -> 8.0f
		QUALITY -> 2.0f
		else -> 4.0f
	}

	/** How far the level may be turned down, as a multiplier. Only quality
	 *  ever turns anything down: the other two never make a voice quieter
	 *  than the engine made it. */
	fun minGain(mode: Int): Float = when (mode) {
		QUALITY -> 0.25f
		else -> 1.0f
	}

	/**
	 * What no sample crosses, as a fraction of full scale. Volume kisses
	 * the ceiling; quality leaves a decibel on the table, which is what
	 * keeps a true peak inside it after anything downstream resamples.
	 */
	fun ceiling(mode: Int): Float = when (mode) {
		VOLUME -> 0.98f
		QUALITY -> 0.89f
		else -> 0.95f
	}

	/** Anything the setting never offered falls back to the default, rather
	 *  than running wild or silent. */
	fun clamp(mode: Int): Int = if (MODES.contains(mode)) mode else BALANCED
}

/**
 * One utterance's worth of finishing, applied chunk by chunk.
 *
 * The chain, per sample, is three standard stages in a fixed order:
 *
 * 1. A one-pole direct-current blocker, so an offset the engine never meant
 *    does not eat headroom or click at the edges of an utterance.
 * 2. An adaptive gain that rides the level towards [AudioOptimization.targetPeak],
 *    attacking at once when the sound gets louder -- which is what keeps a
 *    sudden shout inside the ceiling -- and releasing slowly when it gets
 *    quieter, so the floor does not pump.
 * 3. A soft limiter built on the hyperbolic tangent, which passes small
 *    signals untouched and rounds the top into the ceiling instead of
 *    clipping it.
 *
 * Stateful, and deliberately so: the blocker and the gain both remember the
 * chunk before, which is what joins one chunk to the next with no seam. One
 * instance per utterance, made fresh (or [reset]) when an utterance begins,
 * so the end of a shout never decides the start of a whisper.
 *
 * In place, on 16-bit little-endian mono PCM, answering how many bytes were
 * processed -- which is [count] rounded down to a whole sample, since a
 * trailing odd byte is not a sample and is left alone.
 */
class AudioOptimizer(mode: Int, sampleRateHz: Int, equalizer: EqualizerConfig = EqualizerConfig(), private val lightOutput: Boolean = false) {
	private val equalizer = VoiceEqualizer(equalizer, sampleRateHz)

	private val mode = AudioOptimization.clamp(mode)

	private val target = AudioOptimization.targetPeak(this.mode)
	private val lowest = AudioOptimization.minGain(this.mode)
	private val highest = AudioOptimization.maxGain(this.mode)
	private val top = AudioOptimization.ceiling(this.mode)

	/** How long the gain takes to come most of the way back up after
	 *  ducking, in seconds. Slow on purpose: a gain that hurries back up
	 *  breathes, and breathing is heard. Volume hurries a little, because
	 *  staying ducked is staying quiet, which is the one thing that mode
	 *  refuses to do. */
	private val releaseSeconds: Float = if (this.mode == AudioOptimization.VOLUME) 0.06f else 0.15f

	/** The rate this instance runs at. Two of the three stages have time in
	 *  them -- the blocker has a corner frequency and the gain has a release
	 *  -- and both are fixed in seconds and hertz here and turned into
	 *  per-sample and per-chunk numbers from the rate, so the chain sounds
	 *  the same at eight thousand hertz and at forty-eight. The old fixed
	 *  numbers were right at eleven thousand and twenty five and wrong
	 *  everywhere else: the blocker's corner climbed to nearly forty hertz
	 *  at forty-eight thousand, under the lowest of a male voice, and the
	 *  release ran four times faster because the chunks were four times
	 *  shorter. */
	private val rateHz = sampleRateHz.coerceAtLeast(1)

	/** The blocker's pole for this rate: R = exp(-2 pi f / rate), with f
	 *  the corner, so the corner is the same frequency at every rate. */
	private val blockerR: Float = blockerFor(rateHz)

	private var blockedIn = 0.0f
	private var blockedOut = 0.0f
	private var gain = 1.0f

	fun reset() {
		equalizer.reset()
		blockedIn = 0.0f
		blockedOut = 0.0f
		gain = 1.0f
	}

	fun process(chunk: ByteArray, count: Int): Int {
		val samples = count.coerceIn(0, chunk.size) / 2
		if (samples == 0) return 0
		// The peak first, over the whole chunk, so the gain for the chunk is
		// decided by the chunk rather than by the sample under the cursor,
		// which would duck the middle of a shout and leave its start loud.
		var peak = 0.0f
		if (!lightOutput) for (at in 0 until samples) {
			val low = chunk[at * 2].toInt() and 0xFF
			val high = chunk[at * 2 + 1].toInt()
			val signed = (high shl 8) or low
			val over = abs(signed / 32768.0f)
			if (over > peak) peak = over
		}
		val want = gainFor(peak, target, lowest, highest)
		// Attack at once, release slowly: see the chain above. The release
		// is a fraction of the way per chunk, and the fraction is the
		// chunk's own length against the release time, so a chunk of a
		// tenth of a second moves the gain as far as ten chunks of a
		// hundredth would, at any rate.
		gain = if (want < gain) want else gain + (want - gain) * releaseFor(samples, rateHz, releaseSeconds)
		val applying = if (lightOutput) 1f else gain
		for (at in 0 until samples) {
			val low = chunk[at * 2].toInt() and 0xFF
			val high = chunk[at * 2 + 1].toInt()
			val signed = (high shl 8) or low
			var v = signed / 32768.0f
			// The blocker: y[n] = x[n] - x[n-1] + R * y[n-1], with R a hair
			// under one. Standard one-pole, flat everywhere but the very
			// bottom, where it falls steeply to nothing.
			val out = v - blockedIn + blockerR * blockedOut
			blockedIn = v
			blockedOut = out
			v = equalizer.process(out * applying)
			// The limiter: the ceiling times the hyperbolic tangent of the
			// signal over the ceiling. Linear for small signals, round at
			// the top, and nothing crosses.
			val limited = if (lightOutput) v.coerceIn(-top, top) else top * tanh((v / top).toDouble()).toFloat()
			val back = (limited * 32768.0f).toInt().coerceIn(-32768, 32767)
			chunk[at * 2] = (back and 0xFF).toByte()
			chunk[at * 2 + 1] = ((back shr 8) and 0xFF).toByte()
		}
		return samples * 2
	}

	companion object {
		/** The blocker's corner, in hertz: three decibels down here, flat
		 *  above. Nine hertz, which is what the old fixed pole of 0.995 gave
		 *  at the engine's own eleven thousand and twenty five: everything a
		 *  voice carries passes, and a steady offset does not. */
		const val BLOCKER_HZ = 9.0

		/** The old fixed pole, kept as the number the corner was taken
		 *  from and as what [blockerFor] answers at the engine's own rate. */
		const val BLOCKER_R = 0.995f

		/** The one-pole blocker's coefficient for [rateHz], which puts the
		 *  corner at [BLOCKER_HZ] whatever the rate. */
		fun blockerFor(rateHz: Int): Float =
			kotlin.math.exp(-2.0 * Math.PI * BLOCKER_HZ / rateHz.coerceAtLeast(1)).toFloat()

		/** How far, as a fraction of the distance, the gain moves back up
		 *  over a chunk of [samples] at [rateHz], for a release that takes
		 *  [seconds] to get most of the way: one minus e to the minus the
		 *  chunk's length over the release time. A whole release time in one
		 *  chunk moves it sixty-three per cent of the way, which is what the
		 *  time constant means. */
		fun releaseFor(samples: Int, rateHz: Int, seconds: Float): Float {
			if (samples <= 0 || seconds <= 0f) return 1.0f
			val span = samples.toDouble() / rateHz.coerceAtLeast(1)
			return (1.0 - kotlin.math.exp(-span / seconds)).toFloat().coerceIn(0f, 1f)
		}

		/**
		 * The gain that puts [peak] at [target], inside [minGain] and
		 * [maxGain]. Silence wants no gain at all, which is one rather
		 * than infinity: turning nothing up is how a noise floor becomes
		 * a hiss.
		 */
		fun gainFor(peak: Float, target: Float, minGain: Float, maxGain: Float): Float {
			if (peak <= 1e-6f) return 1.0f
			return (target / peak).coerceIn(minGain, maxGain)
		}

		/**
		 * [v] through the limiter alone, for whoever wants the curve
		 * without the chain. Answers inside [-ceiling, ceiling], always.
		 */
		fun softClip(v: Float, ceiling: Float): Float =
			ceiling * tanh((v / ceiling).toDouble()).toFloat()

		/**
		 * The peak of [samples], as a fraction of full scale. Pure, so a
		 * test can hold it without an instance.
		 */
		fun peakOf(samples: ShortArray): Float {
			var peak = 0.0f
			for (s in samples) {
				val over = abs(s / 32768.0f)
				if (over > peak) peak = over
			}
			return peak
		}
	}
}
