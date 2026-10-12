package com.blindtechnexus.freeeloquencetts

/**
 * Source code, said the way it is written rather than the way it looks.
 *
 * The engine reads prose. Hand it `getElementById` and it says one word with
 * the vowels in odd places; hand it `a != b` and the "!" is silent because a
 * symbol attached to a word is silent unless it carries a meaning the
 * language's own table knows about; hand it `/home/user/notes.txt` and the
 * slashes are nothing at all. A person who cannot see the code hears a noise
 * where the meaning was.
 *
 * So this runs on the way in, ahead of [TextFixes] and of [Symbols], and turns
 * the things the engine would drop into words. It runs *after* the user's own
 * dictionary and after [Abbreviations] for the same reason it runs before the
 * symbols: a dictionary entry is written the way the code is written, and
 * splitting `getElement` into two words would take the entry away from the
 * text it was written for.
 *
 * Every part of it is a switch of its own, with a master switch over the top,
 * because the parts are not equally wanted. Somebody reading a diff wants the
 * operators and the braces; somebody reading a stack trace wants the paths;
 * somebody reading Python wants the indentation and nothing else. A single
 * "code mode" that meant all seven would be a mode most people turned off.
 *
 * Each part is one pass over the text at indices, and each answers the text it
 * was given when it finds nothing to do, which is the case for prose: this
 * runs on every utterance whether or not it is code, and a screen reader
 * reading a message is not going to pay for a scan it does not want.
 */
object CodeReading {

	/** Which of the seven are in force. One value rather than seven reads,
	 *  because the pipeline asks once per utterance and not once per part. */
	class Options(
		val camelCase: Boolean,
		val snakeCase: Boolean,
		val operators: Boolean,
		val braces: Boolean,
		val indentation: Boolean,
		val paths: Boolean,
		val urls: Boolean
	) {
		/** Whether there is anything here to do at all, which is the check
		 *  that keeps prose off the whole of it. */
		val none: Boolean
			get() = !(camelCase || snakeCase || operators || braces ||
				indentation || paths || urls)
	}

	/** Nothing on, which is what a build that has never been told otherwise
	 *  runs with. */
	val OFF = Options(false, false, false, false, false, false, false)

	/** Everything on, which is what the master switch on the settings screen
	 *  turns on when it is turned on from nothing. */
	val ALL = Options(true, true, true, true, true, true, true)

	/**
	 * The operators, longest first.
	 *
	 * The order is the whole of the correctness here: a scanner that met "=="
	 * before "===" would say "double equals" and then "equals", which is two
	 * words for one operator and a meaning nobody wrote. Names are the ones a
	 * screen reader says rather than the ones a grammar book does, because
	 * "not equals" is what somebody listening for a condition wants to hear and
	 * "inequality" is not.
	 */
	private val OPERATORS = listOf(
		">>>" to "unsigned right shift",
		"..." to "dot dot dot",
		"!==" to "bang double equals",
		"===" to "triple equals",
		"<<" to "left shift",
		">>" to "right shift",
		"==" to "double equals",
		"!=" to "not equals",
		"<=" to "less than or equal to",
		">=" to "greater than or equal to",
		"++" to "plus plus",
		"--" to "minus minus",
		"+=" to "plus equals",
		"-=" to "minus equals",
		"*=" to "star equals",
		"/=" to "slash equals",
		"%=" to "percent equals",
		"&=" to "and equals",
		"|=" to "or equals",
		"^=" to "xor equals",
		"&&" to "and and",
		"||" to "or or",
		"!!" to "bang bang",
		"??" to "double question",
		"?." to "question dot",
		"->" to "arrow",
		"=>" to "fat arrow",
		"<-" to "left arrow",
		"::" to "double colon",
		".." to "dot dot",
		"=" to "equals",
		"+" to "plus",
		"-" to "minus",
		"*" to "star",
		"/" to "slash",
		"%" to "percent",
		"&" to "ampersand",
		"|" to "bar",
		"^" to "caret",
		"~" to "tilde",
		"!" to "bang",
		"?" to "question",
		"<" to "less than",
		">" to "greater than"
	)

	/** The brackets and the marks between statements. The angle brackets are
	 *  deliberately not here: in code they are comparison far more often than
	 *  they are a generic, and the operator table above already names them. */
	private val BRACES = listOf(
		'{' to "left brace",
		'}' to "right brace",
		'[' to "left bracket",
		']' to "right bracket",
		'(' to "left paren",
		')' to "right paren",
		';' to "semicolon",
		':' to "colon",
		',' to "comma",
		'.' to "dot",
		'#' to "hash",
		'@' to "at",
		'$' to "dollar",
		'`' to "backtick"
	).toMap()

