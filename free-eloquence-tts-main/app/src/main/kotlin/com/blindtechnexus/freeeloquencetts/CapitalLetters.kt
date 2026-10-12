package com.blindtechnexus.freeeloquencetts

/**
 * How a single capital letter is said when alone.
 * Fast single-pass, no regex, low latency.
 */
object CapitalLetters {

	const val NONE = 0
	const val CAP = 1
	const val PITCH = 2

	val MODES = listOf(NONE, CAP, PITCH)

	private const val UP = "`vbst+6 "
	private const val DOWN = "`vbst-6 "

	fun apply(text: String, mode: Int): String {
		if (mode == NONE || text.isEmpty()) return text
		// Quick check: any single capital?
		var hasSingle = false
		for (i in text.indices) {
			val c = text[i]
			if (c in 'A'..'Z') {
				val prevOk = i == 0 || !(text[i - 1].isLetter() || text[i - 1] == '\'')
				val nextOk = i == text.length - 1 || !(text[i + 1].isLetter() || text[i + 1] == '\'')
				if (prevOk && nextOk) { hasSingle = true; break }
			}
		}
		if (!hasSingle) return text

		val sb = StringBuilder(text.length + 32)
		var i = 0
		while (i < text.length) {
			val c = text[i]
			if (c in 'A'..'Z') {
				val prevOk = i == 0 || !(text[i - 1].isLetter() || text[i - 1] == '\'')
				val nextOk = i == text.length - 1 || !(text[i + 1].isLetter() || text[i + 1] == '\'')
				if (prevOk && nextOk) {
					when (mode) {
						CAP -> {
							sb.append(" cap ")
							sb.append(c)
							sb.append(' ')
						}
						PITCH -> {
							sb.append(' ')
							sb.append(UP)
							sb.append(c)
							sb.append(' ')
							sb.append(DOWN)
						}
						else -> sb.append(c)
					}
					i++
					continue
				}
			}
			sb.append(c)
			i++
		}
		return sb.toString()
	}
}
