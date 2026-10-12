package com.blindtechnexus.freeeloquencetts

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * The words the dictionary manager keeps, and the one file they live in.
 *
 * Two things are kept apart on purpose, and telling them apart is the whole
 * of what makes a dictionary of millions of words possible:
 *
 * - the file, which holds every word the user typed and every word imported
 *   from every file, however many there are, and is always read and written a
 *   line at a time so that nothing ever holds it whole; and
 * - the index, which is what speech is matched against and is bounded before
 *   it is built -- see [WordIndex.Builder] -- so that a file of a billion
 *   words costs an import, a browse and a search, and can never cost the
 *   engine.
 *
 * The index is the one that used to be the crash. It was a trie with a hash
 * map at every node, one object per character of every entry, all alive at
 * once, and a dictionary of a few hundred thousand words was millions of
 * objects assembled on the framework's synthesis thread in front of the first
 * syllable. What a phone did with that is what a phone does with any
 * allocation it cannot serve: the process died, and the voice died with it
 * until the app was opened again. Nothing here can do that now. The index has
 * a budget in characters and a ceiling on entries, both worked out from the
 * heap this device actually has ([indexBudgetChars]); past them an entry is
 * counted rather than kept, and the count is what the dictionary screen says
 * out loud. What is kept is matched by walking the utterance rather than the
 * dictionary, so a text costs what it is long and not what the dictionary is
 * large, and a dictionary that grew by a million words costs the next
 * utterance nothing at all.
 *
 * Three more things, all about a profile that has grown.
 *
 * The list carries a version, and the version is written down beside the file
 * rather than kept in memory, so a service restarted between two visits to the
 * dictionary screen agrees with the screen about what is on disk.
 *
 * The index is built on a thread of its own, started by the write rather than
 * waited for by the reader, so the rebuild is finished long before the
 * utterance that follows it. A reader that arrives mid-rebuild is answered
 * with the index before it, which is one dictionary change out of date for
 * the few hundred milliseconds the rebuild takes and never a wait.
 *
 * And the swap is one write to one volatile reference, so a reader holds
 * either the whole of the old index or the whole of the new one: there is no
 * half-built state for an utterance to trip over, which is the other thing
 * the old trie could not promise.
 */
object UserDictionary {

	private const val NAME = "words.dic"
	private const val VERSION_NAME = "words.dic.ver"
	private const val MAX_LINE_LENGTH = 8192

	/** How many entries the list on disk keeps. This is the file's own
	 *  ceiling and not the speech's: what is *used* for speech is bounded by
	 *  the index budget below, which is smaller. It is generous on purpose,
	 *  because a word left out of the file is a word the user typed and lost,
	 *  while a word left out of the index is a word that is still listed,
	 *  searchable and exported -- just not spoken. */
	const val MAX_ENTRIES = 1_000_000

	/** How many bytes of the file are read for the index. Past this the rest
	 *  of the file is left unread, because bytes are what a phone is out of
	 *  and a dictionary does not need to be read to the end to be useful. */
	private const val MAX_INDEX_BYTES = 96L * 1024 * 1024

	/** The version of the list on disk. Every write moves it on, and the
	 *  number itself is meaningless: what matters is that two readers who see
	 *  the same number are looking at the same list, and a reader who sees a
	 *  different one knows the index it holds is stale. */
	private val versionCount = AtomicInteger(0)

	@Volatile
	private var versionLoaded = false

	val version: Int get() = versionCount.get()

	/**
	 * What a reader takes away: the index, the version it was built at, and
	 * how much of the dictionary it holds.
	 *
	 * [kept] is how many entries the index matches on and [dropped] how many
	 * were left out for want of the room the heap would allow. Both are said
	 * out loud on the dictionary screen rather than kept as a private
	 * disappointment.
	 */
	class Snapshot(
		val version: Int,
		val index: WordIndex,
		val kept: Int,
		val dropped: Int
	)

