package com.blindtechnexus.freeeloquencetts

import kotlin.math.*

/** Immutable settings, copied once per utterance so filters never change mid-buffer. */
data class EqualizerConfig(val bands: List<Int> = List(5) { 0 }, val preamp: Int = 0) {
    fun normalized() = EqualizerConfig(List(5) { bands.getOrElse(it) { 0 }.coerceIn(-12, 12) }, preamp.coerceIn(-12, 12))
    companion object {
        val PRESETS = listOf(
            EqualizerConfig(),
            EqualizerConfig(listOf(6, 4, 0, -2, -3), -4),
            EqualizerConfig(listOf(-3, -2, 1, 4, 5), -4),
            EqualizerConfig(listOf(2, 1, 0, -4, -6), -2),
            EqualizerConfig(listOf(-4, -2, 2, 4, 1), -3)
        )
        val FREQUENCIES = listOf(120, 350, 1000, 2500, 3500)
    }
}

/** Five RBJ peaking biquads (Q=0.7). Runs on mono PCM before the output limiter. */
internal class VoiceEqualizer(config: EqualizerConfig, rate: Int) {
    private val rateHz = rate.coerceAtLeast(1)
    private val settled = config.normalized()
    private val gain = 10.0.pow(settled.preamp / 20.0)
    private val filters = settled.bands.mapIndexedNotNull { i, db ->
        if (db == 0) null else Biquad(EqualizerConfig.FREQUENCIES[i].toDouble().coerceAtMost(rateHz * 0.45), db, rateHz)
    }.toTypedArray()
    fun process(value: Float): Float {
        var out = value.toDouble() * gain
        for (filter in filters) out = filter.process(out)
        return out.toFloat()
    }
    fun reset() = filters.forEach { it.reset() }

    private class Biquad(frequency: Double, db: Int, rate: Int) {
        private val a = 10.0.pow(db / 40.0)
        private val w = 2 * PI * frequency / rate.coerceAtLeast(1)
        private val alpha = sin(w) / (2 * 0.7)
        private val a0 = 1 + alpha / a
        private val b0 = (1 + alpha * a) / a0
        private val b1 = -2 * cos(w) / a0
        private val b2 = (1 - alpha * a) / a0
        private val a1 = b1
        private val a2 = (1 - alpha / a) / a0
        private var z1 = 0.0
        private var z2 = 0.0
        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
        fun reset() { z1 = 0.0; z2 = 0.0 }
    }
}

/** -1 delegates to the quality profile; all other entries are native CVT values. */
object Resampling {
    val METHODS = listOf(-1, Eci.UPSAMPLE_HOLD, Eci.UPSAMPLE_LINEAR, Eci.UPSAMPLE_CUBIC, Eci.UPSAMPLE_SINC)
    val LABELS = listOf(R.string.eq_text_16, R.string.eq_text_17, R.string.eq_text_18, R.string.eq_text_19, R.string.eq_text_20)
    fun label(context: android.content.Context, selected: Int, optimization: Int): String {
        val index = METHODS.indexOf(selected).coerceAtLeast(0)
        val name = context.getString(LABELS[index])
        return if (index == 0) context.getString(R.string.resampling_automatic_value, name,
            context.getString(LABELS[METHODS.indexOf(method(selected, optimization))])) else name
    }
    fun method(selected: Int, optimization: Int) =
        if (selected in METHODS && selected != -1) selected else AudioOptimization.methodFor(optimization)
}
