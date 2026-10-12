package com.blindtechnexus.freeeloquencetts

import android.content.Context
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/**
 * The dictionaries a file was imported from, each kept as the file it was.
 *
 * The words typed on the dictionary screen live in the one file
 * [UserDictionary] keeps. A file brought in from elsewhere -- a .dict shared
 * online, an export from another phone -- is kept beside it under its own
 * name instead of being poured into the same list, so the list of imported
 * files says what came from where and each file can be opened, read, changed
 * and exported on its own.
 *
 * Everything here is streaming and capped, because the files people share
 * hold lakhs of words and the worst of them millions. An import never holds
 * the file in memory: it is read a line at a time, the entries that parse
 * are written straight to their own file, and what does not fit the limits
 * is counted and reported rather than kept. The speech service reads every
 * file the same way, up to the same total the main list has always had (see
 * [UserDictionary.MAX_ENTRIES]), so a library of dictionaries costs an
 * utterance no more than the one list ever did.
 *
 * Files are UTF-8, one entry per line, a word, a tab, and what to say
 * instead -- the same shape as the main list. A file in the engine's own
 * Latin-1 (which is what the files in the wild are) is decoded as Latin-1
 * on the way in; the sampling below is what tells the two apart.
 */
object ExtraDictionaries {

    /** The suffix every imported file carries on disk. */
    const val SUFFIX = ".dict"

    /** How many imported files are kept. Past this an import is refused
     *  rather than silently dropped, and the screen says so. */
    const val MAX_FILES = 50

    /** How many entries one imported file keeps. What does not fit is
     *  counted and reported, and the file is marked truncated.
     *
     *  A million entries is around twenty megabytes of file and is what
     *  [MAX_INPUT_BYTES] holds in the worst case anyway; the two are caps on
     *  what is *kept*, and neither is a cap on what the speech is matched
     *  against. That one is the index's own budget -- see
     *  [UserDictionary.indexBudgetChars] -- and it is the smaller of the
     *  three, so a file that fills this file up still leaves the engine
     *  exactly the memory it had. */
    const val MAX_ENTRIES_PER_FILE = 1_000_000

    /** How many bytes of an import are read before the rest is left
     *  unread. Past this the import is truncated whatever the entry count
     *  says, because bytes are what the phone is out of -- and the bytes are
     *  counted as they are written rather than as they are read, so this is
     *  the size of the file that can result and not an estimate of it. */
    const val MAX_INPUT_BYTES = 192L * 1024 * 1024

    /** How much room has to be left on the phone before an import is
     *  started. An import that fills the storage it is written to takes the
     *  app's settings, its dictionaries and anything else the user had with
     *  it, which is a worse answer than refusing the import. */
    private const val KEEP_FREE_BYTES = 24L * 1024 * 1024

    private const val MAX_LINE_LENGTH = 8192
    /** The longest key and replacement a stored entry may have. The same
     *  two numbers the index matches on, because an entry the index will not
     *  take is an entry that would sit in the file and never be said. */
    private const val MAX_WORD_LENGTH = WordIndex.MAX_KEY_CHARS
    private const val MAX_SAY_LENGTH = WordIndex.MAX_SAY_CHARS
    private const val MAX_NAME_LENGTH = 80

    /** How many times the files have been written in this process. */
    private val versionCount = AtomicInteger(0)

    val version: Int get() = versionCount.get()

    /** One imported file: what the screen calls it, and where it lives. */
    class Info(val name: String, val file: File, val bytes: Long, val modified: Long)

    /** What an import did: the file it wrote, how many entries went in, how
     *  many the import offered, and whether anything was left out. */
    class Result(
        val name: String,
        val kept: Int,
        val total: Int,
        val truncated: Boolean,
        val bytes: Long
    )

    fun dir(context: Context): File = DirectBoot.dictionaries(context)

    fun readyDir(context: Context): File = DirectBoot.dictionariesReady(context)