	/** The index already built, and what it was built from. Volatile and
	 *  written after the index it belongs to, so a reader either sees the
	 *  pair that goes together or sees the older pair, never an index from
	 *  one list against the key of another. */
	@Volatile
	private var builtKey: String? = null

	@Volatile
	private var builtIndex: WordIndex = WordIndex.EMPTY

	@Volatile
	private var builtKept: Int = 0

	@Volatile
	private var builtDropped: Int = 0

	private val buildLock = Any()

	/** The thread the rebuild runs on. One of them, daemon, named, and shared:
	 *  a rebuild is a burst of work and not a stream of it, so a pool would be
	 *  a pool of one. */
	private val builder: ExecutorService = Executors.newSingleThreadExecutor(
		ThreadFactory { work -> Thread(work, "evv-dictionary").apply { isDaemon = true } }
	)

	/** How many characters of dictionary one index may keep on this device.
	 *
	 * Read from the heap the process was actually given rather than fixed,
	 * because a word list that is nothing on a phone with half a gigabyte of
	 * heap is the whole of it on one with a quarter: a sixty-fourth of the
	 * heap is a twelfth or so of what is free at any moment, which leaves the
	 * framework's own buffers room to exist -- and the dictionary is worth
	 * nothing if the synthesis callback cannot be given a buffer to fill. */
	fun indexBudgetChars(): Int {
		val heap = try {
			Runtime.getRuntime().maxMemory()
		} catch (_: Throwable) {
			128L * 1024 * 1024
		}
		val chars = (heap / 64).coerceAtLeast(0L)
		return chars.coerceIn(
			MIN_BUDGET_CHARS.toLong(),
			WordIndex.DEFAULT_BUDGET_CHARS.toLong() * 2L
		).toInt()
	}

	/** The smallest index worth building at all: a couple of thousand
	 *  entries, so that a dictionary of one's own words works even on the
	 *  tightest heap Android hands out. */
	private const val MIN_BUDGET_CHARS = 256 * 1024

	private fun indexEntries(): Int =
		(indexBudgetChars() / 16).coerceAtLeast(64)

	fun file(context: Context): File = File(DirectBoot.dictionaries(context), NAME)

	fun fileForWriting(context: Context): File {
		val into = DirectBoot.dictionariesReady(context)
		return File(into, NAME)
	}

	/** The file the version is kept in, beside the list it describes. */
	private fun versionFile(context: Context): File =
		File(DirectBoot.dictionaries(context), VERSION_NAME)

	/**
	 * Reads the version off the disk, once per process.
	 *
	 * Read rather than assumed, and read before the first comparison rather
	 * than at some convenient moment: a service that started at nought would
	 * see a file whose version is forty and rebuild, which is correct, while a
	 * service that started at forty from a stale guess would see a file whose
	 * version is forty-one and rebuild, which is also correct -- so the only
	 * thing the read buys is not rebuilding on a process start where nothing
	 * changed.
	 */
	private fun ensureVersion(context: Context) {
		if (versionLoaded) return
		synchronized(versionCount) {
			if (versionLoaded) return
			versionCount.set(readVersion(context))
			versionLoaded = true
		}
	}

	private fun readVersion(context: Context): Int = try {
		val f = versionFile(context)
		if (f.exists() && f.canRead()) {
			f.readText(Charsets.UTF_8).trim().toIntOrNull() ?: 0
		} else {
			0
		}
	} catch (unreadable: Exception) {
		0
	}

	/** Writes the version down and moves the counter on. */
	private fun bumpVersion(context: Context): Int {
		ensureVersion(context)
		val next = versionCount.incrementAndGet()
		try {
			versionFile(context).writeText(next.toString(), Charsets.UTF_8)
		} catch (unwritable: Exception) {
			// Left unlogged on purpose: this runs on the thread that saved a
			// word, and the word is saved whatever happened here.
		}
		return next
	}

