package com.blindtechnexus.freeeloquencetts

/**
 * Which of the symbols in the text are spoken by name.
 *
 * The engine has no setting for this. Its own behaviour, measured rather than
 * assumed by speaking one string at a time and comparing the audio, is three
 * things: a symbol standing on its own is always named, out of the language's
 * own character-name table -- "?" alone is "question mark", byte for byte the
 * same audio as the words; a symbol attached to a word is silent when it is
 * sentence punctuation or a bracket, and named when it carries meaning --
 * "$5" is "five dollars", "a=b" is "a equals b"; and the names it uses are its
 * own, which are "asterisk", "dot", "tilda" and "exclamation point" rather
 * than the words another synthesiser might choose.
 *
 * So the levels here are made out of text changes, on the way in, which is
 * also how Speech Dispatcher's own module for this engine does it: the only
 * lever anybody has is the text.
 *
 * [SOME] is the engine left exactly as it stands, and is the default, so
 * nobody's speech changes under them when this arrives.
 *
 * [MOST] and [ALL] add the names of symbols the engine leaves silent when
 * they are attached: brackets, quotes and the like at [MOST], periods and
 * commas at [ALL]. A symbol standing on its own is never touched, because the
 * engine already names it, with its own name and in its own prosody.
 *
 * The symbols the engine already reads in place -- the dollar in "$5", the
 * percent in "50%" -- are never named here even at [ALL], because naming them
 * ourselves would replace "five dollars" with "five dollar" and lose the
 * number the symbol was part of. They are only ever taken away, at [NONE].
 *
 * [NONE] names nothing: everything is replaced with silence except the
 * punctuation the engine needs for its pauses, and the apostrophe inside a
 * word, which is a contraction rather than a symbol. A decimal point, a
 * thousands separator and a colon between digits are the engine's number
 * reading rather than a symbol, and are left alone at every level.
 *
 * [CUSTOM] is [NONE] plus a list of the caller's own: the symbols in it are
 * named wherever they are found, and nothing else is.
 *
 * The backtick is never touched, at any level. It is the engine's annotation
 * escape and text that carries one is asking for a pause or a voice change,
 * not for the word "backquote". The hyphen is left alone as well: standing on
 * its own it is the one symbol the engine has no name for, and attached it is
 * how a minus sign or a range is written.
 */
object Symbols {

	/** No symbol is spoken by name. */
	const val NONE = 0

	/** The engine's own behaviour: what it names, it keeps naming. */
	const val SOME = 1

	/** Brackets, quotes, question marks and the like are spoken as well. */
	const val MOST = 2

	/** Every symbol is spoken, including periods and commas. */
	const val ALL = 3

	/** Only the symbols on the caller's list are spoken. */
	const val CUSTOM = 4

	/** The levels, in the order the settings screen offers them. */
	val MODES = listOf(ALL, MOST, SOME, CUSTOM, NONE)

	/** A symbol's spoken name, and the least level of verbosity that names it. */
	private class Entry(val name: String, val level: Int)

	/**
	 * The symbols that are named, by the engine's own names. The [SOME] level
	 * entries are the ones the engine already reads in place, and are never
	 * named by this filter: replacing them would trade "five dollars" for
	 * "five dollar". They are in the table so that [NONE] and [CUSTOM] can
	 * take them away, and so that a [CUSTOM] list can name one of them and
	 * have it mean "keep what the engine already says".
	 */
	private val TABLE = mapOf(
		'$' to Entry("dollar", SOME),
		'%' to Entry("percent", SOME),
		'+' to Entry("plus", SOME),
		'=' to Entry("equals", SOME),
		'@' to Entry("at", SOME),
		'#' to Entry("number", SOME),
		'*' to Entry("asterisk", SOME),
		'&' to Entry("and", SOME),
		'/' to Entry("slash", SOME),
		'<' to Entry("less than", SOME),
		'>' to Entry("greater than", SOME),
		'|' to Entry("bar", SOME),
		'~' to Entry("tilda", SOME),
		'^' to Entry("caret", SOME),
		'_' to Entry("underscore", SOME),
		'\\' to Entry("backslash", SOME),
		'(' to Entry("left parenthesis", MOST),
		')' to Entry("right parenthesis", MOST),
		'[' to Entry("left bracket", MOST),
		']' to Entry("right bracket", MOST),
		'{' to Entry("left brace", MOST),
		'}' to Entry("right brace", MOST),
		'"' to Entry("quote", MOST),
		'\'' to Entry("apostrophe", MOST),
		'?' to Entry("question mark", MOST),
		'!' to Entry("exclamation point", MOST),
		'¿' to Entry("opening question mark", MOST),
		'¡' to Entry("opening exclamation point", MOST),
		';' to Entry("semicolon", MOST),
		':' to Entry("colon", MOST),
		'.' to Entry("dot", ALL),
		',' to Entry("comma", ALL),
		'€' to Entry("euro", SOME),
		'£' to Entry("pounds", SOME),
		'¥' to Entry("yen", SOME),
		'™' to Entry("trademark", SOME),
		'°' to Entry("degrees", SOME),
		'§' to Entry("section", SOME),
		'¶' to Entry("paragraph", SOME),
		'©' to Entry("copyright", SOME),
		'®' to Entry("registered", SOME),
		'±' to Entry("plus or minus", SOME),
		'×' to Entry("times", SOME),
		'÷' to Entry("divided by", SOME),
		'≤' to Entry("less than or equal to", SOME),
		'≥' to Entry("greater than or equal to", SOME),
		'≠' to Entry("not equal to", SOME)
	)

