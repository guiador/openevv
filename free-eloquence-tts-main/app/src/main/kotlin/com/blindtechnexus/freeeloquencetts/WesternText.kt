package com.blindtechnexus.freeeloquencetts

import java.text.Normalizer

/**
 * Android text turned into what the engine reads.
 *
 * The engine takes single bytes in the language's own code set, which for
 * every language IBM shipped is the Windows Western set. UTF-16 is not an
 * option here: the engine allows the wide code set only for Chinese, Japanese
 * and Korean, and asking for it in English gets the text read as bytes and
 * stopped at the first high byte, which is one character of speech.
 *
 * So anything outside that set has to become something inside it before the
 * engine sees it. A screen reader is handed curly quotes, dashes, emoji and
 * names in scripts Eloquence never knew, and dropping them silently is worse
 * than saying the nearest thing.
 *
 * Total: mixed-script text -- Urdu or Arabic beside English, say -- comes out
 * as the Western words it carries with the rest as single spaces, and nothing
 * thrown in ever throws out. The C1 controls (0x80 to 0x9F) are spaces too:
 * they are controls in the Western set rather than characters, and handing
 * the engine a control is asking it to misbehave.
 */
object WesternText {

	/** The typographic characters that turn up in ordinary text and have a
	 *  plain equivalent worth saying. */
	private val PLAIN = mapOf(
		'‘' to "'", '’' to "'", '‚' to "'", '‛' to "'",
		'“' to "\"", '”' to "\"", '„' to "\"", '‟' to "\"",
		'‐' to "-", '‑' to "-", '‒' to "-", '–' to "-",
		'—' to "-", '―' to "-", '−' to "-",
		'…' to "...", '•' to ".", '·' to ".",
		' ' to " ", ' ' to " ", ' ' to " ", '​' to "",
		'‌' to "", '‍' to "", '﻿' to "",
		'‹' to "<", '›' to ">", '«' to "\"", '»' to "\"",
		'′' to "'", '″' to "\"", '⁄' to "/", '∕' to "/",
		'×' to "x", '™' to " trademark ", '№' to " number ",
		'€' to " euro ", '£' to " pounds ", '¥' to " yen ",
		'≤' to " less than or equal to ", '≥' to " greater than or equal to ",
		'≠' to " not equal to ", '≈' to " about ", '∞' to " infinity ",
		'→' to " to ", '←' to " from ", '°' to " degrees ",
		// The letters French and the odd loan word need that Latin-1 has
		// no byte for: the ligatures and the capital y with a diaeresis
		// are spelled out as the two letters they stand for, and said
		// right in every one of the eight languages.
		'œ' to "oe", 'Œ' to "OE", 'Ÿ' to "Y", 'ĳ' to "ij", 'Ĳ' to "IJ", 'ẞ' to "SS"
	)

	private const val HIGHEST = 0xFF

	/** Which characters [PLAIN] has an equivalent for, so that the scan for
	 *  "is there anything to do here" is a read rather than a hash lookup per
	 *  character. */
	private val MAPPED = BooleanArray(0x10000).apply {
		for (c in PLAIN.keys) this[c.code] = true
	}

	/**
	 * The bytes to hand the engine, without a terminator: the native side adds
	 * one, and a nought anywhere inside would end the text early, so there is
	 * none in what comes back.
	 *
	 * Text that is already in the code set -- which is all of what a screen
	 * reader reads out of prose, names and settings -- goes straight from one
	 * array to the other. The general road below, which builds a second string
	 * before turning it into bytes, only runs for the text that needs
	 * something done to a character.
	 *
	 * Either way the runs of whitespace come out as one space and the ends
	 * are trimmed: a sentence in another script is dozens of dropped
	 * characters in a row, and handing the engine a hundred spaces is a long
	 * silence where a breath was meant. What is said is unchanged -- runs of
	 * spaces say nothing either way -- and what mixed script sounds like is
	 * the Western words it carries, one after another.
	 */
	fun encode(text: String): ByteArray {
		return try {
			if (text.isEmpty()) return ByteArray(0)
			if (needsNoHelp(text)) encodeDirect(text) else encodeGeneral(text)
		} catch (broken: Throwable) {
			try {
				asciiFallback(text)
			} catch (failed: Throwable) {
				ByteArray(0)
			}
		}
	}