	/**
	 * The index and the version it belongs to, ready to match with.
	 *
	 * Answers at once when nothing has moved, which is every utterance but
	 * the first after a change: one integer comparison and one string
	 * comparison against two volatiles. When something has moved the rebuild
	 * happens here, under [buildLock], so two threads asking at once build it
	 * once between them -- and it is normally not here at all, because every
	 * write asks for it in advance through [rebuildAsync] and the answer is
	 * ready before anybody speaks.
	 *
	 * The list is the main list first and then every imported file, in name
	 * order, up to what the budget allows between them all, so a library of
	 * dictionaries costs an utterance no more than the one list ever did.
	 * What says something moved is the main version together with the
	 * imported files' own key, which names every file, its size and its age.
	 */
	fun snapshot(context: Context): Snapshot {
		ensureVersion(context)
		val want = version
		val key = want.toString() + ":" + indexBudgetChars() + ":" +
			ExtraDictionaries.combinedKey(context)
		if (builtKey == key) return Snapshot(want, builtIndex, builtKept, builtDropped)
		synchronized(buildLock) {
			if (builtKey == key) return Snapshot(want, builtIndex, builtKept, builtDropped)
			val made = build(context)
			// Published in the order a reader reads them: the counts, then
			// the index, and the key last, which is what says the whole of it
			// is there.
			builtIndex = made.index
			builtKept = made.kept
			builtDropped = made.dropped
			builtKey = key
			return Snapshot(want, made.index, made.kept, made.dropped)
		}
	}

	private class Built(val index: WordIndex, val kept: Int, val dropped: Int)

	/**
	 * The index for everything on disk, streamed rather than listed.
	 *
	 * Neither file is ever held: each line is added to the builder as it is
	 * read and dropped again, so what this costs the heap is the index and
	 * nothing else, whatever the files weigh. The main list is read first
	 * because it is the user's own words, then every imported file in the
	 * order the screen shows them, and the builder stops accepting entries
	 * when its budget is full -- which is what bounds the whole of this call.
	 */
	private fun build(context: Context): Built {
		val builder = WordIndex.Builder(indexBudgetChars(), indexEntries())
		try {
			readInto(file(context), builder, MAX_INDEX_BYTES)
		} catch (broken: Exception) {
			// What was read is what is matched with.
		}
		try {
			ExtraDictionaries.indexInto(context, builder, MAX_INDEX_BYTES)
		} catch (broken: Exception) {
			// The same: a dictionary that cannot be read costs its own
			// words and never the utterance.
		}
		return Built(builder.build(), builder.size, builder.overflowed)
	}

	/**
	 * Asks for the index to be rebuilt, on the builder's thread, and answers
	 * at once.
	 *
	 * Called by every write, so that the rebuild is under way before whoever
	 * saved the word has finished reading the confirmation and is long since
	 * finished by the time anything is spoken. It is idempotent and cheap to
	 * ask twice: the second ask finds the version already built and does
	 * nothing.
	 */
	fun rebuildAsync(context: Context) {
		val app = context.applicationContext
		try {
			builder.submit(Runnable {
				try {
					snapshot(app)
				} catch (broken: Exception) {
					// A rebuild that failed leaves the last good index in
					// place, and the next utterance asks again.
				}
			})
		} catch (refused: Exception) {
			// Nothing to do but let the reader build it when it asks.
		}
	}

	/** Safe, buffered read, line by line, capped. Used by the screens, which
	 *  show a list rather than match with one -- and by the edits below only
	 *  for the small lists they are not: see [rewrite], which is what an edit
	 *  of a list of a million words goes through. */
	fun words(context: Context): List<Pair<String, String>> = entries(context, MAX_ENTRIES)

	/** The first [cap] entries of the list, for a screen that shows some of
	 *  them and searches the rest. */
	fun entries(context: Context, cap: Int): List<Pair<String, String>> {
		return try {
			readAll(file(context), cap.coerceAtLeast(0))
		} catch (e: Exception) {
			emptyList()
		}
	}

