package com.blindtechnexus.freeeloquencetts

/**
 * Text the engine reads wrong, put right before it sees it.
 * Fast single-pass, no regex, handles billion chars in realtime.
 *
 * Fixes:
 * - letter+digit: "teamtalk5" -> "teamtalk 5"
 * - letter+opener: "abc~" -> "abc ~" etc
 * - "books (s)" -> "books(s)"
 * - space before punctuation: "hello ." -> "hello."
 * - the characters a web page carries and a voice cannot say: zero-width
 *   spaces, soft hyphens, byte-order marks, and the several spaces that are
 *   not the space bar (see [WEB_CLEAN])
 *
 * Commas inside numbers are deliberately left alone: "10,000" stays "10,000",
 * because a comma between digits says the number is read whole (see
 * [Numbers], whose first rule is exactly that). Stripping them here would
 * turn a formatted number into a long plain run and have it read digit by
 * digit, which is the opposite of what the comma means.
 */
object TextFixes {

	private const val OPENERS = "~#\$%^*({|[<\"•"

	private val OPENERS_SET = OPENERS.toSet()
	private val MARKS = setOf(':', '.', '!', ';', ',', '?')

	fun apply(raw: String): String {
		if (raw.isEmpty()) return raw
		// The characters a page leaves behind, taken out first: everything
		// below is about where words end and marks begin, and a zero-width
		// space in the middle of a word is a boundary that is not one.
		val text = webClean(raw)
		if (text.isEmpty()) return text

		// Quick scan if any fix needed
		var needLetterDigit = false
		var needOpener = false
		var needBracket = false
		var needMark = false
		for (c in text) {
			if (!needLetterDigit && c.isLetter()) needLetterDigit = true
			if (!needOpener && c in OPENERS_SET) needOpener = true
			if (!needBracket && c == '(') needBracket = true
			if (!needMark && c in MARKS) needMark = true
			if (needLetterDigit && needOpener && needBracket && needMark) break
		}
		if (!needLetterDigit && !needOpener && !needBracket && !needMark) return text

		val n = text.length
		val out = StringBuilder(n + 32)
		var i = 0
		while (i < n) {
			val c = text[i]

			// Handle spaces before marks and "(s)" - look ahead when we see whitespace
			if (c.isWhitespace()) {
				var j = i
				while (j < n && text[j].isWhitespace()) j++
				if (j < n) {
					val next = text[j]
					// Check "(s)" case: spaces + "(s)" after a letter
					if (needBracket && next == '(' && j + 2 < n && text[j + 1] == 's' && text[j + 2] == ')') {
						// If previous output ends with letter, skip spaces
						if (out.isNotEmpty() && out.last().isLetter()) {
							// Skip spaces, will handle '(' in next iteration
							i = j
							continue
						}
					}
					// Space before punctuation mark: if next is mark and not followed by letter/digit
					if (needMark && next in MARKS) {
						val afterNext = if (j + 1 < n) text[j + 1] else '\u0000'
						if (afterNext == '\u0000' || (!afterNext.isLetter() && !afterNext.isDigit())) {
							// Skip spaces
							i = j
							continue
						}
					}
				}
				// Otherwise keep single space
				out.append(' ')
				i = j
				continue
			}

			// Letter + digit: insert space
			if (c.isDigit() && out.isNotEmpty()) {
				val prev = out.last()
				if (prev.isLetter()) {
					out.append(' ')
				}
			}

			// Letter + opener: insert space
			if (c in OPENERS_SET && out.isNotEmpty()) {
				val prev = out.last()
				if (prev.isLetter()) {
					// Check if previous char in original was not already space
					if (prev != ' ') out.append(' ')
				}
			}

			out.append(c)
			i++
		}
		return out.toString()
	}

	/**
	 * The characters a web page carries that a voice cannot say.
	 *
	 * A page arrives from a browser already flattened to text, and the
	 * flattening leaves things in it: a zero-width space wherever the markup
	 * allowed a line break, a soft hyphen wherever a word could be split, a
	 * byte-order mark at the front, an object replacement character wherever
	 * an image was, and a non-breaking space wherever the layout wanted one.
	 * None of them is a character anybody means to hear. The engine is given
	 * them anyway -- they are in the text it was handed -- and what it does
	 * with them is say a pause, or nothing, or the word "space", any of
	 * which is a wrong word in the middle of a sentence.
	 *
	 * So they are taken out here, before anything else reads the text: the
	 * invisible ones are dropped and the spaces that are not spaces become
	 * the space bar. The whole pass is one scan and only runs when one of
	 * these characters is in the text at all, which is never for text a
	 * person typed and often for text a page was.
	 */
	private fun isWebNoise(c: Char): Boolean = when (c) {
		'\u00A0', '\u1680', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004', '\u2005',
		'\u2006', '\u2007', '\u2008', '\u2009', '\u200A', '\u202F', '\u205F', '\u3000' -> true
		'\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF', '\u00AD', '\uFFFC', '\u180E' -> true
		else -> false
	}

	private fun webClean(text: String): String {
		var needed = false
		for (c in text) {
			if (isWebNoise(c)) {
				needed = true
				break
			}
		}
		if (!needed) return text
		val out = StringBuilder(text.length)
		for (c in text) {
			when {
				c == '\u00A0' || c == '\u1680' || (c in '\u2000'..'\u200A') ||
					c == '\u202F' || c == '\u205F' || c == '\u3000' -> out.append(' ')
				c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060' ||
					c == '\uFEFF' || c == '\u00AD' || c == '\uFFFC' || c == '\u180E' -> Unit
				else -> out.append(c)
			}
		}
		return out.toString()
	}
}
