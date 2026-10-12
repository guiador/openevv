package com.blindtechnexus.freeeloquencetts

/**
 * One-time passcodes read a digit at a time.
 *
 * A verification code is not a number. "492801" is six digits a person has
 * to type one after another, and the engine's "four hundred ninety-two
 * thousand eight hundred and one" is an arithmetic answer to a question nobody
 * asked -- the listener has to work the digits back out of it, and gets the
 * zeros wrong. So a code is found and said as "4 9 2 8 0 1", whatever the
 * number mode is set to: this runs before [Numbers] and independently of it,
 * because a code wants spelling out even when every other number is read
 * whole.
 *
 * What makes a run of digits a code is the words around it, and the whole
 * of this file is the question of how sure to be. A run of four to eight
 * digits is a candidate; a candidate is a code when the text near it says so.
 * Each candidate is scored and the score has to reach [THRESHOLD]:
 *
 * - The words that name a code -- OTP, one-time, verification, verify,
 *   passcode, security code, login code, two-factor, PIN, code, token, and
 *   their German, Spanish, French, Italian and Polish equivalents -- count
 *   for most of it, more when they are the specific words ("verification
 *   code") than the general one ("code"), and more when they are close:
 *   within forty characters before or after the digits, less out to eighty.
 * - The phrasing of a code message counts a little: "is your", "enter",
 *   "use", a colon straight before the digits, "do not share", "expires",
 *   "valid for". The Android SMS retriever prefix "<#>" and Google's "G-"
 *   count more, because nothing else is ever written that way.
 * - Six digits counts a little, being what most codes are.
 * - A message that is nothing but the digits is a code somebody copied, and
 *   counts as such.
 *
 * And what takes a candidate away: a four-digit run that reads as a year,
 * unless "code" stands right beside it; digits that are part of a price,
 * a decimal, a percentage, a time, a date, a longer run, or a word; and the
 * words that name an identifier rather than a code -- order, invoice,
 * tracking, ticket, reference, account, amount, flight, seat, room -- close
 * in front of the digits.
 *
 * A code written in groups -- "123 456", "123-456", "12 34 56", "1234-5678"
 * -- is one candidate when the groups are the same size, and is said with
 * a breath between the groups, which is how a person would read it out.
 *
 * None of this is a regular expression. The text is scanned once for
 * candidates and the words are looked for only in the window around each,
 * so a page with no code in it costs one pass and a message with one costs a
 * few string searches.
 */
object Otp {

	/** The fewest and the most digits a code has. Four is a PIN; eight is
	 *  the longest anything sends. Anything longer is a telephone number or
	 *  an account, and anything shorter is a quantity. */
	const val MIN_DIGITS = 4
	const val MAX_DIGITS = 8

	/** What a candidate's score has to reach to be read as a code. */
	const val THRESHOLD = 3

	/** How far around the digits the words are looked for. */
	private const val NEAR = 40
	private const val FAR = 80

	/** A found code: where it sits in the text and the digits in it, with
	 *  the group breaks kept so it can be said with a breath between them. */
	class Code(val start: Int, val end: Int, val groups: List<String>) {
		val digits: String get() = groups.joinToString("")
	}

	/** The words that say "this is a code", with how much each is worth.
	 *  Lower case, because the text is lowered before they are looked for;
	 *  ASCII where the language allows, with the accented spellings beside
	 *  them where it does not. Longer entries before shorter ones that they
	 *  contain, so "verification code" is found as itself. */
	private val STRONG = arrayOf(
		"otp", "one-time", "one time", "onetime",
		"verification", "verify", "verifying", "verif",
		"passcode", "pass code", "security code", "login code", "log in code", "sign-in code",
		"sign in code", "confirmation code", "confirm code", "authentication", "auth code",
		"2fa", "two-factor", "two factor", "2-step", "two-step", "activation code",
		"access code", "secret code", "validation code", "mfa",
		// German
		"bestätigungscode", "bestaetigungscode", "sicherheitscode", "verifizierung", "einmalpasswort",
		"einmal-passwort", "einmalcode", "tan",
		// Spanish
		"código de verificación", "codigo de verificacion", "verificación", "verificacion",
		"clave de acceso", "contraseña de un solo uso",
		// French
		"code de vérification", "code de verification", "vérification", "code de sécurité",
		"code de securite", "mot de passe à usage unique",
		// Italian
		"codice di verifica", "verifica", "codice di sicurezza", "codice di accesso",
		// Polish
		"kod weryfikacyjny", "weryfikac", "kod jednorazowy", "jednorazow"
	)