	/** How many entries the list holds, counted without keeping any of them. */
	fun count(context: Context): Int {
		var n = 0
		return try {
			val f = file(context)
			if (!f.exists() || !f.canRead()) return 0
			f.bufferedReader(Charsets.UTF_8).use { reader ->
				var line: String?
				while (reader.readLine().also { line = it } != null) {
					if (n >= MAX_ENTRIES) break
					if (parseLine(line ?: continue) != null) n++
				}
			}
			n
		} catch (unreadable: Exception) {
			n
		}
	}

	private fun readAll(f: File, cap: Int): List<Pair<String, String>> {
		val out = ArrayList<Pair<String, String>>(1024)
		if (!f.exists() || !f.canRead()) return out
		f.bufferedReader(Charsets.UTF_8).use { reader ->
			var line: String?
			while (reader.readLine().also { line = it } != null) {
				if (out.size >= cap) break
				val l = line ?: continue
				parseLine(l)?.let { out.add(it) }
			}
		}
		return out
	}

	/** [f]'s entries into [builder], stopping when the builder is full or the
	 *  bytes read pass [byteCap]. Nothing but the index is held. */
	private fun readInto(f: File, builder: WordIndex.Builder, byteCap: Long) {
		if (!f.exists() || !f.canRead()) return
		var bytes = 0L
		f.bufferedReader(Charsets.UTF_8).use { reader ->
			var line: String?
			while (reader.readLine().also { line = it } != null) {
				val l = line ?: continue
				bytes += l.length + 1
				if (bytes > byteCap || builder.roomLeft <= 0) break
				val split = l.indexOf('\t')
				if (split <= 0) continue
				val word = l.substring(0, split).trim()
				if (word.isEmpty()) continue
				val say = l.substring(split + 1).trim()
				if (say.isEmpty()) continue
				builder.add(word, say)
			}
		}
	}

	/** One line as an entry, or nothing for one that is not. Total: a line
	 *  longer than anything sane, a line with no tab and a line with an empty
	 *  side are all answered with nothing rather than by throwing. */
	private fun parseLine(line: String): Pair<String, String>? {
		if (line.isEmpty() || line.length > MAX_LINE_LENGTH) return null
		val split = line.indexOf('\t')
		if (split <= 0) return null
		val word = line.substring(0, split).trim()
		val say = line.substring(split + 1).trim()
		if (word.isEmpty() || say.isEmpty()) return null
		if (word.length > WordIndex.MAX_KEY_CHARS) return null
		if (say.length > WordIndex.MAX_SAY_CHARS) return null
		return word to say
	}

