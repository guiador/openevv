package com.blindtechnexus.freeeloquencetts

/**
 * How a run of digits is read, before the engine sees it.
 *
 * Modes:
 * - AS_IS: leave every number to the engine, which says "nine hundred
 *   eighty-five thousand two hundred fifty-six" for 985256.
 * - DIGITS: a long run one digit at a time -- 985256 is "9 8 5 2 5 6".
 * - PAIRS: a long run two at a time from the left -- 985256 is "98 52 56".
 * - TRIPLETS: a long run three at a time -- 985256 is "985 256".
 *
 * The rules, which are the same in every mode but AS_IS and are meant to be
 * strong enough that nothing surprising ever happens to a number:
 *
 * 1. A run of fewer than [LONG_FROM] digits -- five or fewer -- is one
 *    number and is never broken up. Years, times, prices, PINs, page
 *    numbers, percentages and counts are all short, and "2026" read as
 *    "20 26" is a year nobody recognises. Only a run of six or more digits
 *    is ever regrouped, in any mode.
 *
 * 2. A number written with thousands separators is the number, whole.
 *    "1,000,000" and "10.000.000" (the continental spelling) are copied as
 *    written in every mode, because the separator is what says "a million"
 *    and breaking it at the separator would be reading the punctuation and
 *    losing the number. A separator has to be followed by exactly three
 *    digits to count as one; "5, 2026" and "1,0000" are two numbers.
 *
 * 3. The digits after a decimal point are a fraction and are left alone.
 *    "3.1415926" is three point one four one five nine two six to the
 *    engine already, and regrouping the fraction would make it a telephone
 *    number. A point counts as decimal only when there are digits on both
 *    sides of it; "Section 7.123456" is still a fraction, which is the
 *    safer wrong answer.
 *
 * 4. Otherwise a long run is grouped from the left, telephone style, so an
 *    odd length keeps its short group at the end: "1234567" in pairs is
 *    "12 34 56 7" and in triplets "123 456 7". A group that opens with a
 *    zero is said a digit at a time, so the zero is heard: "0412" in pairs
 *    is "0 4 12" rather than "four twelve".
 *
 * 5. A plus before digits is the dialling kind and is kept with a space
 *    after it ("+911" stays callable), punctuation of every other kind
 *    splits runs ("12:34" is two runs), and only the ten ASCII digits are
 *    touched. Everything that is not a run of digits comes through exactly
 *    as it was written.
 *
 * The whole thing is one pass over the text with no regular expressions, so
 * a page of text costs what copying it costs, and a text with nothing to do
 * -- which is most text -- comes back as the very string it was.
 */
object Numbers {

	const val AS_IS = 0
	const val DIGITS = 1
	const val PAIRS = 2
	const val TRIPLETS = 3

	val MODES = listOf(AS_IS, DIGITS, PAIRS, TRIPLETS)

	/** A run this long or longer is regrouped; anything shorter is one
	 *  number, in every mode but AS_IS. */
	const val LONG_FROM = 6

	/** A mode outside the list is the default, so a preference written by
	 *  another build is a behaviour and not a crash. */
	fun clamp(mode: Int): Int = if (MODES.contains(mode)) mode else AS_IS

	/** How many digits make one group in [mode]: one, two or three. */
	fun groupSize(mode: Int): Int = when (mode) {
		PAIRS -> 2
		TRIPLETS -> 3
		else -> 1
	}

	fun apply(text: String, mode: Int): String {
		val settled = clamp(mode)
		if (settled == AS_IS) return text
		if (text.isEmpty()) return text

		// Nothing to do without a run of LONG_FROM digits somewhere, and
		// the plus is the one other thing this pass ever rewrites.
		if (!hasWork(text)) return text

		var changed = false
		val out = StringBuilder(text.length + text.length / 2 + 16)
		val n = text.length
		var i = 0
		while (i < n) {
			val c = text[i]
			if (c == '`') {
				// An engine annotation -- "`vbst+6", "`p1" -- is carried
				// through untouched: it is not text and its digits are not
				// a number.
				var k = i
				while (k < n && !text[k].isWhitespace()) k++
				out.append(text, i, k)
				i = k
				continue
			}
			if (c == '+' && i + 1 < n && text[i + 1].isAsciiDigit()) {
				// The dialling plus keeps a space after it, so it is said
				// as "plus" in front of the number rather than swallowed.
				out.append('+').append(' ')
				changed = true
				i++
				continue
			}
			if (!c.isAsciiDigit()) {
				out.append(c)
				i++
				continue
			}
			var j = i
			while (j < n && text[j].isAsciiDigit()) j++
			// Rule 2: a number written with thousands separators is the
			// number, whole, however long it is.
			val separated = scanSeparators(text, j)
			if (separated > 0) {
				out.append(text, i, j + separated)
				i = j + separated
				continue
			}
			// Rule 3: a decimal number is a quantity, whole: both the
			// fraction after the point and the integer part before it.
			if (isFraction(text, i) || isDecimalInteger(text, j)) {
				out.append(text, i, j)
				i = j
				continue
			}
			changed = groupRun(text, i, j, settled, out) or changed
			i = j
		}
		return if (changed) out.toString() else text
	}