    /** Every imported file, by name. */
    fun list(context: Context): List<Info> {
        return try {
            val d = dir(context)
            if (!d.isDirectory) return emptyList()
            val files = d.listFiles() ?: return emptyList()
            files.filter { it.isFile && it.name.endsWith(SUFFIX, ignoreCase = true) && it.canRead() }
                .sortedBy { it.name.lowercase() }
                .take(MAX_FILES)
                .map { Info(displayName(it.name), it, it.length(), it.lastModified()) }
        } catch (broken: Exception) {
            emptyList()
        }
    }

    /** What the screen calls [fileName]: the name without the suffix. */
    fun displayName(fileName: String): String =
        if (fileName.endsWith(SUFFIX, ignoreCase = true)) fileName.dropLast(SUFFIX.length)
        else fileName

    /** The file [name] lives in, or nothing when there is no such file.
     *  The name is sanitized back into a file name, so a name typed by hand
     *  can never walk out of the directory. */
    fun fileFor(context: Context, name: String): File? {
        return try {
            val clean = sanitize(name)
            if (clean.isEmpty()) return null
            val f = File(dir(context), "$clean$SUFFIX")
            if (f.isFile && f.canRead()) f else null
        } catch (broken: Exception) {
            null
        }
    }

    /** What says whether the files moved since they were last read: every
     *  file's name, size and age, and the version writes bump. Memoized for
     *  two seconds, because the speech service asks on every utterance and a
     *  directory listing per utterance is work for nothing; every write
     *  clears the memo through [noteChanged], so a change is never missed. */
    @Volatile
    private var keyCache: String? = null

    @Volatile
    private var keyAt: Long = 0L

    fun combinedKey(context: Context): String {
        return try {
            val now = System.currentTimeMillis()
            val cached = keyCache
            if (cached != null && now - keyAt < 2000L) return cached
            val key = list(context).joinToString("|") { "${it.file.name}:${it.bytes}:${it.modified}" } +
                "|v$version"
            keyCache = key
            keyAt = now
            key
        } catch (broken: Exception) {
            "e$version"
        }
    }

    private fun noteChanged() {
        versionCount.incrementAndGet()
        keyAt = 0L
    }

    /**
     * Every imported file offered to the index being built, in the order the
     * screen lists them, and none of them ever held.
     *
     * This is what the speech is matched against and the whole of why a
     * dictionary of millions of words is possible: the files are read a line
     * at a time, each line is offered to the builder and then dropped, and
     * the reading stops the moment the builder has no room left. What is
     * held at the end is the index and nothing else, so a file of a billion
     * words costs the same as a file of a thousand -- and a file that cannot
     * be read costs its own words and never the utterance.
     */
    fun indexInto(context: Context, builder: WordIndex.Builder, byteCap: Long) {
        try {
            for (info in list(context)) {
                if (builder.roomLeft <= 0) return
                var bytes = 0L
                BufferedReader(InputStreamReader(FileInputStream(info.file), Charsets.UTF_8), 8192).use { reader ->
                    while (builder.roomLeft > 0) {
                        val line = reader.readLine() ?: break
                        bytes += line.length + 1
                        if (bytes > byteCap) break
                        val parsed = parseLine(line) ?: continue
                        builder.add(parsed.first, parsed.second)
                    }
                }
            }
        } catch (broken: Exception) {
            // What was indexed is what is matched with.
        }
    }

    /** Every imported file's entries, up to [cap] of them, main list first
     *  being the caller's business. Streaming and capped: files past the cap
     *  are not even opened. Kept for callers that want the pairs themselves
     *  -- the speech service wants [indexInto], which holds none of them. */
    fun allEntries(context: Context, cap: Int): List<Pair<String, String>> {
        if (cap <= 0) return emptyList()
        val out = ArrayList<Pair<String, String>>(1024)
        try {
            for (info in list(context)) {
                readInto(info.file, out, cap)
                if (out.size >= cap) break
            }
        } catch (broken: Exception) {
            // What parsed is what is spoken.
        }
        return out
    }