	/** Atomic write: a temporary file beside the list, renamed over it. */
	fun write(context: Context, entries: List<Pair<String, String>>): Boolean {
		return try {
			val target = fileForWriting(context)
			val tmp = File(target.parentFile, "${target.name}.tmp")
			tmp.bufferedWriter(Charsets.UTF_8).use { w ->
				var count = 0
				for ((word, say) in entries) {
					if (count++ >= MAX_ENTRIES) break
					if (word.isEmpty() || say.isEmpty()) continue
					if (word.length > WordIndex.MAX_KEY_CHARS) continue
					if (say.length > WordIndex.MAX_SAY_CHARS) continue
					// Sanitize: no newline or tab in word/say that would break format
					val cleanWord = word.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim()
					val cleanSay = say.replace("\n", " ").replace("\r", " ").trim()
					if (cleanWord.isEmpty() || cleanSay.isEmpty()) continue
					w.write(cleanWord)
					w.write('\t'.code)
					w.write(cleanSay)
					w.write('\n'.code)
				}
			}
			if (!replace(tmp, target)) return false
			bumpVersion(context)
			// The index for the list just written is built now, off this
			// thread, so that the utterance which follows finds it ready
			// rather than paying for it. See [snapshot].
			rebuildAsync(context)
			true
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * [tmp] put in the place of [target], without a moment in which the file
	 * does not exist.
	 *
	 * A rename is atomic and is the whole of this on any filesystem worth the
	 * name; the copy is for the ones where it is not, and it is done only
	 * when the rename has already refused. The list is never deleted first:
	 * doing that and then failing to rename is a dictionary that was there a
	 * second ago and is not now.
	 */
	private fun replace(tmp: File, target: File): Boolean {
		if (tmp.renameTo(target)) return true
		return try {
			tmp.inputStream().use { from ->
				target.outputStream().use { to -> from.copyTo(to) }
			}
			tmp.delete()
			true
		} catch (failed: Exception) {
			false
		}
	}

	/**
	 * [drop] asked of every line, and the answer written to a new list beside
	 * the old one rather than a new list held in the heap.
	 *
	 * This is how a word is added, taken away or changed, and it is streaming
	 * on purpose: an edit of a list of a million words used to read all of
	 * them into memory, filter them there and write them back out, which is
	 * both the slow way and the way that runs a phone out of memory while the
	 * person who typed one word waits. Here the file is read one line at a
	 * time, the lines that survive are written as they are read, and what the
	 * call costs does not depend on how long the list is.
	 *
	 * A line that is not an entry is kept exactly as it was written, because
	 * it was written by somebody and losing it to a rewrite of our own is
	 * worse than keeping a line that does nothing.
	 */
	private fun rewrite(context: Context, drop: (String) -> Boolean, add: List<Pair<String, String>>): Boolean {
		val target = fileForWriting(context)
		val tmp = File(target.parentFile, "${target.name}.tmp")
		try {
			var written = 0
			val from = file(context)
			val writer = tmp.bufferedWriter(Charsets.UTF_8)
			try {
				if (from.exists() && from.canRead()) {
					from.bufferedReader(Charsets.UTF_8).use { reader ->
						var line: String?
						while (reader.readLine().also { line = it } != null) {
							val l = line ?: continue
							val parsed = parseLine(l)
							if (parsed == null) {
								writer.write(l)
								writer.write('\n'.code)
								continue
							}
							if (drop(parsed.first)) continue
							if (written >= MAX_ENTRIES) continue
							writer.write(l)
							writer.write('\n'.code)
							written++
						}
					}
				}
				for ((word, say) in add) {
					if (written >= MAX_ENTRIES) break
					if (word.isEmpty() || say.isEmpty()) continue
					if (word.length > WordIndex.MAX_KEY_CHARS) continue
					if (say.length > WordIndex.MAX_SAY_CHARS) continue
					val cleanWord = word.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim()
					val cleanSay = say.replace("\n", " ").replace("\r", " ").trim()
					if (cleanWord.isEmpty() || cleanSay.isEmpty()) continue
					writer.write(cleanWord)
					writer.write('\t'.code)
					writer.write(cleanSay)
					writer.write('\n'.code)
					written++
				}
			} finally {
				writer.close()
			}
			if (!replace(tmp, target)) return false
		} catch (broken: Exception) {
			runCatching { tmp.delete() }
			return false
		}
		bumpVersion(context)
		rebuildAsync(context)
		return true
	}

	fun add(context: Context, word: String, say: String): Boolean {
		if (word.isBlank() || say.isBlank()) return false
		return try {
			rewrite(context, { sameWord(it, word) }, listOf(word.trim() to say.trim()))
		} catch (e: Exception) {
			false
		}
	}

	fun remove(context: Context, word: String): Boolean {
		return try {
			rewrite(context, { sameWord(it, word) }, emptyList())
		} catch (e: Exception) {
			false
		}
	}

	fun replace(context: Context, oldWord: String, word: String, say: String): Boolean {
		if (word.isBlank() || say.isBlank()) return false
		return try {
			rewrite(
				context,
				{ sameWord(it, oldWord) || sameWord(it, word) },
				listOf(word.trim() to say.trim())
			)
		} catch (e: Exception) {
			false
		}
	}

	/**
	 * [entries] brought into the list, keeping what is already there.
	 *
	 * Nothing is held but the entries handed in and one folded key per word
	 * that is already there -- and that one is capped at the index's own
	 * ceiling, because past it the duplicate costs a match and not a word: a
	 * key that is stored twice is still said the same way.
	 */
	fun merge(context: Context, entries: List<Pair<String, String>>): Int {
		if (entries.isEmpty()) return 0
		return try {
			val given = LinkedHashMap<String, Pair<String, String>>()
			for ((word, say) in entries) {
				if (given.size >= MAX_ENTRIES) break
				if (word.isEmpty() || say.isEmpty()) continue
				if (word.length > WordIndex.MAX_KEY_CHARS) continue
				if (say.length > WordIndex.MAX_SAY_CHARS) continue
				given[word.lowercase()] = word.trim() to say.trim()
			}
			if (given.isEmpty()) return 0
			val seen = HashSet<String>(Math.min(given.size, 1 shl 16))
			val ok = rewrite(
				context,
				{ key ->
					val had = given.containsKey(key)
					if (had && seen.size < MAX_ENTRIES) seen.add(key)
					had
				},
				given.values.toList()
			)
			if (!ok) 0 else (given.size - seen.size).coerceAtLeast(0)
		} catch (e: Exception) {
			0
		}
	}

	/** Parse text (used for small imports). Safe, capped. */
	fun parse(text: String): List<Pair<String, String>> {
		if (text.isEmpty()) return emptyList()
		return try {
			val out = ArrayList<Pair<String, String>>()
			for (line in text.lineSequence()) {
				if (out.size >= MAX_ENTRIES) break
				parseLine(line)?.let { out.add(it) }
			}
			out
		} catch (e: Exception) {
			emptyList()
		}
	}

	/** Parse directly from InputStream without loading whole file into memory. */
	fun parseStream(input: InputStream): List<Pair<String, String>> {
		return try {
			val out = ArrayList<Pair<String, String>>(1024)
			BufferedReader(input.reader(Charsets.UTF_8), 8192).use { reader ->
				var line: String?
				var bytes = 0L
				while (reader.readLine().also { line = it } != null) {
					if (out.size >= MAX_ENTRIES) break
					val l = line ?: continue
					bytes += l.length + 1
					if (bytes > MAX_INDEX_BYTES) break
					parseLine(l)?.let { out.add(it) }
				}
			}
			out
		} catch (e: Exception) {
			emptyList()
		}
	}

	/** [text] with the index in force, which is what the pipeline calls. */
	fun apply(text: String, index: WordIndex): String {
		if (text.isEmpty() || index.isEmpty) return text
		return try {
			index.apply(text)
		} catch (broken: Throwable) {
			text
		}
	}

	/**
	 * [text] with a list of entries applied, for a caller that has the pairs
	 * rather than the index -- the tests, and the settings screen's preview.
	 *
	 * The index for a list is kept beside it, keyed on the list's own
	 * identity, so the second call with the same list is the first call's
	 * index: an utterance is not the place to build one, and the pipeline
	 * takes an index wherever it can.
	 */
	fun apply(text: String, entries: List<Pair<String, String>>): String {
		if (text.isEmpty() || entries.isEmpty()) return text
		return try {
			val ready = lastListIndex
			if (ready != null && lastList === entries) return ready.apply(text)
			val made = WordIndex.of(entries)
			lastList = entries
			lastListIndex = made
			made.apply(text)
		} catch (e: Throwable) {
			text
		}
	}

	@Volatile
	private var lastList: List<Pair<String, String>>? = null

	@Volatile
	private var lastListIndex: WordIndex? = null

	private fun sameWord(a: String, b: String): Boolean = a.equals(b, ignoreCase = true)
	private fun foldKey(word: String): String = word.lowercase()
}