	/** Whether [text] holds anything this pass would change: a run of
	 *  [LONG_FROM] digits, or a plus in front of a digit. One scan, no
	 *  allocation. */
	private fun hasWork(text: String): Boolean {
		var run = 0
		val n = text.length
		var i = 0
		while (i < n) {
			val c = text[i]
			if (c.isAsciiDigit()) {
				run++
				if (run >= LONG_FROM) return true
			} else {
				run = 0
				if (c == '+' && i + 1 < n && text[i + 1].isAsciiDigit()) return true
			}
			i++
		}
		return false
	}

	/**
	 * How many characters past a digit run belong to a thousands grouping:
	 * separators each followed by exactly three digits, with no fourth
	 * digit after the last group and no second kind of separator in the
	 * middle. The separator is a comma, or a full stop where the whole
	 * number is written the continental way ("10.000.000"); a single
	 * point followed by three digits is a decimal and not a grouping, which
	 * is why a point needs a second group before it counts. Anything else
	 * answers nought.
	 */
	private fun scanSeparators(text: String, from: Int): Int {
		val n = text.length
		if (from >= n) return 0
		val separator = text[from]
		if (separator != ',' && separator != '.') return 0
		var j = from
		var groups = 0
		while (j < n && text[j] == separator) {
			if (j + 3 >= n) break
			if (!text[j + 1].isAsciiDigit() || !text[j + 2].isAsciiDigit() || !text[j + 3].isAsciiDigit()) break
			// Exactly three: a fourth digit means the separator was inside
			// a longer run rather than between groups.
			if (j + 4 < n && text[j + 4].isAsciiDigit()) return 0
			groups++
			j += 4
		}
		if (groups == 0) return 0
		// A grouping ends where the digits do. A comma grouping followed by a
		// decimal point is "1,234.5", which is still the number whole; the
		// fraction is copied by the next turn of the loop as a fraction.
		if (separator == '.' && groups < 2) return 0
		return j - from
	}

	/** Whether the run starting at [start] sits directly after a decimal
	 *  point that itself sits directly after a digit: the fraction of a
	 *  decimal number. */
	private fun isFraction(text: String, start: Int): Boolean {
		if (start < 2) return false
		val point = text[start - 1]
		if (point != '.' && point != ',') return false
		return text[start - 2].isAsciiDigit()
	}

	/** Whether the run ending at [end] is the integer part of a decimal:
	 *  a point or a decimal comma directly after it with a digit directly
	 *  after that. "985256.78" is a quantity, not a code. */
	private fun isDecimalInteger(text: String, end: Int): Boolean {
		if (end + 1 >= text.length) return false
		val point = text[end]
		if (point != '.' && point != ',') return false
		return text[end + 1].isAsciiDigit()
	}

	private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

	/**
	 * One digit run grouped for [mode], appended to [out]. Answers whether
	 * anything but the run itself went in, so a text with nothing to do --
	 * which is most text -- comes back as the string it was.
	 */
	private fun groupRun(text: String, start: Int, end: Int, mode: Int, out: StringBuilder): Boolean {
		val length = end - start
		// Rule 1: a short run is one number.
		if (length < LONG_FROM) {
			out.append(text, start, end)
			return false
		}
		if (mode == DIGITS) {
			for (k in start until end) {
				if (k > start) out.append(' ')
				out.append(text[k])
			}
			return true
		}
		// Rule 4: groups from the left, and the short group at the end.
		val group = groupSize(mode)
		var p = start
		var first = true
		while (p < end) {
			val size = minOf(group, end - p)
			if (!first) out.append(' ')
			first = false
			if (size > 1 && text[p] == '0') {
				// A group that opens with a zero is said a digit at a
				// time, so the zero is heard rather than swallowed.
				out.append(text[p])
				for (k in p + 1 until p + size) {
					out.append(' ').append(text[k])
				}
			} else {
				out.append(text, p, p + size)
			}
			p += size
		}
		return true
	}

	/**
	 * [digits] said one at a time, with a space between each. The shape
	 * the OTP reader and the DIGITS mode both produce, written once so the
	 * two can never disagree about it.
	 */
	fun spaced(digits: CharSequence): String {
		val out = StringBuilder(digits.length * 2)
		for (k in digits.indices) {
			if (k > 0) out.append(' ')
			out.append(digits[k])
		}
		return out.toString()
	}
}