	/**
	 * The typographic characters turned into the plain ones they stand for,
	 * which is what [WesternText] does to them later anyway. Doing it here
	 * means a curly quote is silenced at [NONE] just as a straight one is,
	 * rather than sailing through because it is not in the table.
	 */
	private val PLAIN = mapOf(
		'‘' to "'", '’' to "'", '‚' to "'", '‛' to "'",
		'“' to "\"", '”' to "\"", '„' to "\"", '‟' to "\"",
		'‹' to "<", '›' to ">", '«' to "\"", '»' to "\"",
		'′' to "'", '″' to "\"", '⁄' to "/", '∕' to "/",
		'…' to "...",
		'•' to ".", '·' to "."
	)

	/** Whether a character is one either table knows, which is the whole of
	 *  what decides whether there is anything here to do. A lookup of a
	 *  character against a hash map is a boxed character and a hash; this is a
	 *  read. Long text is mostly characters nobody can name, and it used to be
	 *  walked one map lookup at a time to find that out, then rebuilt, then
	 *  handed to a regular expression to collapse the spaces that were never
	 *  added. */
	private val RELEVANT = BooleanArray(0x10000).apply {
		for (c in TABLE.keys) this[c.code] = true
		for (c in PLAIN.keys) this[c.code] = true
	}

	/** The typographic characters on their own, which is what [normalize] is
	 *  looking for and nothing else. */
	private val PLAIN_CHARS = BooleanArray(0x10000).apply {
		for (c in PLAIN.keys) this[c.code] = true
	}

	/** The marks the engine needs where they are, to leave its pauses alone. */
	private const val PROSODY = ",.;:?!¿¡"

	// Only names inserted by this filter are translated. Currency, mathematics
	// and isolated characters keep the selected engine language's own reading.
	private val NAMED = "()[]{}\"'?!;:.,¿¡"
	private val LOCAL_NAMES = mapOf(
		"es" to listOf("abre paréntesis", "cierra paréntesis", "abre corchete", "cierra corchete", "abre llave", "cierra llave", "comillas", "apóstrofo", "interrogación", "exclamación", "punto y coma", "dos puntos", "punto", "coma", "abre interrogación", "abre exclamación"),
		"de" to listOf("Klammer auf", "Klammer zu", "eckige Klammer auf", "eckige Klammer zu", "geschweifte Klammer auf", "geschweifte Klammer zu", "Anführungszeichen", "Apostroph", "Fragezeichen", "Ausrufezeichen", "Semikolon", "Doppelpunkt", "Punkt", "Komma", "umgekehrtes Fragezeichen", "umgekehrtes Ausrufezeichen"),
		"fr" to listOf("parenthèse ouvrante", "parenthèse fermante", "crochet ouvrant", "crochet fermant", "accolade ouvrante", "accolade fermante", "guillemet", "apostrophe", "point d’interrogation", "point d’exclamation", "point-virgule", "deux-points", "point", "virgule", "point d’interrogation inversé", "point d’exclamation inversé"),
		"it" to listOf("parentesi aperta", "parentesi chiusa", "parentesi quadra aperta", "parentesi quadra chiusa", "parentesi graffa aperta", "parentesi graffa chiusa", "virgolette", "apostrofo", "punto interrogativo", "punto esclamativo", "punto e virgola", "due punti", "punto", "virgola", "punto interrogativo capovolto", "punto esclamativo capovolto")
	)