	private val GENERAL = arrayOf(
		"code", "pin", "token", "código", "codigo", "clave", "codice", "kod", "passwort", "password"
	)

	/** Phrasing that goes with a code and with little else. */
	private val HINTS = arrayOf(
		"is your", "is the", "your code", "enter", "use ", "do not share", "don't share",
		"dont share", "never share", "do not disclose", "expires", "expire", "valid for",
		"valid till", "valid until", "will expire", "no comparta", "ne partagez", "nicht weiter",
		"non condividere", "nie udostępniaj", "nie udostepniaj"
	)

	/** The words that name an identifier or a quantity rather than a code,
	 *  which take a candidate away when they stand close in front of it. */
	private val NOT_A_CODE = arrayOf(
		"order", "invoice", "tracking", "ticket", "reference", "ref.", "ref ", "account", "acct",
		"amount", "total", "balance", "price", "flight", "seat", "room", "zip", "postal",
		"postcode", "pincode", "pin code ", "extension", "ext.", "page", "year", "model",
		"serial", "id:", "id ", "no.", "number", "phone", "tel", "call", "dial", "sms to",
		"send to", "text to", "bestellung", "rechnung", "pedido", "factura", "commande",
		"facture", "ordine", "fattura", "zamówienie", "zamowienie"
	)

	/** The characters that may stand between two groups of one code. */
	private fun isGroupBreak(c: Char): Boolean =
		c == ' ' || c == '-' || c == '\u2011' || c == '\u2013' || c == '.' || c == ',' || c == '\u00A0'

	private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

	/** [text] with every code in it said a digit at a time. The text as it
	 *  was when there is nothing to do, which is most text. */
	fun apply(text: String): String {
		if (text.isEmpty()) return text
		if (!hasFourDigits(text)) return text
		val found = find(text)
		if (found.isEmpty()) return text
		val out = StringBuilder(text.length + found.size * 16)
		var at = 0
		for (code in found) {
			out.append(text, at, code.start)
			for ((k, group) in code.groups.withIndex()) {
				if (k > 0) out.append(", ")
				out.append(Numbers.spaced(group))
			}
			at = code.end
		}
		out.append(text, at, text.length)
		return out.toString()
	}

	/** The codes in [text], in order and without overlap. */
	fun find(text: String): List<Code> {
		val lower = lowered(text)
		val candidates = candidates(text)
		if (candidates.isEmpty()) return emptyList()
		val whole = text.trim()
		val out = ArrayList<Code>(candidates.size)
		for (candidate in candidates) {
			if (score(text, lower, candidate, whole) >= THRESHOLD) out.add(candidate)
		}
		return out
	}

	/** The score the candidate earns, for the tests and for anybody tuning
	 *  the words above. */
	fun scoreOf(text: String, candidate: Code): Int =
		score(text, lowered(text), candidate, text.trim())

	private fun hasFourDigits(text: String): Boolean {
		var run = 0
		for (c in text) {
			if (c.isAsciiDigit()) {
				if (++run >= MIN_DIGITS) return true
			} else {
				run = 0
			}
		}
		return false
	}

