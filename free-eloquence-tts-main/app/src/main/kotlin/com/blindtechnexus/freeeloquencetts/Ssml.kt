package com.blindtechnexus.freeeloquencetts

/**
 * SSML and other XML-like tags removal, fast single-pass, no regex.
 * Keeps words between tags, removes tags, collapses spaces.
 */
object Ssml {

	fun strip(text: String): String {
		if (text.isEmpty()) return text
		if ('<' !in text) return text
		// Fast path: no '>' after '<' means no tag
		var hasTag = false
		var i = 0
		while (i < text.length) {
			if (text[i] == '<') {
				val close = text.indexOf('>', i + 1)
				if (close != -1) { hasTag = true; break }
			}
			i++
		}
		if (!hasTag) return text

		val sb = StringBuilder(text.length)
		var inside = false
		var lastWasSpace = false
		for (c in text) {
			when {
				c == '<' -> {
					inside = true
					if (!lastWasSpace && sb.isNotEmpty()) {
						sb.append(' ')
						lastWasSpace = true
					}
				}
				c == '>' && inside -> {
					inside = false
				}
				inside -> { /* skip */ }
				c.isWhitespace() -> {
					if (!lastWasSpace) {
						sb.append(' ')
						lastWasSpace = true
					}
				}
				else -> {
					sb.append(c)
					lastWasSpace = false
				}
			}
		}
		return sb.toString().trim()
	}
}
