package com.blindtechnexus.freeeloquencetts

/**
 * Emoji and emoticons, read by name or not read at all.
 *
 * The engine knows no emoji: its Western code set stops at 0xFF, and anything
 * above that is dropped to a space on the way in. What the reader hears is a
 * gap where a face or a heart was, which is information lost. The switch here
 * says what to do instead.
 *
 * On, every emoji in the table below is replaced with its name -- "😀" becomes
 * "grinning face" -- and the common typed emoticons are given names too, so
 * ":-)" is "smiley face" rather than "colon hyphen right parenthesis". The
 * names are the words this engine can already say, so nothing depends on a
 * voice knowing an emoji.
 *
 * Off, both are taken out, which is the silence the engine always gave. The
 * table is deliberately a common few rather than the whole of Unicode: a list
 * of three thousand names would be slower to read than a gap, and the ones
 * that turn up in messages are here. An emoji not in the table is left for
 * [WesternText] to drop, which is the same as off.
 */
object Emoji {

	private val NAMES = mapOf(
		// Faces.
		"😀" to "grinning face",
		"😁" to "grinning face",
		"😂" to "face with tears of joy",
		"😃" to "smiling face",
		"😄" to "smiling face",
		"😅" to "grinning face with sweat",
		"😆" to "laughing face",
		"😉" to "winking face",
		"😊" to "smiling face",
		"😋" to "savoring face",
		"😎" to "smiling face with sunglasses",
		"😍" to "smiling face with hearts",
		"😘" to "face blowing a kiss",
		"😗" to "kissing face",
		"😙" to "kissing face",
		"😚" to "kissing face",
		"🙂" to "slightly smiling face",
		"🤗" to "hugging face",
		"🤔" to "thinking face",
		"😐" to "neutral face",
		"😑" to "expressionless face",
		"😶" to "face without mouth",
		"🙄" to "face with rolling eyes",
		"😏" to "smirking face",
		"😣" to "persevering face",
		"😥" to "sad but relieved face",
		"😮" to "face with open mouth",
		"🤐" to "zipper mouth face",
		"😯" to "hushed face",
		"😪" to "sleepy face",
		"😫" to "tired face",
		"😴" to "sleeping face",
		"😌" to "relieved face",
		"😛" to "face with stuck out tongue",
		"😜" to "winking face with tongue",
		"😝" to "squinting face with tongue",
		"🤤" to "drooling face",
		"😒" to "unamused face",
		"😓" to "face with cold sweat",
		"😔" to "pensive face",
		"😕" to "confused face",
		"🙃" to "upside down face",
		"🤑" to "money mouth face",
		"😲" to "astonished face",
		"😳" to "flushed face",
		"😞" to "disappointed face",
		"😟" to "worried face",
		"😠" to "angry face",
		"😡" to "pouting face",
		"😢" to "crying face",
		"😭" to "loudly crying face",
		"😱" to "face screaming in fear",
		"😨" to "fearful face",
		"😰" to "face with open mouth and cold sweat",
		"😷" to "face with medical mask",
		"🤒" to "face with thermometer",
		"🤕" to "face with bandage",
		"🤢" to "nauseated face",
		"🤮" to "face vomiting",
		"🤧" to "sneezing face",
		"😇" to "smiling face with halo",
		"🤠" to "cowboy face",
		"🤡" to "clown face",
		"🤥" to "lying face",
		"🤫" to "shushing face",
		"🤭" to "face with hand over mouth",
		"🤯" to "exploding head",
		"🥳" to "partying face",
		"🥺" to "pleading face",
		"😈" to "smiling face with horns",
		"👿" to "angry face with horns",
		"👻" to "ghost",
		"💀" to "skull",
		"👽" to "alien",
		"🤖" to "robot",
		"😺" to "smiling cat face",
		"😸" to "grinning cat face",
		"😹" to "cat face with tears of joy",
		"😻" to "smiling cat face with hearts",
		"😼" to "cat face with wry smile",
		"😽" to "kissing cat face",
		"🙀" to "weary cat face",
		"😿" to "crying cat face",
		"😾" to "pouting cat face",
		// Hearts and gestures.
		"❤" to "red heart",
		"🧡" to "orange heart",
		"💛" to "yellow heart",
		"💚" to "green heart",
		"💙" to "blue heart",
		"💜" to "purple heart",
		"🖤" to "black heart",
		"💔" to "broken heart",
		"💕" to "two hearts",
		"💖" to "sparkling heart",
		"💗" to "growing heart",
		"💓" to "beating heart",
		"💘" to "heart with arrow",
		"💝" to "heart with ribbon",
		"💞" to "revolving hearts",
		"💟" to "heart decoration",
		"👍" to "thumbs up",
		"👎" to "thumbs down",
		"👌" to "OK hand",
		"👏" to "clapping hands",
		"🙌" to "raised hands",
		"🙏" to "folded hands",
		"👋" to "waving hand",
		"🤝" to "handshake",
		"💪" to "flexed biceps",
		"✌" to "victory hand",
		"🤞" to "crossed fingers",
		"👊" to "fist",
		"✊" to "raised fist",
		"👈" to "pointing left",
		"👉" to "pointing right",
		"👆" to "pointing up",
		"👇" to "pointing down",
		// Marks and symbols.
		"✅" to "check mark",
		"❌" to "cross mark",
		"❓" to "question mark",
		"❗" to "exclamation mark",
		"⚠" to "warning",
		"🚫" to "prohibited",
		"⭐" to "star",
		"🌟" to "glowing star",
		"✨" to "sparkles",
		"🔥" to "fire",
		"🎉" to "party popper",
		"🎁" to "wrapped gift",
		"🎂" to "birthday cake",
		"☕" to "hot beverage",
		"🍕" to "pizza",
		"⚽" to "soccer ball",
		"🏀" to "basketball",
		"🎵" to "musical note",
		"🎶" to "musical notes",
		"💯" to "hundred points",
		"👀" to "eyes",
		"👁" to "eye",
		"🆗" to "OK",
		"🔒" to "locked",
		"🔓" to "unlocked",
		"🔔" to "bell",
		"🔕" to "bell with slash",
		"🔋" to "battery",
		"🔌" to "electric plug",
		"💡" to "light bulb",
		"📱" to "mobile phone",
		"💻" to "laptop",
		"⌚" to "watch",
		"📷" to "camera",
		"✈" to "airplane",
		"🚗" to "car",
		"🚌" to "bus",
		"🚕" to "taxi",
		"🚀" to "rocket",
		"🌙" to "crescent moon",
		"☀" to "sun",
		"🌧" to "cloud with rain",
		"❄" to "snowflake",
		"🌈" to "rainbow",
		"🌍" to "earth"
	)

