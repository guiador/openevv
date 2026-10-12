package com.blindtechnexus.freeeloquencetts

/**
 * Text said one letter at a time, and the alphabet it is said in.
 *
 * Two modes that are really one question asked twice: how to say a letter, and
 * which of the text's words to say that way.
 *
 * The first is the alphabet. Letters on their own are the shortest sounds the
 * engine makes, and four of them -- B, D, P, T, and C, E, G, V in some
 * accents -- are close enough that a person listening at speed cannot be sure
 * which came. A phonetic alphabet exists for exactly that reason, so [NATO]
 * says "Alpha Bravo Charlie" instead of "a b c" and a letter that has to be
 * written down by whoever heard it cannot be mistaken for another.
 *
 * The second is which words. Everything, for somebody reading out a password
 * or a verification code, and only the words written in capitals, for somebody
 * reading prose who wants an acronym said as letters and the rest of the
 * sentence as words.
 *
 * This runs late, after [CapitalLetters] and before [Symbols]. After, because
 * the capital letters pass annotates a single capital standing on its own, and
 * a word spelled out is nothing but single capitals standing on their own --
 * running this first would put a pitch change in front of every letter of
 * every word. Before the symbols, because a spelled word is ordinary letters
 * with spaces between them and the symbol pass has nothing to find in it,
 * while a symbol in the text still has to be named.
 */
object Spelling {

	/** Nothing is spelled and the text is said as words. */
	const val OFF = 0

	/** Every letter of every word, as the letter. */
	const val LETTERS = 1

	/** Every letter of every word, as its phonetic name. */
	const val NATO = 2

	/** Only a word written entirely in capitals, as letters. */
	const val CAPITALS = 3

	/** Only a word written entirely in capitals, as phonetic names. */
	const val CAPITALS_NATO = 4

	/** The levels, in the order the settings screen offers them. */
	val MODES = listOf(OFF, LETTERS, NATO, CAPITALS, CAPITALS_NATO)

	/**
	 * The NATO alphabet, which is the ICAO spelling alphabet and the one
	 * aviation, the military and every help desk that reads a serial number
	 * out loud have settled on.
	 *
	 * Indexed by `c - 'a'` for a lower case letter and by `c - '0'` into
	 * [DIGITS] for a digit. Digits have no code word of their own in the
	 * alphabet, so they are said as their names, which is unambiguous enough
	 * and is what the alphabet's own users do when they read a number out.
	 *
	 * Called [ALPHABET] and not NATO because [NATO] is the mode number, and
	 * two members of one object cannot share a name.
	 */
	private val ALPHABET = arrayOf(
		"Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf",
		"Hotel", "India", "Juliett", "Kilo", "Lima", "Mike", "November",
		"Oscar", "Papa", "Quebec", "Romeo", "Sierra", "Tango", "Uniform",
		"Victor", "Whiskey", "X-ray", "Yankee", "Zulu"
	)

	private val DIGITS = arrayOf(
		"Zero", "One", "Two", "Three", "Four",
		"Five", "Six", "Seven", "Eight", "Nine"
	)

	/** The shortest run of capitals that is worth spelling. "I" and "a" are
	 *  words, and "OK" is one too; three is where a run of capitals stops
	 *  being a word anybody wrote and starts being an abbreviation. */
	private const val MIN_ACRONYM = 3

	/** [text] with the words [mode] asks for said letter by letter. */
	fun apply(text: String, mode: Int): String {
		if (mode == OFF || text.isEmpty()) return text
		val phonetic = mode == NATO || mode == CAPITALS_NATO
		val capitalsOnly = mode == CAPITALS || mode == CAPITALS_NATO
		val out = StringBuilder(text.length * 4)
		var at = 0
		var changed = false
		while (at < text.length) {
			val c = text[at]
			if (!c.isLetterOrDigit()) {
				out.append(c)
				at++
				continue
			}
			val end = wordEnd(text, at)
			val word = text.substring(at, end)
			if (capitalsOnly && !isAcronym(word)) {
				out.append(word)
			} else {
				spell(word, phonetic, out)
				changed = true
			}
			at = end
		}
		return if (changed) out.toString() else text
	}

	/** One word, letter by letter, into [out]. */
	private fun spell(word: String, phonetic: Boolean, out: StringBuilder) {
		var first = true
		for (c in word) {
			val said = nameOf(c, phonetic)
			if (!first) out.append(' ')
			out.append(said)
			first = false
		}
	}

	/** What one character is called. */
	private fun nameOf(c: Char, phonetic: Boolean): String {
		if (!phonetic) return c.toString()
		return when {
			c in 'a'..'z' -> ALPHABET[c - 'a']
			c in 'A'..'Z' -> ALPHABET[c - 'A']
			c in '0'..'9' -> DIGITS[c - '0']
			else -> c.toString()
		}
	}

	/** Whether a run of characters is a word that [CAPITALS] should spell:
	 *  long enough to be an abbreviation, and written with no lower case in
	 *  it. A word of two capitals is a word -- "OK", "TV", "US" -- and
	 *  spelling it would be saying four letters where two were written. */
	private fun isAcronym(word: String): Boolean {
		if (word.length < MIN_ACRONYM) return false
		var letters = 0
		for (c in word) {
			if (c.isLowerCase()) return false
			if (c.isLetter()) letters++
		}
		return letters >= MIN_ACRONYM
	}

	private fun wordEnd(text: String, from: Int): Int {
		var j = from
		while (j < text.length && text[j].isLetterOrDigit()) j++
		return j
	}
}