	/** The text in lower case, character for character, so an index into
	 *  one is an index into the other: String.lowercase can change the
	 *  length of a text with certain letters in it, and a window measured in
	 *  the original would then land somewhere else in the copy. */
	private fun lowered(text: String): String {
		val chars = CharArray(text.length)
		for (i in text.indices) chars[i] = Character.toLowerCase(text[i])
		return String(chars)
	}

	/**
	 * Every run of digits, or group of runs, that has the shape of a code:
	 * four to eight digits in all, in one run or in two or three runs of the
	 * same size with one break character between each, standing on its own
	 * rather than inside a word, a price, a decimal or a longer run.
	 */
	private fun candidates(text: String): List<Code> {
		val n = text.length
		val out = ArrayList<Code>(2)
		var i = 0
		while (i < n) {
			val c = text[i]
			if (c == '`') {
				// An engine annotation is carried past, digits and all.
				while (i < n && !text[i].isWhitespace()) i++
				continue
			}
			if (!c.isAsciiDigit()) {
				i++
				continue
			}
			var j = i
			while (j < n && text[j].isAsciiDigit()) j++
			val first = j - i
			// A longer run is an account or a telephone number, and the
			// digits inside it are never a code. Skipped whole.
			if (first > MAX_DIGITS) {
				i = j
				continue
			}
			val groups = ArrayList<String>(3)
			groups.add(text.substring(i, j))
			var end = j
			// Groups of the same size with one break between each.
			var total = first
			while (groups.size < 3 && end + 1 < n && isGroupBreak(text[end]) && text[end + 1].isAsciiDigit()) {
				var k = end + 1
				while (k < n && text[k].isAsciiDigit()) k++
				val size = k - end - 1
				if (size != first || total + size > MAX_DIGITS) break
				// A break followed by more digits than a group is a longer
				// number ("123 4567"), and a decimal comma or point is a
				// quantity; both are left for Numbers to decide.
				groups.add(text.substring(end + 1, k))
				total += size
				end = k
			}
			// A grouping that ran into another digit run was not a
			// grouping: "123 456 7890" is a telephone number.
			if (groups.size > 1 && end + 1 < n && isGroupBreak(text[end]) && text[end + 1].isAsciiDigit()) {
				i = end
				while (i < n && (text[i].isAsciiDigit() || isGroupBreak(text[i]))) i++
				continue
			}
			if (groups.size == 1 && first < MIN_DIGITS) {
				i = j
				continue
			}
			if (total in MIN_DIGITS..MAX_DIGITS && standsAlone(text, i, end)) {
				out.add(Code(i, end, groups))
			}
			i = end
		}
		return out
	}

	/** Whether the stretch [start, end) is a token of its own and not a part
	 *  of a price, a decimal, a percentage, a time, a date, a path or a word. */
	private fun standsAlone(text: String, start: Int, end: Int): Boolean {
		if (start > 0) {
			val before = text[start - 1]
			if (before.isLetterOrDigit()) return false
			if (before == '$' || before == '€' || before == '£' || before == '¥' || before == '₹' || before == '%') return false
			if (before == '/' || before == ':' || before == '@' || before == '_') return false
			if ((before == '.' || before == ',') && start > 1 && text[start - 2].isAsciiDigit()) return false
		}
		if (end < text.length) {
			val after = text[end]
			if (after.isLetterOrDigit()) return false
			if (after == '%' || after == '/' || after == '@' || after == '_') return false
			if (after == ':' && end + 1 < text.length && text[end + 1].isAsciiDigit()) return false
			if ((after == '.' || after == ',') && end + 1 < text.length && text[end + 1].isAsciiDigit()) return false
		}
		return true
	}