	private fun encodeDirect(text: String): ByteArray {
		var start = 0
		var end = text.length
		while (start < end && isSpaceCode(text[start].code)) start++
		while (end > start && isSpaceCode(text[end - 1].code)) end--
		if (start >= end) return ByteArray(0)
		val direct = ByteArray(end - start)
		var o = 0
		var spaced = false
		var i = start
		while (i < end) {
			val code = text[i].code
			if (isSpaceCode(code)) {
				if (!spaced) {
					direct[o++] = ' '.code.toByte()
					spaced = true
				}
			} else {
				direct[o++] = byteOf(code)
				spaced = false
			}
			i++
		}
		// The trim above took the ends, but a text of nothing but spaces
		// collapses to one of them, which is trimmed here instead.
		if (o == 1 && direct[0] == ' '.code.toByte()) return ByteArray(0)
		return if (o == direct.size) direct else direct.copyOf(o)
	}

	private fun encodeGeneral(text: String): ByteArray {
		val flat = StringBuilder(text.length)
		var i = 0
		while (i < text.length) {
			val c = text[i]
			val plain = if (c.code < MAPPED.size && MAPPED[c.code]) PLAIN[c] else null
			when {
				plain != null -> flat.append(plain)
				c.code == 0 -> flat.append(' ')
				c.code <= HIGHEST -> flat.append(c)
				else -> flat.append(fallback(text, i))
			}
			i += if (Character.isHighSurrogate(c) && i + 1 < text.length) 2 else 1
		}
		var start = 0
		var end = flat.length
		while (start < end && isSpaceCode(flat[start].code)) start++
		while (end > start && isSpaceCode(flat[end - 1].code)) end--
		if (start >= end) return ByteArray(0)
		val out = ByteArray(end - start)
		var o = 0
		var spaced = false
		var j = start
		while (j < end) {
			val code = flat[j].code
			if (isSpaceCode(code)) {
				if (!spaced) {
					out[o++] = ' '.code.toByte()
					spaced = true
				}
			} else {
				out[o++] = byteOf(code)
				spaced = false
			}
			j++
		}
		if (o == 1 && out[0] == ' '.code.toByte()) return ByteArray(0)
		return if (o == out.size) out else out.copyOf(o)
	}

	/** One code as the byte the engine reads: itself where the code set has
	 *  room for it, and a space everywhere else, including the C1 controls,
	 *  which are controls in the Western set and not characters. */
	private fun byteOf(code: Int): Byte {
		if (code in 0x80..0x9F) return ' '.code.toByte()
		if (code in 1..HIGHEST) return code.toByte()
		return ' '.code.toByte()
	}

	private fun isSpaceCode(code: Int): Boolean =
		code == 0x20 || (code in 0x09..0x0D)

	/** Whether every character in [text] is one the code set has room for,
	 *  as itself, which is the case that wants no builder and no second
	 *  pass. */
	private fun needsNoHelp(text: String): Boolean {
		for (at in text.indices) {
			val c = text[at]
			if (c.code > HIGHEST || c.code == 0 || MAPPED[c.code]) return false
		}
		return true
	}

	/**
	 * What to say for a character the code set has no room for. An accent that
	 * decomposes gives up its base letter, which turns a name like Dvořák into
	 * one the engine can read; everything else becomes a space rather than a
	 * noise.
	 */
	private fun fallback(text: String, at: Int): String {
		return try {
			val c = text[at]
			val one = if (Character.isHighSurrogate(c) && at + 1 < text.length) {
				text.substring(at, at + 2)
			} else {
				c.toString()
			}
			val stripped = Normalizer.normalize(one, Normalizer.Form.NFD)
				.filter { it.code <= HIGHEST && !isCombining(it) }
			if (stripped.isEmpty()) " " else stripped
		} catch (broken: Throwable) {
			" "
		}
	}

	/** The last resort: letters, digits and marks as themselves, everything
	 *  else a space, runs collapsed. Only ever reached when the road above
	 *  threw, which it never should. */
	private fun asciiFallback(text: String): ByteArray {
		var start = 0
		var end = text.length
		while (start < end && isSpaceCode(text[start].code)) start++
		while (end > start && isSpaceCode(text[end - 1].code)) end--
		if (start >= end) return ByteArray(0)
		val out = ByteArray(end - start)
		var o = 0
		var spaced = false
		for (i in start until end) {
			val code = text[i].code
			if (isSpaceCode(code)) {
				if (!spaced) {
					out[o++] = ' '.code.toByte()
					spaced = true
				}
			} else if (code in 1..0x7E) {
				out[o++] = code.toByte()
				spaced = false
			} else if (!spaced) {
				out[o++] = ' '.code.toByte()
				spaced = true
			}
		}
		if (o == 1 && out[0] == ' '.code.toByte()) return ByteArray(0)
		return if (o == out.size) out else out.copyOf(o)
	}

	private fun isCombining(c: Char): Boolean = when (Character.getType(c).toByte()) {
		Character.NON_SPACING_MARK,
		Character.COMBINING_SPACING_MARK,
		Character.ENCLOSING_MARK -> true
		else -> false
	}
}
