package com.blindtechnexus.freeeloquencetts

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * The pronunciation dictionaries people write for this engine.
 *
 * The engine has a loader of its own, and it does not take these: eciLoadDict
 * answers eciDictAccessError for every one of them, because what it reads is
 * IBM's own saved form and what people share is text. So the text is read here
 * and the engine is taught a word at a time, which is the same interface the
 * loader would have used and does not depend on agreeing about a file format.
 *
 * A line is a key, a tab, and what to say instead. What to say is either a
 * respelling ("dee oe jeigh") or a pronunciation in the engine's own alphabet
 * ("`[Ekspoz1e]"), and the engine takes both. The bytes of a file shared in
 * the wild are the engine's own code set -- an e-acute is one byte -- so they
 * are read as Latin-1. The file this app writes for the user's own words is
 * UTF-8, since what someone typed may hold characters Latin-1 has no room
 * for, and is read as such.
 */
object Dictionaries {

	/** Teaches the engine every entry in [file]. Answers how many went in.
	 *  [charset] is how the file is decoded. */
	fun load(engine: EvvEngine, volume: Int, file: File, charset: java.nio.charset.Charset = Charsets.ISO_8859_1): Int {
		var taught = 0
		var refused = 0
		try {
			file.forEachLine(charset) { line ->
				val split = line.indexOf('\t')
				if (split > 0) {
					val key = line.substring(0, split).trim()
					val say = line.substring(split + 1).trim()
					if (key.isNotEmpty() && say.isNotEmpty()) {
						if (engine.teachWord(volume, key, say) == 0) taught++ else refused++
					}
				}
			}
		} catch (unreadable: Exception) {
			Log.e(TAG, "cannot read ${file.name}", unreadable)
			return taught
		}
		if (refused > 0) Log.e(TAG, "${file.name}: $refused of ${taught + refused} entries refused")
		return taught
	}

	/** How many entries a file offers, for saying so on the settings screen
	 *  without an engine to hand. */
	fun count(file: File, charset: java.nio.charset.Charset = Charsets.ISO_8859_1): Int = try {
		file.useLines(charset) { lines -> lines.count { it.indexOf('\t') > 0 } }
	} catch (unreadable: Exception) {
		0
	}

	/**
	 * The text of a file a caller picked, decoded the way the files in the
	 * wild are. The engine's own code set is Latin-1, so that is the fallback;
	 * a file that is valid UTF-8 -- one this app exported, say -- is read as
	 * UTF-8. Neither way is the text refused.
	 * Safe for large files: caps at 10 MB.
	 */
	fun read(file: File): String {
		return try {
			if (!file.exists()) return ""
			if (file.length() > 10 * 1024 * 1024) {
				// Cap: read only first 10 MB
				val bytes = file.inputStream().use { it.readNBytes(10 * 1024 * 1024) }
				decode(bytes)
			} else {
				val bytes = file.readBytes()
				decode(bytes)
			}
		} catch (e: Exception) {
			""
		}
	}

	/** [bytes] decoded as UTF-8 when they are, and as Latin-1 -- the code set
	 *  the engine and the files in the wild use -- when they are not. Neither
	 *  way is the text refused. */
	fun decode(bytes: ByteArray): String = try {
		utf8(bytes) ?: String(bytes, Charsets.ISO_8859_1)
	} catch (e: Exception) {
		try { String(bytes, Charsets.ISO_8859_1) } catch (_: Exception) { "" }
	}

	/** Decode from InputStream safely, capped at 10 MB. */
	fun decode(input: java.io.InputStream): String {
		return try {
			val bytes = input.readNBytes(10 * 1024 * 1024)
			decode(bytes)
		} catch (e: Exception) {
			""
		}
	}

	/** [bytes] as UTF-8, or nothing when they are not UTF-8. */
	private fun utf8(bytes: ByteArray): String? = try {
		StandardCharsets.UTF_8.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT)
			.decode(ByteBuffer.wrap(bytes))
			.toString()
	} catch (broken: Exception) {
		null
	}

	private const val TAG = "freeeloquencetts"
}