	/** [text] with the parts of it [options] asks for turned into words. */
	fun apply(text: String, options: Options): String {
		if (text.isEmpty() || options.none) return text
		var out = text
		try { if (options.urls) out = urls(out) } catch (_: Exception) {}
		try { if (options.paths) out = paths(out) } catch (_: Exception) {}
		try { if (options.camelCase || options.snakeCase) out = identifiers(out, options) } catch (_: Exception) {}
		try { if (options.operators || options.braces) out = marks(out, options) } catch (_: Exception) {}
		try { if (options.indentation) out = indentation(out) } catch (_: Exception) {}
		return out
	}

	// ---- addresses -------------------------------------------------------

	/**
	 * `https://www.example.com/a/b` as "url example dot com /a/b".
	 *
	 * The scheme is a name and the dots in a host are not sentence ends, and
	 * those are the two things the engine gets wrong about an address: it
	 * spells "https" as a word and pauses at every dot as though the sentence
	 * had ended five times. What is left alone is everything after the host,
	 * which is a path and a query, and the pass below this one names the marks
	 * in it -- so the address is not turned into one long phrase here, it is
	 * turned into the two parts that want different treatment.
	 */
	private fun urls(text: String): String {
		if (text.indexOf("://") < 0) return text
		val out = StringBuilder(text.length + 32)
		var at = 0
		var changed = false
		while (at < text.length) {
			val end = schemeEnd(text, at)
			if (end < 0) {
				out.append(text[at])
				at++
				continue
			}
			changed = true
			out.append("url ")
			at = end
			// The host runs to the first mark that is not part of a name.
			val hostEnd = hostEnd(text, at)
			var host = text.substring(at, hostEnd)
			at = hostEnd
			if (host.startsWith("www.")) host = host.substring(4)
			for (c in host) {
				if (c == '.') out.append(" dot ") else out.append(c)
			}
		}
		return if (changed) settled(text, out.toString()) else text
	}