	private fun score(text: String, lower: String, candidate: Code, whole: String): Int {
		val n = lower.length
		val start = candidate.start
		val end = candidate.end
		val nearBefore = lower.substring(maxOf(0, start - NEAR), start)
		val farBefore = lower.substring(maxOf(0, start - FAR), maxOf(0, start - NEAR))
		val nearAfter = lower.substring(end, minOf(n, end + NEAR))
		val farAfter = lower.substring(minOf(n, end + NEAR), minOf(n, end + FAR))
		var score = 0

		// The words, at the best they are worth anywhere in the window.
		var words = 0
		for (word in STRONG) {
			if (nearBefore.contains(word) || nearAfter.contains(word)) { words = maxOf(words, 3); break }
			if (farBefore.contains(word) || farAfter.contains(word)) words = maxOf(words, 2)
		}
		if (words < 3) {
			for (word in GENERAL) {
				if (containsWord(nearBefore, word) || containsWord(nearAfter, word)) { words = maxOf(words, 2); break }
				if (containsWord(farBefore, word) || containsWord(farAfter, word)) words = maxOf(words, 1)
			}
		}
		score += words

		// The phrasing.
		val around = lower.substring(maxOf(0, start - FAR), minOf(n, end + FAR))
		var hints = 0
		for (hint in HINTS) {
			if (around.contains(hint)) hints++
			if (hints >= 2) break
		}
		score += hints
		if (nearBefore.contains("<#>") || lower.startsWith("<#>")) score += 2
		if (start >= 2 && lower[start - 1] == '-' && lower[start - 2] == 'g' && (start == 2 || !lower[start - 3].isLetterOrDigit())) score += 2
		val lead = nearBefore.trimEnd()
		if (lead.endsWith(":")) score += 1

		// The shape.
		val digits = candidate.digits
		if (digits.length == 6) score += 1
		if (candidate.groups.size > 1) score += 1
		if (whole == text.substring(start, end) || whole == digits) score += 2

		// What takes it away.
		if (digits.length == 4 && isYear(digits) && !lead.endsWith(":") && !endsWithCodeWord(lead)) score -= 2
		for (word in NOT_A_CODE) {
			if (endsNear(lead, word, 16)) { score -= 3; break }
		}
		// A list of numbers -- "1234 5678 9012" that was not one grouping,
		// scores in a table -- is not a message with a code in it.
		if (otherDigitRuns(lower, start, end) >= 3) score -= 1
		return score
	}

	private fun containsWord(window: String, word: String): Boolean {
		var from = 0
		while (true) {
			val at = window.indexOf(word, from)
			if (at < 0) return false
			val before = if (at == 0) ' ' else window[at - 1]
			val afterAt = at + word.length
			val after = if (afterAt >= window.length) ' ' else window[afterAt]
			if (!before.isLetter() && !after.isLetter()) return true
			from = at + 1
		}
	}

	private fun isYear(digits: String): Boolean {
		val value = digits.toIntOrNull() ?: return false
		return value in 1900..2099
	}

	private fun endsWithCodeWord(lead: String): Boolean {
		val tail = lead.trimEnd(':', ' ', '-', '\t')
		return tail.endsWith("code") || tail.endsWith("otp") || tail.endsWith("pin") ||
			tail.endsWith("código") || tail.endsWith("codigo") || tail.endsWith("codice") || tail.endsWith("kod")
	}

	/** Whether [word] stands within the last [within] characters of [lead]. */
	private fun endsNear(lead: String, word: String, within: Int): Boolean {
		val tail = if (lead.length > within) lead.substring(lead.length - within) else lead
		return tail.contains(word)
	}

	/** How many other runs of four or more digits sit within the far window,
	 *  which is what a table of figures looks like. */
	private fun otherDigitRuns(lower: String, start: Int, end: Int): Int {
		val from = maxOf(0, start - FAR)
		val to = minOf(lower.length, end + FAR)
		var runs = 0
		var run = 0
		var i = from
		while (i < to) {
			if (i in start until end) {
				i = end
				run = 0
				continue
			}
			if (lower[i].isAsciiDigit()) {
				run++
				if (run == MIN_DIGITS) runs++
			} else {
				run = 0
			}
			i++
		}
		return runs
	}
}
