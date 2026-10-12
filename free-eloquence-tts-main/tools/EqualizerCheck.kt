package com.blindtechnexus.freeeloquencetts

import kotlin.math.*

/** Host-side checks of the actual Kotlin DSP; no Android runtime or loudspeaker needed. */
fun main() {
    check(Eci.sampleRateIndex(24000) == 24000)
    check(Eci.sampleRateHz(24000) == 24000)
    check(Eci.sampleRateIndex(48000) == 6)
    check(Eci.sampleRateHz(2) == 22050)
    check(EqualizerConfig(listOf(90, -90), 90).normalized() == EqualizerConfig(listOf(12, -12, 0, 0, 0), 12))
    val neutral = VoiceEqualizer(EqualizerConfig(), 11025)
    for (i in -1000..1000) check(neutral.process(i / 1000f) == i / 1000f)
    for (rate in Eci.SAMPLE_RATES) {
        for (band in 0..4) {
            val frequency = EqualizerConfig.FREQUENCIES[band].toDouble().coerceAtMost(rate * 0.45)
            for (db in listOf(-12, 12)) {
                val eq = VoiceEqualizer(EqualizerConfig(List(5) { if (it == band) db else 0 }), rate)
                var energyIn = 0.0
                var energyOut = 0.0
                repeat(rate) { i ->
                    val x = (0.05 * sin(2 * PI * frequency * i / rate)).toFloat()
                    val y = eq.process(x)
                    check(y.isFinite())
                    if (i > rate / 2) { energyIn += x * x; energyOut += y * y }
                }
                val measured = 10 * log10(energyOut / energyIn)
                check(abs(measured - db) < 0.15) { "rate=$rate band=$band gain=$db measured=$measured" }
                eq.reset()
                repeat(1000) { check(eq.process(0f) == 0f) }
            }
        }
        for (mode in AudioOptimization.MODES) {
            val processor = AudioOptimizer(mode, rate, EqualizerConfig(List(5) { 12 }, 12))
            val bytes = ByteArray(4097)
            repeat(50) { turn ->
                repeat(bytes.size / 2) { i ->
                    val n = if ((i + turn) % 2 == 0) 32767 else -32768
                    bytes[2*i] = n.toByte(); bytes[2*i+1] = (n shr 8).toByte()
                }
                bytes[bytes.lastIndex] = 73
                check(processor.process(bytes, bytes.size) == 4096)
                check(bytes.last() == 73.toByte())
                repeat(bytes.size / 2) { i ->
                    val n = ((bytes[2*i+1].toInt() shl 8) or (bytes[2*i].toInt() and 255))
                    check(abs(n / 32768f) <= AudioOptimization.ceiling(mode) + 0.0001f)
                }
            }
            processor.reset()
            bytes.fill(0)
            processor.process(bytes, bytes.size)
            check(bytes.all { it == 0.toByte() })
        }
    }
    check(Resampling.method(-1, AudioOptimization.QUALITY) == Eci.UPSAMPLE_SINC)
    check(Resampling.method(Eci.UPSAMPLE_LINEAR, AudioOptimization.QUALITY) == Eci.UPSAMPLE_LINEAR)
    println("OK: neutralidad, ${Eci.SAMPLE_RATES.size * 10} respuestas de banda, ${Eci.SAMPLE_RATES.size * 3} combinaciones de frecuencia y limitador, extremos, silencio, reinicio y remuestreo")
}