    /** The entries of the one file [name], up to [cap] of them. */
    fun entries(context: Context, name: String, cap: Int = MAX_ENTRIES_PER_FILE): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(1024)
        try {
            val f = fileFor(context, name) ?: return emptyList()
            readInto(f, out, cap.coerceAtMost(MAX_ENTRIES_PER_FILE))
        } catch (broken: Exception) {
            // What parsed is what is shown.
        }
        return out
    }

    /** The first [cap] entries of [name] whose word or saying holds
     *  [query], whatever capital letters any of them are written in.
     *  Streaming: the file is read a line at a time and only the matches
     *  are kept, so searching a file of lakhs of words costs the matches
     *  and not the file. */
    fun search(context: Context, name: String, query: String, cap: Int = 200): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(64.coerceAtMost(cap.coerceAtLeast(0)))
        if (query.isBlank() || cap <= 0) return out
        try {
            val f = fileFor(context, name) ?: return out
            val want = query.trim().lowercase()
            BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8), 8192).use { reader ->
                while (out.size < cap) {
                    val line = reader.readLine() ?: break
                    val parsed = parseLine(line) ?: continue
                    if (parsed.first.lowercase().contains(want) || parsed.second.lowercase().contains(want)) {
                        out.add(parsed)
                    }
                }
            }
        } catch (broken: Exception) {
            // What matched is what is shown.
        }
        return out
    }

    /** How many entries [info] holds. Streaming: the pairs are validated
     *  but never kept, so even the largest file answers in well under a
     *  second. */
    fun count(info: Info): Int {
        var n = 0
        try {
            BufferedReader(InputStreamReader(FileInputStream(info.file), Charsets.UTF_8), 8192).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (parseLine(line) != null) n++
                }
            }
        } catch (broken: Exception) {
            // What counted is what is said.
        }
        return n
    }

    /** [f]'s entries appended to [out], stopping at [cap]. */
    private fun readInto(f: File, out: MutableList<Pair<String, String>>, cap: Int) {
        if (out.size >= cap) return
        try {
            BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8), 8192).use { reader ->
                while (out.size < cap) {
                    val line = reader.readLine() ?: break
                    parseLine(line)?.let { out.add(it) }
                }
            }
        } catch (broken: Exception) {
            // What parsed is what is kept.
        }
    }

    /** One line as an entry, or nothing for one that is not. */
    private fun parseLine(line: String): Pair<String, String>? {
        if (line.isEmpty() || line.length > MAX_LINE_LENGTH) return null
        val split = line.indexOf('\t')
        if (split <= 0) return null
        val word = line.substring(0, split).trim()
        val say = line.substring(split + 1).trim()
        if (word.isEmpty() || say.isEmpty()) return null
        if (word.length > MAX_WORD_LENGTH || say.length > MAX_SAY_LENGTH) return null
        return word to say
    }

    /**
     * Brings [raw] in as the imported file [displayName].
     *
     * Streaming: the import is read a line at a time and written straight to
     * its own file, so a file of millions of lines costs a buffer and not
     * the file. [progress] hears every few thousand entries kept, and
     * [isCancelled] is asked on every line, so the screen can show the work
     * and stop it. Answers what the import did, or nothing when it was
     * cancelled, refused, or held nothing at all.
     */
    fun importStream(
        context: Context,
        raw: InputStream,
        displayName: String,
        progress: ((total: Int, kept: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
        cap: Int = MAX_ENTRIES_PER_FILE
    ): Result? {
        return try {
            val into = readyDir(context)
            if (list(context).size >= MAX_FILES) return null
            val room = roomForImport(into)
            if (room <= 0L) return null
            val target = uniqueFile(into, sanitize(displayName))
                ?: return null
            val tmp = File(into, "${target.name}.tmp")
            val buffered = BufferedInputStream(raw, 32768)
            val charset = detect(buffered)
            val written = ByteCounter(FileOutputStream(tmp))
            var kept = 0
            var total = 0
            var truncated = false
            var failed = false
            try {
                BufferedReader(InputStreamReader(buffered, charset), 8192).use { reader ->
                    BufferedWriter(OutputStreamWriter(written, Charsets.UTF_8), 8192).use { writer ->
                        while (true) {
                            val l = reader.readLine() ?: break
                            if (isCancelled?.invoke() == true) {
                                failed = true
                                break
                            }
                            if (written.count > room) {
                                truncated = true
                                break
                            }
                            val parsed = parseLine(l) ?: continue
                            total++
                            if (kept >= cap) {
                                truncated = true
                                continue
                            }
                            writer.write(parsed.first)
                            writer.write("\t")
                            writer.write(parsed.second)
                            writer.write("\n")
                            kept++
                            if (progress != null && total % 5000 == 0) {
                                try {
                                    progress(total, kept)
                                } catch (broken: Exception) {
                                    // Progress is a courtesy, not a contract.
                                }
                            }
                        }
                    }
                }
            } catch (broken: Exception) {
                failed = true
            }
            if (failed) {
                try {
                    tmp.delete()
                } catch (broken: Exception) {
                }
                return null
            }
            if (kept == 0) {
                try {
                    tmp.delete()
                } catch (broken: Exception) {
                }
                return null
            }
            if (!put(tmp, target)) return null
            noteChanged()
            UserDictionary.rebuildAsync(context)
            Result(displayName(target.name), kept, total, truncated, target.length())
        } catch (broken: Exception) {
            null
        }
    }

    /**
     * Brings [raw] into the file [name], after the entries already there.
     * The file keeps [MAX_ENTRIES_PER_FILE] at most; what does not fit is
     * counted and reported like any other import.
     */
    fun appendImport(
        context: Context,
        name: String,
        raw: InputStream,
        progress: ((total: Int, kept: Int) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null,
        cap: Int = MAX_ENTRIES_PER_FILE
    ): Result? {
        return try {
            val f = fileFor(context, name) ?: return null
            var room = cap - count(Info(name, f, f.length(), f.lastModified()))
            if (room <= 0) return Result(name, 0, 0, true, f.length())
            val buffered = BufferedInputStream(raw, 32768)
            val charset = detect(buffered)
            val tmp = File(f.parentFile, "${f.name}.tmp")
            val written = ByteCounter(FileOutputStream(tmp))
            val byteRoom = roomForImport(readyDir(context))
            var kept = 0
            var total = 0
            var truncated = false
            var failed = false
            try {
                // What is already there is copied in first, line by line, so
                // that the import is written into a file of its own and put
                // in place in one move: an import that is cancelled, or that
                // fills the storage it is written to, leaves the dictionary
                // it was adding to exactly as it was rather than with half
                // of a line at the end of it.
                BufferedReader(InputStreamReader(FileInputStream(f), Charsets.UTF_8), 8192).use { reader ->
                    BufferedWriter(OutputStreamWriter(written, Charsets.UTF_8), 8192).use { writer ->
                        while (true) {
                            val l = reader.readLine() ?: break
                            writer.write(l)
                            writer.write('\n'.code)
                        }
                    }
                }
                // The bytes of the import are counted apart from the bytes of
                // the copy and added to them, because the two phases write to
                // the file through different streams: counting only the first
                // would let a second import of a hundred gigabytes fill the
                // phone without the check ever firing.
                val appended = ByteCounter(FileOutputStream(tmp, true))
                BufferedReader(InputStreamReader(buffered, charset), 8192).use { reader ->
                    BufferedWriter(OutputStreamWriter(appended, Charsets.UTF_8), 8192).use { writer ->
                        while (true) {
                            val l = reader.readLine() ?: break
                            if (isCancelled?.invoke() == true) {
                                failed = true
                                break
                            }
                            if (written.count + appended.count > byteRoom) {
                                truncated = true
                                break
                            }
                            val parsed = parseLine(l) ?: continue
                            total++
                            if (room <= 0) {
                                truncated = true
                                continue
                            }
                            writer.write(parsed.first)
                            writer.write("\t")
                            writer.write(parsed.second)
                            writer.write("\n")
                            kept++
                            room--
                            if (progress != null && total % 5000 == 0) {
                                try {
                                    progress(total, kept)
                                } catch (broken: Exception) {
                                }
                            }
                        }
                    }
                }
            } catch (broken: Exception) {
                failed = true
            }
            if (failed) {
                runCatching { tmp.delete() }
                return null
            }
            // A truncated import is a short file and not a broken one: the
            // last line written is a whole line, because the check above
            // happens between lines rather than inside one.
            if (!put(tmp, f)) return null
            noteChanged()
            UserDictionary.rebuildAsync(context)
            Result(name, kept, total, truncated, f.length())
        } catch (broken: Exception) {
            null
        }
    }

    /** Writes [entries] over the file [name], atomically. */
    fun write(context: Context, name: String, entries: List<Pair<String, String>>): Boolean {
        return try {
            val clean = sanitize(name)
            if (clean.isEmpty()) return false
            val target = File(readyDir(context), "$clean$SUFFIX")
            val tmp = File(target.parentFile, "${target.name}.tmp")
            BufferedWriter(OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8), 8192).use { w ->
                var n = 0
                for ((word, say) in entries) {
                    if (n++ >= MAX_ENTRIES_PER_FILE) break
                    if (word.isEmpty() || say.isEmpty()) continue
                    if (word.length > MAX_WORD_LENGTH || say.length > MAX_SAY_LENGTH) continue
                    val cleanWord = word.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim()
                    val cleanSay = say.replace("\n", " ").replace("\r", " ").trim()
                    if (cleanWord.isEmpty() || cleanSay.isEmpty()) continue
                    w.write(cleanWord)
                    w.write("\t")
                    w.write(cleanSay)
                    w.write("\n")
                }
            }
            if (!put(tmp, target)) return false
            noteChanged()
            UserDictionary.rebuildAsync(context)
            true
        } catch (broken: Exception) {
            false
        }
    }

    fun add(context: Context, name: String, word: String, say: String): Boolean {
        if (word.isBlank() || say.isBlank()) return false
        return rewrite(context, name, { it.equals(word, ignoreCase = true) }, listOf(word.trim() to say.trim()))
    }

    fun remove(context: Context, name: String, word: String): Boolean {
        return rewrite(context, name, { it.equals(word, ignoreCase = true) }, emptyList())
    }

    fun replace(context: Context, name: String, oldWord: String, word: String, say: String): Boolean {
        if (word.isBlank() || say.isBlank()) return false
        return rewrite(
            context,
            name,
            { it.equals(oldWord, ignoreCase = true) || it.equals(word, ignoreCase = true) },
            listOf(word.trim() to say.trim())
        )
    }

    /**
     * One entry changed in the file [name], streamed rather than listed.
     *
     * The change used to be read, filtered and written back, which is a list
     * of a million words in memory to change one of them. Here the file is
     * read a line at a time into a file beside it, [drop] is asked of each
     * word as it goes past, and the new entries are written at the end; the
     * result then takes the place of what was there in one move. A line that
     * is not an entry is kept as it was written.
     */
    private fun rewrite(
        context: Context,
        name: String,
        drop: (String) -> Boolean,
        add: List<Pair<String, String>>
    ): Boolean {
        return try {
            val target = fileFor(context, name) ?: return false
            val tmp = File(target.parentFile, "${target.name}.tmp")
            var written = 0
            val writer = BufferedWriter(OutputStreamWriter(FileOutputStream(tmp), Charsets.UTF_8), 8192)
            try {
                BufferedReader(InputStreamReader(FileInputStream(target), Charsets.UTF_8), 8192).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val parsed = parseLine(line)
                        if (parsed == null) {
                            writer.write(line)
                            writer.write('\n'.code)
                            continue
                        }
                        if (drop(parsed.first)) continue
                        if (written >= MAX_ENTRIES_PER_FILE) continue
                        writer.write(parsed.first)
                        writer.write('\t'.code)
                        writer.write(parsed.second)
                        writer.write('\n'.code)
                        written++
                    }
                }
                for ((word, say) in add) {
                    if (written >= MAX_ENTRIES_PER_FILE) break
                    val parsed = parseLine(word + "\t" + say) ?: continue
                    writer.write(parsed.first)
                    writer.write('\t'.code)
                    writer.write(parsed.second)
                    writer.write('\n'.code)
                    written++
                }
            } finally {
                writer.close()
            }
            if (!put(tmp, target)) return false
            noteChanged()
            UserDictionary.rebuildAsync(context)
            true
        } catch (broken: Exception) {
            false
        }
    }

    /** Takes the file [name] away. */
    fun delete(context: Context, name: String): Boolean {
        return try {
            val f = fileFor(context, name) ?: return false
            val gone = f.delete()
            if (gone) {
                noteChanged()
                UserDictionary.rebuildAsync(context)
            }
            gone
        } catch (broken: Exception) {
            false
        }
    }

    /** How many bytes of an import may be written before the rest of it is
     *  left unread: the smaller of the file ceiling and what the phone has
     *  free with room to spare. */
    private fun roomForImport(into: File): Long {
        val free = try {
            into.usableSpace
        } catch (broken: Exception) {
            MAX_INPUT_BYTES
        }
        return Math.min(MAX_INPUT_BYTES, (free - KEEP_FREE_BYTES).coerceAtLeast(0L))
    }

    /** [tmp] put in the place of [target] in one move, with no moment in
     *  which the dictionary does not exist: a rename, and a copy only for a
     *  filesystem that refused the rename. */
    private fun put(tmp: File, target: File): Boolean {
        if (tmp.renameTo(target)) return true
        return try {
            tmp.inputStream().use { from ->
                target.outputStream().use { to -> from.copyTo(to) }
            }
            tmp.delete()
            true
        } catch (failed: Exception) {
            runCatching { tmp.delete() }
            false
        }
    }

    /** The bytes a stream has been given, counted as they go past rather than
     *  estimated from the lines that produced them: what fills a phone's
     *  storage is bytes, and a file of accented words is not the length of
     *  its lines. */
    private class ByteCounter(private val out: java.io.OutputStream) : java.io.OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }

        override fun flush() = out.flush()

        override fun close() = out.close()
    }

    /**
     * A display name as a file name: letters, digits and a few marks, the
     * rest underscores, a known suffix of its own taken off so that
     * "spanish.dict" does not become "spanish.dict.dict".
     */
    private fun sanitize(name: String): String {
        var base = name.trim().take(MAX_NAME_LENGTH)
        for (suffix in listOf(".dict", ".dic", ".txt")) {
            if (base.endsWith(suffix, ignoreCase = true) && base.length > suffix.length) {
                base = base.dropLast(suffix.length)
                break
            }
        }
        val clean = StringBuilder(base.length)
        for (c in base) {
            // The brackets are the numbered names' own ("spanish (2)"), so
            // they stay: a name that cannot be written back is a file that
            // cannot be opened again.
            if (c.isLetterOrDigit() || c == '-' || c == '_' || c == '.' || c == ' ' || c == '(' || c == ')') clean.append(c)
            else clean.append('_')
        }
        var out = clean.toString().trim().trim { it == '.' || it == '_' }
        while (out.contains("  ")) out = out.replace("  ", " ")
        if (out.isEmpty()) out = "dictionary"
        return out.take(MAX_NAME_LENGTH)
    }

    /** [base] as a file that is not there yet, numbered when it has to be. */
    private fun uniqueFile(into: File, base: String): File? {
        val first = File(into, "$base$SUFFIX")
        if (!first.exists()) return first
        for (n in 2..100) {
            val next = File(into, "$base ($n)$SUFFIX")
            if (!next.exists()) return next
        }
        return null
    }

    /**
     * How [buffered] is decoded: UTF-8 when the sample is, and Latin-1 --
     * the code set the engine and the files in the wild use -- when it is
     * not. The stream is left where it was, so the import reads from the
     * start whatever the answer.
     */
    private fun detect(buffered: BufferedInputStream): Charset {
        return try {
            buffered.mark(65536)
            val sample = ByteArray(32768)
            var n = 0
            while (n < sample.size) {
                val r = buffered.read(sample, n, sample.size - n)
                if (r <= 0) break
                n += r
            }
            try {
                buffered.reset()
            } catch (broken: Exception) {
                // A stream that cannot rewind is read from here; the import
                // loses its first bytes rather than the whole file.
            }
            // The tail may end mid-character, which strict decoding would
            // refuse for no reason, so a few bytes at the end sit the test
            // out.
            val check = if (n > 8) n - 4 else n
            if (isValidUtf8(sample, check)) Charsets.UTF_8 else Charsets.ISO_8859_1
        } catch (broken: Exception) {
            Charsets.UTF_8
        }
    }

    private fun isValidUtf8(bytes: ByteArray, length: Int): Boolean {
        if (length <= 0) return true
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length))
            true
        } catch (broken: Exception) {
            false
        }
    }
}