	/** Just past the "://" of a scheme starting at [at], or -1. A scheme is
	 *  letters, digits, plus, minus and dot, and has to be at the start of a
	 *  word: "a://b" in the middle of prose is not an address, and neither is
	 *  the "//" a comment starts with. */
	private fun schemeEnd(text: String, at: Int): Int {
		if (at > 0 && isIdentifierChar(text[at - 1])) return -1
		var j = at
		while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '+' ||
				text[j] == '-' || text[j] == '.')
		) j++
		if (j == at || j > at + 12) return -1
		if (j + 2 < text.length && text[j] == ':' && text[j + 1] == '/' && text[j + 2] == '/') {
			return j + 3
		}
		return -1
	}

	/** Where a host stops: the first character that cannot be in a name. */
	private fun hostEnd(text: String, from: Int): Int {
		var j = from
		while (j < text.length) {
			val c = text[j]
			if (c.isLetterOrDigit() || c == '.' || c == '-' || c == '_') j++ else break
		}
		return j
	}

	/**
	 * `/home/user/notes.txt` as "path home slash user slash notes dot txt".
	 *
	 * Two shapes are taken for a path, and both are narrow on purpose. One
	 * starts where a path has to: a slash, a tilde, a dot slash, or a Windows
	 * drive letter. The other is a relative one that carries a file name, so
	 * `src/main/App.kt` is a path and `and/or` is not -- a run of words with a
	 * slash in it is prose, and saying "slash" in the middle of a sentence
	 * because two words happened to be joined would be worse than the silence.
	 */
	private fun paths(text: String): String {
		if (text.indexOf('/') < 0 && text.indexOf('\\') < 0) return text
		val out = StringBuilder(text.length + 64)
		var at = 0
		var changed = false
		while (at < text.length) {
			val end = if (isPathStart(text, at)) tokenEnd(text, at) else -1
			if (end < 0 || !looksLikePath(text, at, end)) {
				out.append(text[at])
				at++
				continue
			}
			changed = true
			out.append("path ")
			for (j in at until end) {
				when (val c = text[j]) {
					// The separator a path opens with is part of saying that
					// it is a path, and saying "slash" for it as well would be
					// saying the same thing twice in a row.
					'/', '\\' -> if (j > at) out.append(" slash ")
					'.' -> out.append(" dot ")
					':' -> out.append(" colon ")
					'_' -> out.append(' ')
					else -> out.append(c)
				}
			}
			at = end
		}
		return if (changed) settled(text, out.toString()) else text
	}

	/** Whether [at] is somewhere a path could begin. Deliberately wide -- any
	 *  word start -- because a relative path begins with an ordinary letter and
	 *  there is no way to know it is one until the rest of it has been read.
	 *  [looksLikePath] is what decides, and it is the narrow half. */
	private fun isPathStart(text: String, at: Int): Boolean {
		if (at > 0 && !text[at - 1].isWhitespace()) return false
		val c = text[at]
		return c == '/' || c == '~' || c == '.' || c.isLetterOrDigit()
	}

	/** The end of the run of characters a path is made of. */
	private fun tokenEnd(text: String, from: Int): Int {
		var j = from
		while (j < text.length) {
			val c = text[j]
			if (c.isLetterOrDigit() || c == '/' || c == '\\' || c == '.' ||
				c == '_' || c == '-' || c == '+' || c == '~' || c == ':'
			) j++ else break
		}
		return j
	}

	/** Whether the run [from, to) is a path rather than prose with a slash in
	 *  it. Two shapes are taken, and both are narrow on purpose: one starts
	 *  where a path has to -- a slash, a tilde, a dot slash, a drive letter --
	 *  and the other is a relative one carrying a file name, so that
	 *  `src/main/App.kt` is a path and `and/or` is not. Saying "slash" in the
	 *  middle of a sentence because two words happened to be joined would be
	 *  worse than the silence this leaves. */
	private fun looksLikePath(text: String, from: Int, to: Int): Boolean {
		if (to - from < 3) return false
		var separators = 0
		var extension = false
		for (j in from until to) {
			val c = text[j]
			if (c == '/' || c == '\\') {
				if (j > from) separators++
				continue
			}
			// A dot with a letter or a digit on both sides is an extension,
			// which is what a relative path has and a pair of joined words
			// does not.
			if (c == '.' && j > from && j + 1 < to &&
				text[j - 1].isLetterOrDigit() && text[j + 1].isLetterOrDigit()
			) extension = true
		}
		if (separators < 1) return false
		val first = text[from]
		if (first == '/' || first == '~') return true
		if (first == '.' && from + 1 < to &&
			(text[from + 1] == '/' || text[from + 1] == '\\')
		) return true
		if (first.isLetter() && from + 2 < to && text[from + 1] == ':' &&
			(text[from + 2] == '/' || text[from + 2] == '\\')
		) return true
		return extension
	}

	// ---- identifiers -----------------------------------------------------

	/**
	 * The two ways a name is written out of several words, taken apart.
	 *
	 * `getElementById` becomes "get element by id" and `my_total_count`
	 * becomes "my total count". A capital that starts a part after the first is
	 * lowered, because "get Element" is read as two words with the second one
	 * shouted, and what was written is one name of three ordinary words. The
	 * first letter of the word is left exactly as it was written, because it is
	 * not the start of a part and lowering it would take the capital off every
	 * word of prose that reached here.
	 *
	 * An acronym is left whole: in `parseHTTPResponse` the run of capitals is
	 * one word, so only the last of them starts the part after it, which is the
	 * rule that makes "parse http response" rather than "parse h t t p
	 * response". A name that is nothing but capitals -- `HTTP` on its own -- is
	 * not taken apart at all, because taking it apart would spell it.
	 */
	private fun identifiers(text: String, options: Options): String {
		val out = StringBuilder(text.length + 32)
		var changed = false
		var at = 0
		while (at < text.length) {
			val c = text[at]
			if (!c.isLetter() && c != '_') {
				out.append(c)
				at++
				continue
			}
			val end = identifierEnd(text, at)
			val word = text.substring(at, end)
			val split = splitIdentifier(word, options)
			if (split !== word) changed = true
			out.append(split)
			at = end
		}
		return if (changed) out.toString() else text
	}

	private fun identifierEnd(text: String, from: Int): Int {
		var j = from
		while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
		return j
	}

	private fun splitIdentifier(word: String, options: Options): String {
		if (word.length < 2) return word
		var body = word
		if (options.snakeCase && body.indexOf('_') >= 0) {
			body = body.replace('_', ' ').trim()
			if (body.isEmpty()) return word
		}
		if (!options.camelCase) return body
		// An identifier written with no lower case in it at all is an acronym,
		// and taking it apart would spell it. HTTP is a word to be said, not
		// four letters; parseHTTPResponse is two words and one of them is.
		if (body.none { it.isLowerCase() }) return body
		val out = StringBuilder(body.length + 8)
		for (at in body.indices) {
			val c = body[at]
			// The first letter is left exactly as it was written. It is not
			// the start of a part -- it is the start of the word -- and
			// lowering it would take the capital off every word of prose that
			// happened to reach here, which is most of them.
			if (at == 0 || !c.isUpperCase()) {
				out.append(c)
				continue
			}
			// A capital starts a new part when something other than a capital
			// is behind it, and when a capital is behind it and a lower case is
			// in front of it -- which is the last letter of an acronym standing
			// in front of the word after it. Every letter is lowered as it goes
			// out, so a part is an ordinary word whatever it was written as.
			val previous = body[at - 1]
			val next = if (at + 1 < body.length) body[at + 1] else '\u0000'
			val startsPart = (!previous.isUpperCase() && !previous.isDigit()) ||
				(previous.isUpperCase() && next.isLowerCase())
			if (startsPart) out.append(' ')
			out.append(c.lowercaseChar())
		}
		return out.toString()
	}

	// ---- the marks -------------------------------------------------------

	/**
	 * The operators and the brackets, named.
	 *
	 * Two passes rather than one because they are two switches and a person
	 * who wants one does not necessarily want the other: the operators first,
	 * because "!=" has to be met as one thing before "!" is met on its own,
	 * and then the brackets and the marks between statements.
	 */
	private fun marks(text: String, options: Options): String {
		var out = text
		if (options.operators) out = operators(out)
		if (options.braces) out = braces(out)
		return out
	}

	private fun operators(text: String): String {
		val out = StringBuilder(text.length + 64)
		var at = 0
		var changed = false
		while (at < text.length) {
			val found = operatorAt(text, at)
			if (found == null) {
				out.append(text[at])
				at++
				continue
			}
			changed = true
			out.append(' ').append(found.second).append(' ')
			at += found.first.length
		}
		return if (changed) settled(text, out.toString()) else text
	}

	private fun operatorAt(text: String, at: Int): Pair<String, String>? {
		for ((token, name) in OPERATORS) {
			if (text.startsWith(token, at)) return token to name
		}
		return null
	}

	private fun braces(text: String): String {
		var relevant = false
		for (at in text.indices) {
			if (BRACES.containsKey(text[at])) {
				relevant = true
				break
			}
		}
		if (!relevant) return text
		val out = StringBuilder(text.length + 64)
		for (at in text.indices) {
			val name = BRACES[text[at]]
			if (name == null) out.append(text[at])
			else out.append(' ').append(name).append(' ')
		}
		return settled(text, out.toString())
	}

	/**
	 * What a pass that puts spaces around the things it names owes the text it
	 * was given back.
	 *
	 * The runs of spaces inside are the pass's own and go. The space at either
	 * end is a different question: an operator at the end of a line leaves one
	 * behind, and dropping it would be tidier, but the indentation pass runs
	 * after this one and reads the whitespace at the start of a line, so a
	 * leading space that was in the text has to still be there when it does.
	 * Hence the rule is put back what was there rather than trim.
	 */
	private fun settled(source: String, built: String): String {
		var out = COLLAPSE.replace(built, " ")
		if (out.startsWith(" ") && source.isNotEmpty() && !source[0].isWhitespace()) {
			out = out.substring(1)
		}
		if (out.endsWith(" ") && source.isNotEmpty() && !source[source.length - 1].isWhitespace()) {
			out = out.dropLast(1)
		}
		return out
	}

	// ---- how far in a line the code is -----------------------------------

	/**
	 * The space in front of a line of code, said as a number.
	 *
	 * "indent 4" rather than a pause nobody can measure. Indentation is the
	 * whole of the structure in some languages and half of it in the rest, and
	 * it is the one thing about a file that a synthesiser reading prose has no
	 * reason to say anything about at all. A tab counts as one and a space
	 * counts as one, which is a count of the characters rather than a guess at
	 * how wide the author's editor made them: a file of tabs and a file of
	 * spaces say different numbers, and saying the same number for both would
	 * be inventing an answer to a question the text does not ask.
	 */
	private fun indentation(text: String): String {
		var needed = false
		for (at in text.indices) {
			val c = text[at]
			if (c != ' ' && c != '\t') continue
			// Only whitespace the first thing on a line is an indent of it.
			if (at == 0 || text[at - 1] == '\n') {
				needed = true
				break
			}
		}
		if (!needed) return text
		val out = StringBuilder(text.length + 32)
		var atLineStart = true
		var index = 0
		while (index < text.length) {
			val c = text[index]
			if (c == '\n') {
				out.append(c)
				atLineStart = true
				index++
				continue
			}
			if (!atLineStart || (c != ' ' && c != '\t')) {
				out.append(c)
				atLineStart = false
				index++
				continue
			}
			var end = index
			var count = 0
			while (end < text.length && (text[end] == ' ' || text[end] == '\t')) {
				count++
				end++
			}
			atLineStart = false
			// Whitespace at the end of the text is not an indent of anything.
			if (end < text.length) out.append("indent ").append(count).append(' ')
			index = end
		}
		return out.toString()
	}

	private fun isIdentifierChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

	/** The runs of spaces the replacements above leave behind. */
	private val COLLAPSE = Regex(" {2,}")
}
