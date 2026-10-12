package com.blindtechnexus.freeeloquencetts

/**
 * Shortening the gaps the engine leaves at punctuation and at the end.
 * Fast single-pass, no regex, low latency.
 */
object Pauses {

	const val KEEP = 0
	const val END_ONLY = 1
	const val ALL = 2

	private const val MARKS = "-,.:;?!–—"
	private val MARK_SET = MARKS.toSet()
	private const val BRIEF = "`p1"
	private const val AT_THE_END = "`p100"
	const val AT_A_BOUNDARY = " `p1"

	fun apply(text: String, mode: Int, last: Boolean): String {
		if (mode == KEEP || text.isEmpty()) return text
		var out = text
		if (mode == ALL) {
			// Fast check if any mark present
			var hasMark = false
			for (c in text) if (c in MARK_SET) { hasMark = true; break }
			if (hasMark) out = insertBriefPauses(text)
		}
		if (last && !endsWithMark(out)) out = "$out $AT_THE_END"
		return out
	}

	private fun insertBriefPauses(text: String): String {
		val n = text.length
		val sb = StringBuilder(n + n / 10 + 16)
		var i = 0
		while (i < n) {
			val c = text[i]
			if (c in MARK_SET) {
				// Check if start of run
				val isRunStart = i == 0 || text[i - 1] != c
				if (isRunStart) {
					// Find run of same mark
					var j = i + 1
					while (j < n && text[j] == c) j++
					// Check preceding char: must be letter/digit/space
					val prevOk = if (i > 0) {
						val p = text[i - 1]
						p.isLetterOrDigit() || p.isWhitespace()
					} else false
					// Check following char: whitespace, slash, backslash, or end
					val nextOk = if (j >= n) true else {
						val nxt = text[j]
						nxt.isWhitespace() || nxt == '/' || nxt == '\\'
					}
					if (prevOk && nextOk) {
						// Insert brief pause before this run
						sb.append(' ')
						sb.append(BRIEF)
						// Append the run itself
						for (k in i until j) sb.append(text[k])
						i = j
						continue
					}
				}
			}
			sb.append(c)
			i++
		}
		return sb.toString()
	}

	private fun endsWithMark(text: String): Boolean {
		var at = text.length
		while (at > 0 && text[at - 1].isWhitespace()) at--
		return at > 0 && text[at - 1] in MARK_SET
	}
}