	/**
	 * [text] with the symbols [mode] asks for named or silenced. [custom] is
	 * the caller's own list and is read only at [CUSTOM].
	 * In SOME mode, isolated commas are removed so comma is not spoken as word.
	 */
	fun apply(text: String, mode: Int, custom: String, language: String = "en"): String {
		if (text.isEmpty()) return text
		if (mode == SOME) {
			// SOME originally left text to engine, but isolated comma would be spoken as "comma"
			// by engine. Requirement: remove comma from SOME mode, so isolated commas become silence.
			var hasIsolatedComma = false
			for (i in text.indices) {
				if (text[i] == ',' && !attached(text, i)) { hasIsolatedComma = true; break }
			}
			if (!hasIsolatedComma) return text
			val sb = StringBuilder(text.length)
			for (i in text.indices) {
				val c = text[i]
				if (c == ',' && !attached(text, i)) sb.append(' ') else sb.append(c)
			}
			return COLLAPSE.replace(sb.toString().trim(), " ")
		}
		var relevant = false
		for (at in text.indices) {
			if (RELEVANT[text[at].code]) {
				relevant = true
				break
			}
		}
		// Nothing in the text is a symbol or a typographic character, so there
		// is nothing to name, nothing to take away, and no spaces left behind
		// by either. What is said is trimmed, which is what the rebuild below
		// trims to as well.
		if (!relevant) return text.trim()
		val listed = if (mode == CUSTOM) normalize(custom).filter { TABLE.containsKey(it) }.toSet() else emptySet()
		val source = normalize(text)
		val out = StringBuilder(source.length + 32)
		for (at in source.indices) {
			val c = source[at]
			when {
				// A contraction is a word, not a symbol, and the engine reads
				// it as one. "don apostrophe t" is nobody's reading of don't.
				c == '\'' && betweenWordCharacters(source, at) -> out.append(c)

				// A mark between two digits is inside a number, and the
				// engine's number reading is the right one: 3.14 is "three
				// point one four", not "three dot fourteen".
				c in ",.:" && betweenDigits(source, at) -> out.append(c)

				else -> {
					val entry = TABLE[c]
					when {
						entry == null -> out.append(c)

						// Silenced: at NONE, and at CUSTOM for everything not
						// on the list. A mark the engine pauses at is kept
						// where it is attached to something and not inside a
						// word, since a period in "example.com" would
						// otherwise still be named by the engine's own rules.
						mode == NONE || (mode == CUSTOM && c !in listed) ->
							if (c in PROSODY && attached(source, at) &&
								!betweenWordCharacters(source, at)
							) out.append(c) else out.append(' ')

						// Named, where the engine would have left the symbol
						// silent: attached, and one of the levels the mode
						// names. The engine's own reading of its own symbols
						// is better than ours -- "$5" is "five dollars" where
						// a replacement would say "five dollar" -- so the
						// [SOME]-level entries are never named here, at any
						// mode: a mode that does not silence them leaves them
						// to the engine.
						attached(source, at) && entry.level >= MOST && names(mode, entry, c, listed) ->
							out.append(' ').append(LOCAL_NAMES[language]?.getOrNull(NAMED.indexOf(c)) ?: entry.name).append(' ')

						// Standing alone, or already read by the engine.
						else -> out.append(c)
					}
				}
			}
		}
		return COLLAPSE.replace(out.toString().trim(), " ")
	}

	/** Whether [mode] names [entry], once it is attached and at least [MOST]. */
	private fun names(mode: Int, entry: Entry, c: Char, listed: Set<Char>): Boolean = when (mode) {
		ALL -> true
		MOST -> entry.level == MOST
		CUSTOM -> c in listed
		else -> false
	}

	/** [text] with every typographic character turned into its plain one. */
	private fun normalize(text: String): String {
		var needed = false
		for (at in text.indices) {
			val c = text[at].code
			if (c < PLAIN_CHARS.size && PLAIN_CHARS[c]) {
				needed = true
				break
			}
		}
		if (!needed) return text
		val out = StringBuilder(text.length)
		for (c in text) out.append(PLAIN[c] ?: c)
		return out.toString()
	}

	/** Whether the character at [at] has something other than space beside it. */
	private fun attached(text: String, at: Int): Boolean {
		val before = at > 0 && !text[at - 1].isWhitespace()
		val after = at < text.length - 1 && !text[at + 1].isWhitespace()
		return before || after
	}

	/** Whether the character at [at] has a letter or a digit on both sides. */
	private fun betweenWordCharacters(text: String, at: Int): Boolean =
		at > 0 && at < text.length - 1 &&
			(text[at - 1].isLetterOrDigit() && text[at + 1].isLetterOrDigit())

	/** Whether the character at [at] has a digit on both sides. */
	private fun betweenDigits(text: String, at: Int): Boolean =
		at > 0 && at < text.length - 1 &&
			(text[at - 1].isDigit() && text[at + 1].isDigit())

	/** Runs of spaces, which the replacements above can leave behind. */
	private val COLLAPSE = Regex(" {2,}")
}
