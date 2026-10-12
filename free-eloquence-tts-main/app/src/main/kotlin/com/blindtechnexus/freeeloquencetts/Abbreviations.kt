package com.blindtechnexus.freeeloquencetts

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The abbreviations and expansions a person writes for themselves.
 *
 * The engine has an abbreviation dictionary of its own, and it is one switch
 * with no choice in it: eciDictionary on, and the engine expands whatever its
 * own table takes for an abbreviation, whether that was wanted or not. It
 * cannot be taught another entry from outside -- the volume a caller can write
 * is the main dictionary, which is a pronunciation rather than a substitution,
 * and the abbreviation volume refuses every write -- so the substitutions this
 * app can offer are its own, done on the text before the engine sees it.
 *
 * Three things about a substitution are a choice rather than a fixed answer,
 * and each of them is a thing people ask for:
 *
 * *Where it matches.* "w" is a whole word, so an entry for "st" leaves
 * "street" and "first" alone and says "street" only where "st" stands on its
 * own. "s" is the start of a word, which is what an abbreviation usually is --
 * "Dr" inside "Drive" is not an abbreviation, but "Dr" at the front of a word
 * is -- and it is the one the engine's own table behaves like. "a" is
 * anywhere, which is what a person wants for a symbol of their own.
 *
 * *Whether the capital letters matter.* Off by default, so an entry written in
 * capitals is heard however the word was typed; on, for the entries where the
 * capitals are the point, which is most of them once somebody is writing
 * acronyms.
 *
 * *What it says.* Free text, which may itself be a respelling ("dee oe
 * jeigh") or a pronunciation in the engine's own alphabet ("`[Ekspoz1e]").
 * Both go straight to the engine, which takes either.
 *
 * The list is a file beside the user dictionary, in the same directory and in
 * the same spirit: a line per entry, tabs between the columns, and a build
 * that reads what an earlier build wrote. A file with two columns rather than
 * four is an older one and is read with the defaults, so a list brought over
 * from the dictionary screen keeps working.
 *
 * This runs in [TextPipeline] after the user dictionary and before
 * [CodeReading], so a substitution is the spelling the rest of the road sees
 * and code splitting does not take an entry away from the text it was written
 * for.
 */
object Abbreviations {

	private const val NAME = "abbreviations.dic"
	private const val MAX_LINE_LENGTH = 8192
	private const val MAX_ENTRIES = 20_000
	private const val MAX_KEY_LENGTH = 200
	private const val MAX_SAY_LENGTH = 2000

	/** A whole word, and nothing that has one beside it. */
	const val WHOLE_WORD = 0

	/** The start of a word, which is where an abbreviation usually is. */
	const val STARTS_WITH = 1

	/** Anywhere at all. */
	const val ANYWHERE = 2

	/** The three, in the order the screen offers them. */
	val MATCHES = listOf(WHOLE_WORD, STARTS_WITH, ANYWHERE)

	/** How many times the list has been written in this process. What the
	 *  speech service asks before it reads the file: an unchanged list costs
	 *  one integer comparison on the way to every utterance, and a write from
	 *  the screen moves it on so that the next utterance reads the list again.
	 *  Kept in memory rather than beside the file, because the screen and the
	 *  service are in the one process and a process that has just started has
	 *  no list in hand whatever the number says. */
	private val versionCount = AtomicInteger(0)

	val version: Int get() = versionCount.get()

	/** One entry: what is written, what to say, where it matches, and whether
	 *  the capital letters have to be the ones that were typed. */
	class Entry(
		val key: String,
		val say: String,
		val match: Int = WHOLE_WORD,
		val ignoreCase: Boolean = true
	) {
		/** Whether this is the same entry as [other], which is what a screen
		 *  asks when it replaces one rather than adding a second. Same key,
		 *  same matching -- and case matters in the comparison only when the
		 *  entry says it does, since that is the only time the two are
		 *  distinguishable to the reader of the text. */
		fun sameAs(other: Entry): Boolean =
			key.equals(other.key, ignoreCase = ignoreCase || other.ignoreCase) &&
				match == other.match

		override fun toString(): String = "$key -> $say"
	}

	// ---- the file --------------------------------------------------------

	fun file(context: Context): File = File(DirectBoot.dictionaries(context), NAME)

	fun fileForWriting(context: Context): File = File(DirectBoot.dictionariesReady(context), NAME)

	/** The list on disk. An empty one when there is no file, which is the
	 *  answer for a build that has never been given an abbreviation. */
	fun entries(context: Context): List<Entry> = try {
		val f = file(context)
		if (!f.exists() || !f.canRead()) emptyList() else read(f)
	} catch (unreadable: Exception) {
		emptyList()
	}

	private fun read(f: File): List<Entry> =
		f.bufferedReader(Charsets.UTF_8).use { parseLines(it) }

	private fun parseLines(reader: BufferedReader): List<Entry> {
		val out = ArrayList<Entry>(64)
		var count = 0
		while (true) {
			val line = reader.readLine() ?: break
			if (count++ >= MAX_ENTRIES) break
			entryOf(line)?.let { out.add(it) }
		}
		return out
	}

	/** One line of the file as an entry, or nought for one that is not. */
	private fun entryOf(line: String): Entry? {
		if (line.isEmpty() || line.length > MAX_LINE_LENGTH) return null
		val first = line.indexOf('\t')
		if (first <= 0) return null
		val rest = line.substring(first + 1)
		val second = rest.indexOf('\t')
		val say = if (second < 0) rest else rest.substring(0, second)
		val tail = if (second < 0) "" else rest.substring(second + 1)
		val key = line.substring(0, first).trim()
		val said = say.trim()
		if (key.isEmpty() || said.isEmpty()) return null
		if (key.length > MAX_KEY_LENGTH || said.length > MAX_SAY_LENGTH) return null
		val columns = if (tail.isEmpty()) emptyList() else tail.split('\t')
		val match = matchOf(columns.getOrNull(0))
		val ignoreCase = columns.getOrNull(1)?.trim()?.lowercase() != "s"
		return Entry(key, said, match, ignoreCase)
	}

	private fun matchOf(column: String?): Int = when (column?.trim()?.lowercase()) {
		"s" -> STARTS_WITH
		"a" -> ANYWHERE
		else -> WHOLE_WORD
	}

	/** Writes the list, atomically: to a file beside the one it replaces and
	 *  then over the top of it, so a write that is interrupted leaves the list
	 *  that was there rather than half of a new one. */
	fun write(context: Context, entries: List<Entry>): Boolean = try {
		val target = fileForWriting(context)
		val tmp = File(target.parentFile, "${target.name}.tmp")
		tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
			var count = 0
			for (entry in entries) {
				if (count++ >= MAX_ENTRIES) break
				val key = clean(entry.key)
				val say = cleanSay(entry.say)
				if (key.isEmpty() || say.isEmpty()) continue
				writer.write(key)
				writer.write('\t'.code)
				writer.write(say)
				writer.write('\t'.code)
				writer.write(matchColumn(entry.match))
				writer.write('\t'.code)
				writer.write(if (entry.ignoreCase) "i" else "s")
				writer.write('\n'.code)
			}
		}
		if (target.exists()) target.delete()
		tmp.renameTo(target)
		versionCount.incrementAndGet()
		true
	} catch (unwritable: Exception) {
		false
	}

	/** A line of the file is one line, so a key or an expansion carrying a
	 *  newline or a tab would be two entries and neither of them right. */
	private fun clean(text: String): String =
		text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim()

	private fun cleanSay(text: String): String =
		text.replace('\n', ' ').replace('\r', ' ').trim()

	private fun matchColumn(match: Int): String = when (match) {
		STARTS_WITH -> "s"
		ANYWHERE -> "a"
		else -> "w"
	}

	fun add(context: Context, entry: Entry): Boolean {
		if (entry.key.isBlank() || entry.say.isBlank()) return false
		return try {
			val kept = entries(context).filterNot { it.sameAs(entry) }.toMutableList()
			if (kept.size >= MAX_ENTRIES) false
			else kept.add(entry).let { write(context, kept) }
		} catch (broken: Exception) {
			false
		}
	}

	fun remove(context: Context, entry: Entry): Boolean = try {
		write(context, entries(context).filterNot { it.sameAs(entry) })
	} catch (broken: Exception) {
		false
	}

	/** Replaces [was] with [entry], which is how an edit is written: the entry
	 *  that was there goes whether or not its key survived the edit, since a
	 *  key changed is a different entry and the old one is nobody's any more. */
	fun replace(context: Context, was: Entry, entry: Entry): Boolean {
		if (entry.key.isBlank() || entry.say.isBlank()) return false
		return try {
			val kept = entries(context).filterNot { it.sameAs(was) || it.sameAs(entry) }.toMutableList()
			kept.add(entry)
			write(context, kept)
		} catch (broken: Exception) {
			false
		}
	}

	/** A file somebody picked, as entries. Four columns or two; anything a
	 *  line does not carry is a default. */
	fun parse(text: String): List<Entry> {
		if (text.isEmpty()) return emptyList()
		return try {
			val out = ArrayList<Entry>()
			var count = 0
			for (line in text.lineSequence()) {
				if (count++ >= MAX_ENTRIES) break
				entryOf(line)?.let { out.add(it) }
			}
			out
		} catch (broken: Exception) {
			emptyList()
		}
	}

	/** The same, straight off a stream, for a file too big to hold. */
	fun parseStream(input: InputStream): List<Entry> = try {
		BufferedReader(input.reader(Charsets.UTF_8), 8192).use { parseLines(it) }
	} catch (broken: Exception) {
		emptyList()
	}

	/** What the list looks like as text, for the export. */
	fun format(entries: List<Entry>): String = buildString {
		for (entry in entries) {
			append(clean(entry.key))
			append('\t')
			append(cleanSay(entry.say))
			append('\t')
			append(matchColumn(entry.match))
			append('\t')
			append(if (entry.ignoreCase) "i" else "s")
			append('\n')
		}
	}

	// ---- saying it -------------------------------------------------------

	@Volatile
	private var cachedEntries: List<Entry>? = null

	@Volatile
	private var cachedMatcher: Replacer? = null

	private val cacheLock = Any()

	/**
	 * [text] with every abbreviation on the list expanded.
	 *
	 * The trie the matching runs on is built once per list rather than once
	 * per utterance, and the list is what says whether it is the same one: the
	 * pipeline is handed the same list object until the file moves, so an
	 * unchanged list costs one reference comparison.
	 */
	fun apply(text: String, entries: List<Entry>): String {
		if (text.isEmpty() || entries.isEmpty()) return text
		return try {
			val replacer = synchronized(cacheLock) {
				val held = cachedMatcher
				if (held != null && cachedEntries === entries) held
				else Replacer(entries).also {
					cachedMatcher = it
					cachedEntries = entries
				}
			}
			replacer.apply(text)
		} catch (broken: Exception) {
			text
		}
	}

	/**
	 * The list as a trie of lower case characters, with the entries that end
	 * at each node hanging off it.
	 *
	 * One structure rather than one walk per entry, because "anywhere" matching
	 * is a question asked at every position in the text and an entry at a time
	 * there is the whole text walked once per entry. The capital letters are
	 * folded on the way in and asked about again on the way out, so an entry
	 * that wants them kept can say so without the trie having to hold both
	 * spellings of everything.
	 */
	private class Replacer(entries: List<Entry>) {

		private class Node {
			val next = HashMap<Char, Node>()
			var entries: MutableList<Entry>? = null
		}

		private val root = Node()
		private val empty: Boolean

		init {
			var count = 0
			for (entry in entries) {
				if (count++ >= MAX_ENTRIES) break
				if (entry.key.isEmpty() || entry.say.isEmpty()) continue
				var node = root
				for (c in entry.key) {
					node = node.next.getOrPut(c.lowercaseChar()) { Node() }
				}
				(node.entries ?: ArrayList<Entry>(1).also { node.entries = it }).add(entry)
			}
			empty = root.next.isEmpty()
		}

		fun apply(text: String): String {
			if (empty) return text
			val length = text.length
			val out = StringBuilder(length + 32)
			var at = 0
			var changed = false
			while (at < length) {
				val found = longest(text, at)
				if (found == null) {
					out.append(text[at])
					at++
					continue
				}
				out.append(found.first.say)
				at += found.second
				changed = true
			}
			return if (changed) out.toString() else text
		}

		/**
		 * The longest entry starting at [from] whose own rule about what may
		 * stand beside it holds, and how much of the text it took.
		 *
		 * The longest rather than the first, because "st" and "str" on the same
		 * list are two entries and the one that was written longer is the one
		 * that was meant. The length comes back with it rather than being
		 * worked out from the key again by the caller, because the walk is the
		 * only place that knows where the match actually stopped.
		 */
		private fun longest(text: String, from: Int): Pair<Entry, Int>? {
			val before = from == 0 || !isWordChar(text[from - 1])
			var node = root
			var best: Entry? = null
			var bestLength = 0
			var at = from
			while (at < text.length) {
				node = node.next[text[at].lowercaseChar()] ?: break
				at++
				val here = node.entries ?: continue
				val afterWord = at >= text.length || !isWordChar(text[at])
				for (entry in here) {
					val allowed = when (entry.match) {
						ANYWHERE -> true
						STARTS_WITH -> before
						else -> before && afterWord
					}
					if (!allowed) continue
					if (!entry.ignoreCase && !matchesCase(text, from, at, entry.key)) continue
					if (at - from > bestLength) {
						best = entry
						bestLength = at - from
					}
				}
			}
			val winner = best ?: return null
			return winner to bestLength
		}

		private fun matchesCase(text: String, from: Int, to: Int, key: String): Boolean {
			if (to - from != key.length) return false
			for (offset in 0 until key.length) {
				if (text[from + offset] != key[offset]) return false
			}
			return true
		}

		private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\''
	}
}
