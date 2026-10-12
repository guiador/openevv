package com.blindtechnexus.freeeloquencetts

/** One immutable snapshot per request; a single preference makes profile changes atomic. */
data class ResponseExperiment(
    val enabled: Boolean = false,
    val pieceChars: Int = 250,
    val leadMs: Int = 150,
    val frameSamples: Int = 2048,
    val ringKiB: Int = 256,
    val streamFromChars: Int = 10000,
    val preparedPieces: Int = 1,
    val textWorkers: Int = 1,
    val playerBufferMs: Int = 333,
    val startPercent: Int = 100,
    val hardCut: Boolean = false,
    val lightOutput: Boolean = false,
    val readSamples: Int = 0
) {
    fun normalized() = copy(
        pieceChars = pieceChars.coerceIn(32, 1000), leadMs = leadMs.coerceIn(20, 500),
        frameSamples = frameSamples.takeIf { it in FRAMES } ?: 2048,
        ringKiB = ringKiB.takeIf { it in RINGS } ?: 256,
        streamFromChars = streamFromChars.coerceIn(0, 10000),
        preparedPieces = preparedPieces.coerceIn(1, 4), textWorkers = textWorkers.coerceIn(1, 3),
        playerBufferMs = playerBufferMs.coerceIn(20, 500), startPercent = startPercent.coerceIn(1, 100),
        readSamples = readSamples.takeIf { it in READ_SAMPLES } ?: 0
    )
    fun effective() = if (enabled) normalized() else ResponseExperiment()
    fun encode() = normalized().run { listOf(if (enabled) 1 else 0, pieceChars, leadMs, frameSamples,
        ringKiB, streamFromChars, preparedPieces, textWorkers, playerBufferMs, startPercent,
        if (hardCut) 1 else 0, if (lightOutput) 1 else 0, readSamples).joinToString(",") }

    companion object {
        val FRAMES = listOf(128, 256, 512, 1024, 2048, 4096)
        val RINGS = listOf(16, 32, 64, 128, 256)
        val READ_SAMPLES = listOf(0, 128, 256, 512, 1024, 2048, 4096)
        // Estimates, not device benchmarks. Profiles leave voice, rate and routing unchanged.
        val PROFILES = listOf(
            ResponseExperiment(enabled = true),
            ResponseExperiment(true, 250, 120, 1024, 64, 1000, 2, 1, 120, 50, readSamples = 1024),
            ResponseExperiment(true, 100, 60, 512, 32, 0, 2, 2, 60, 25, readSamples = 512),
            ResponseExperiment(true, 32, 20, 128, 16, 0, 1, 2, 30, 10, true, readSamples = 128)
        )
        fun decode(value: String?): ResponseExperiment {
            val p = value?.split(',')?.map { it.toIntOrNull() ?: return ResponseExperiment() }
                ?: return ResponseExperiment()
            if (p.size != 12 && p.size != 13) return ResponseExperiment()
            return ResponseExperiment(p[0] == 1, p[1], p[2], p[3], p[4], p[5], p[6], p[7],
                p[8], p[9], p[10] == 1, p[11] == 1, p.getOrElse(12) { 0 }).normalized()
        }
    }
}

/** Times stop at delivery to Android, not at sound leaving the loudspeaker. */
internal object ResponseTiming {
    data class Reading(val preparedMs: Double, val pcmMs: Double, val deliveredMs: Double)
    @Volatile var latest: Reading? = null
    data class PlayerReading(val rateHz: Int, val frames: Int, val startFrames: Int)
    @Volatile var player: PlayerReading? = null
}