	/** One typed emoticon: what to look for, what to say for it, and the
	 *  character it starts with. */
	private class Pattern(val finder: Regex, val name: String, val lead: Char)

	/** The typed emoticons, longest first so ":-)" is read before ")". The
	 *  lead is the first character of the pattern, which is what lets [apply]
	 *  leave the whole set alone -- twenty-four searches over the text --
	 *  whenever no character that could begin one of them is present. */
	private val EMOTICONS = listOf(
		Pattern(Regex("(?<![A-Za-z0-9]):-\\)(?![A-Za-z0-9])"), "smiley face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):\\)(?![A-Za-z0-9])"), "smiley face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):-D(?![A-Za-z0-9])"), "big grin", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):D(?![A-Za-z0-9])"), "big grin", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]);-\\)(?![A-Za-z0-9])"), "wink", ';'),
		Pattern(Regex("(?<![A-Za-z0-9]);\\)(?![A-Za-z0-9])"), "wink", ';'),
		Pattern(Regex("(?<![A-Za-z0-9]):-\\((?![A-Za-z0-9])"), "frowning face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):\\((?![A-Za-z0-9])"), "frowning face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):-P(?![A-Za-z0-9])"), "tongue out", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):P(?![A-Za-z0-9])"), "tongue out", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):'\\((?![A-Za-z0-9])"), "crying face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):-\\|(?![A-Za-z0-9])"), "neutral face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):\\|(?![A-Za-z0-9])"), "neutral face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):-/(?![A-Za-z0-9])"), "confused face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9]):/(?![A-Za-z0-9])"), "confused face", ':'),
		Pattern(Regex("(?<![A-Za-z0-9])XD(?![A-Za-z0-9])"), "laughing", 'X'),
		Pattern(Regex("(?<![A-Za-z0-9])<3(?![A-Za-z0-9])"), "heart", '<'),
		Pattern(Regex("(?<![A-Za-z0-9])</3(?![A-Za-z0-9])"), "broken heart", '<'),
		Pattern(Regex("(?<![A-Za-z0-9])8-\\)(?![A-Za-z0-9])"), "cool", '8'),
		Pattern(Regex("(?<![A-Za-z0-9])B-\\)(?![A-Za-z0-9])"), "cool", 'B'),
		Pattern(Regex("(?<![A-Za-z0-9])O_O(?![A-Za-z0-9])"), "surprised face", 'O'),
		Pattern(Regex("(?<![A-Za-z0-9])\\^_\\^(?![A-Za-z0-9])"), "happy face", '^'),
		Pattern(Regex("(?<![A-Za-z0-9])T_T(?![A-Za-z0-9])"), "crying face", 'T'),
		Pattern(Regex("(?<![A-Za-z0-9])>_<(?![A-Za-z0-9])"), "frustrated face", '>')
	)

	/** Every character any emoticon can begin with, as a lookup rather than as
	 *  a search. */
	private val LEADS = BooleanArray(0x10000).apply {
		for (pattern in EMOTICONS) this[pattern.lead.code] = true
	}

	/** The emoji whose name is one code unit, and the ones whose name is a
	 *  surrogate pair. Two tables rather than one keyed by strings, because
	 *  making a key per character is an allocation per character -- a
	 *  paragraph of ordinary words is a few thousand objects made to be thrown
	 *  away, and that is what made this the slowest thing on the way to an
	 *  utterance. */
	private val BY_CHAR = HashMap<Char, String>()
	private val BY_PAIR = HashMap<String, String>()

	/** Whether a character is one the table names, answered from a table of
	 *  its own so that the scan over the text never has to build a key. */
	private val NAMED = BooleanArray(0x10000)

	private val SPACES = Regex(" {2,}")

	init {
		for ((key, name) in NAMES) {
			when (key.length) {
				1 -> {
					BY_CHAR[key[0]] = name
					NAMED[key[0].code] = true
				}
				2 -> BY_PAIR[key] = name
				// Nothing in the table is longer, and a key that was would
				// silently never be found, so it is said about instead.
				else -> error("an emoji name is " + key.length + " code units long")
			}
		}
	}

	/**
	 * [text] with emoji and emoticons named when [read] is on, and taken out
	 * when it is off.
	 *
	 * The text is scanned once, and the work is only done for the text that
	 * asks for it. A paragraph of ordinary words has no emoji in it, no
	 * character an emoticon could begin with, no double space and nothing to
	 * trim: it comes back as the very string that went in. What used to happen
	 * to it was a rebuilt copy, and twenty-four regular expressions run over
	 * that copy, on the way to every utterance -- which is the sort of cost
	 * that reads as "the engine is slow" and has nothing to do with the engine.
	 */
	fun apply(text: String, read: Boolean, language: Int = Eci.ENGLISH_US,
              assets: android.content.res.AssetManager? = null): String {
        val named = if (assets != null && (language == Eci.SPANISH_SPAIN || language == Eci.SPANISH_MEXICO))
            runCatching { UnicodeEmoji.apply(text, read, language, assets) }.getOrDefault(text) else text
        return applyLegacy(named, read)
    }

    private fun applyLegacy(text: String, read: Boolean): String {
		if (text.isEmpty()) return text
		var emoji = false
		var emoticon = false
		var spacing = false
		var at = 0
		while (at < text.length) {
			val c = text[at]
			if (!spacing) {
				val doubled = at > 0 && text[at - 1] == ' ' && c == ' '
				val edge = (at == 0 || at == text.length - 1) && c.isWhitespace()
				spacing = doubled || edge
			}
			if (!emoji) {
				emoji = c.isHighSurrogate() || (c.code < NAMED.size && NAMED[c.code])
			}
			if (!emoticon && c.code < LEADS.size && LEADS[c.code]) emoticon = true
			if (emoji && emoticon && spacing) break
			at++
		}
		if (!emoji && !emoticon && !spacing) return text
		val built = if (emoji) name(text, read) else collapse(text)
		if (!emoticon) return built
		var out = built
		for (pattern in EMOTICONS) {
			if (!out.contains(pattern.lead)) continue
			out = pattern.finder.replace(out, if (read) " " + pattern.name + " " else " ")
		}
		return if (out == built) built else SPACES.replace(out, " ").trim()
	}

	/** [text] with every emoji the table knows turned into its name, or into
	 *  nothing, and with runs of spaces run into one as they go. */
	private fun name(text: String, read: Boolean): String {
		val out = StringBuilder(text.length + 16)
		var at = 0
		while (at < text.length) {
			val c = text[at]
			if (c.isHighSurrogate() && at + 1 < text.length && text[at + 1].isLowSurrogate()) {
				val pair = BY_PAIR[text.substring(at, at + 2)]
				if (pair == null) {
					append(out, c)
					append(out, text[at + 1])
				} else {
					append(out, if (read) " " + pair + " " else " ")
				}
				at += 2
				continue
			}
			val known = BY_CHAR[c]
			if (known == null) append(out, c) else append(out, if (read) " " + known + " " else " ")
			at++
		}
		return out.toString().trim()
	}

	/** [text] with runs of two or more spaces down to one, and the ends
	 *  trimmed -- which is all there is to do when there is no emoji in it. */
	private fun collapse(text: String): String {
		if (!hasDoubleSpace(text)) return text.trim()
		val out = StringBuilder(text.length)
		for (c in text) {
			if (c == ' ' && out.isNotEmpty() && out[out.length - 1] == ' ') continue
			out.append(c)
		}
		return out.toString().trim()
	}

	private fun hasDoubleSpace(text: String): Boolean {
		for (at in 1 until text.length) {
			if (text[at] == ' ' && text[at - 1] == ' ') return true
		}
		return false
	}

	/** Into [out], keeping runs of spaces down to one space as they go. That
	 *  is what the whole rebuilt text used to be handed to a regular
	 *  expression for afterwards, and it costs nothing to do on the way. */
	private fun append(out: StringBuilder, c: Char) {
		if (c == ' ' && out.isNotEmpty() && out[out.length - 1] == ' ') return
		out.append(c)
	}

	private fun append(out: StringBuilder, more: String) {
		for (c in more) append(out, c)
	}
}
