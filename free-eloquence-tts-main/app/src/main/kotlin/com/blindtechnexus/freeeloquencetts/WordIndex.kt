package com.blindtechnexus.freeeloquencetts

/**
 * The words a dictionary holds, and the text they are matched against.
 *
 * This is the thing that a dictionary of millions of words used to be
 * impossible without. The matcher before it was a trie with a hash map at
 * every node -- one object per character of every word, all of them alive at
 * once, built on the way to the first syllable of every utterance after a
 * change. A file of a few hundred thousand entries is a few million such
 * objects, and a phone that was asked to hold them while it also held the
 * previous copy answered the only way it could: it ran out of memory in the
 * middle of the framework's synthesis thread, and the voice -- the whole
 * engine, for the rest of the session -- went with it.
 *
 * What replaces it works the other way round. The text decides what is
 * looked at: the utterance is walked word by word, and for each word the
 * index answers, in one hash probe, whether any entry begins with it. A
 * dictionary of ten entries and a dictionary of a million cost an utterance
 * the same, because neither is walked -- and the only thing that grows with
 * the dictionary is the fixed array the words sit in, which is bounded here
 * before it is ever built. See [Builder], which is the whole of the bound:
 * a budget in characters, and entries past it counted rather than kept.
 *
 * The shapes of the storage are three:
 *
 * - One `CharArray` holds every key and every replacement, back to back, with
 *   offsets beside it. Strings would be two objects apiece -- the String and
 *   its array -- and a hundred thousand of them is a hundred thousand objects
 *   the garbage collector has to walk; this is one array and four numbers per
 *   entry, which is what a phone can hold.
 * - One open-addressed table maps the folded first word of a key to the chain
 *   of entries that begin with it. Probing is linear over a table sized to
 *   twice the entries, so a probe is a couple of array reads.
 * - One chain per table slot, which is what makes a phrase and its own first
 *   word -- "new" and "new york" -- live in the same probe and be told apart
 *   by the full comparison.
 *
 * Keys are stored folded (lower case, runs of whitespace down to one space)
 * and the text is folded as it is compared, so matching is case-blind the way
 * the dictionary manager has always promised, and no folded copy of the text
 * is ever made. A key that begins with something other than a letter or a
 * digit -- ".net", "e.g.", "$5" -- cannot be reached by a word, so those live
 * in a second, much smaller index keyed by their first character and are
 * tried at the offsets where such a key could begin.
 *
 * Nothing here allocates per word of the text. A text with no match in it is
 * answered with the very string it came in as, which is the case for almost
 * every utterance and costs one comparison.
 */
class WordIndex private constructor(
    private val pool: CharArray,
    private val keyAt: IntArray,
    private val keyLen: IntArray,
    private val sayAt: IntArray,
    private val sayLen: IntArray,
    private val next: IntArray,
    private val table: IntArray,
    private val tableHash: IntArray,
    private val mask: Int,
    private val punctHead: IntArray,
    private val punctNext: IntArray,
    val size: Int,
    val dropped: Int,
    val pooledChars: Int
) {

    private val empty: Boolean = size == 0

    /** How many words of the dictionary are in use, and how many were left
     *  out for want of room. The second is what the dictionary screen says
     *  out loud rather than leaving somebody to wonder why a word they
     *  imported is not being said. */
    val isEmpty: Boolean get() = empty

    /**
     * [text] with every entry in this index replaced, or [text] itself when
     * nothing matched.
     *
     * One pass, at indices, and the copy of the text is not started until the
     * first entry hits: a text with no dictionary word in it -- which is most
     * of them -- is not copied at all. The longest match at a position wins,
     * so "new york" beats "new" where both are entries, and a match is never
     * taken in the middle of a word ("cat" does not touch "bobcat").
     */
    fun apply(text: String): String {
        if (empty || text.isEmpty()) return text
        val n = text.length
        var out: StringBuilder? = null
        var copied = 0
        var i = 0
        while (i < n) {
            val c = text[i]
            var end = -1
            var entry = -1
            if (isWordChar(c)) {
                // A word is only ever matched from its own beginning, so a
                // key cannot be found inside a longer word. That is the rule
                // the trie had as well, and it is the one the tests hold.
                if (i == 0 || !isWordChar(text[i - 1])) {
                    val hit = matchWord(text, i)
                    if (hit >= 0) {
                        end = (hit ushr 32).toInt()
                        entry = (hit and 0xFFFFFFFFL).toInt() - 1
                    }
                }
            } else if (punctHead.isNotEmpty()) {
                val hit = matchPunctuation(text, i)
                if (hit >= 0) {
                    end = (hit ushr 32).toInt()
                    entry = (hit and 0xFFFFFFFFL).toInt() - 1
                }
            }
            if (entry < 0 || end <= i) {
                i++
                continue
            }
            if (out == null) out = StringBuilder(n + 16)
            if (copied < i) out.append(text, copied, i)
            val at = sayAt[entry]
            out.append(pool, at, sayLen[entry])
            copied = end
            i = end
        }
        val answer = out ?: return text
        if (copied < n) answer.append(text, copied, n)
        return answer.toString()
    }

    /** The longest key that starts at [from], packed as end shl 32 or
     *  entry+1, or -1 when nothing starts there. */
    private fun matchWord(text: String, from: Int): Long {
        val n = text.length
        var to = from
        while (to < n && isWordChar(text[to])) to++
        if (to == from) return -1L
        val hash = foldHash(text, from, to)
        var slot = hash and mask
        var best = -1L
        while (true) {
            val held = table[slot]
            if (held == 0) return best
            if (tableHash[slot] == hash) {
                var e = held - 1
                while (e >= 0) {
                    val end = compare(text, from, e)
                    if (end > 0) {
                        val packed = (end.toLong() shl 32) or (e + 1).toLong()
                        if (best < 0 || end > (best ushr 32).toInt()) best = packed
                    }
                    e = next[e]
                }
                return best
            }
            slot = (slot + 1) and mask
        }
    }

    /** The same question for a key that does not begin with a word, asked at
     *  an offset that is not one. */
    private fun matchPunctuation(text: String, at: Int): Long {
        val bucket = fold(text[at]).code and (punctHead.size - 1)
        var e = punctHead[bucket] - 1
        var best = -1L
        while (e >= 0) {
            val end = compare(text, at, e)
            if (end > 0 && (best < 0 || end > (best ushr 32).toInt())) {
                best = (end.toLong() shl 32) or (e + 1).toLong()
            }
            e = punctNext[e]
        }
        return best
    }

    /**
     * How far the key of [entry] reaches when it starts at [from], or -1 when
     * it does not. Words are compared whole, a run of whitespace in the key
     * matches any run of whitespace in the text, and a match has to end where
     * a word does -- so "new york" is not found in "new yorker".
     */
    private fun compare(text: String, from: Int, entry: Int): Int {
        val n = text.length
        var t = from
        var k = keyAt[entry]
        val kEnd = k + keyLen[entry]
        while (k < kEnd) {
            if (pool[k] == ' ') {
                if (t >= n || !text[t].isWhitespace()) return -1
                while (t < n && text[t].isWhitespace()) t++
                while (k < kEnd && pool[k] == ' ') k++
                continue
            }
            if (t >= n) return -1
            if (fold(text[t]) != pool[k]) return -1
            t++
            k++
        }
        if (t < n && isWordChar(text[t])) return -1
        return t
    }

    private fun foldHash(text: String, from: Int, to: Int): Int {
        var hash = 1
        var i = from
        while (i < to) {
            hash = hash * 31 + fold(text[i]).code
            i++
        }
        return hash
    }

    private fun fold(c: Char): Char =
        if (c in 'A'..'Z') (c + 32) else Character.toLowerCase(c)

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\''

    /**
     * The index being made, one entry at a time, with the bound on it.
     *
     * The bound is a number of characters and not a number of entries,
     * because characters are what runs a phone out of memory: a dictionary of
     * single letters holds a hundred times the entries of a dictionary of
     * phrases in the same bytes. [DEFAULT_BUDGET_CHARS] is around three
     * hundred thousand ordinary entries -- a dictionary that fits in the
     * memory the old trie spent on twenty thousand -- and past it an entry is
     * counted in [WordIndex.dropped] rather than kept. Nothing here throws,
     * nothing here allocates an object per entry, and the build is
     * proportional to what is kept, so an import of a billion words costs the
     * same as an import of a thousand: the file is read once, in order, and
     * stops being read when the budget is full.
     */
    class Builder(
        private val budgetChars: Int = DEFAULT_BUDGET_CHARS,
        private val maxEntries: Int = DEFAULT_MAX_ENTRIES
    ) {

        private var pool = CharArray(1 shl 12)
        private var pooled = 0

        private var entryKeyAt = IntArray(256)
        private var entryKeyLen = IntArray(256)
        private var entrySayAt = IntArray(256)
        private var entrySayLen = IntArray(256)
        private var entryFirst = IntArray(256)
        private var entries = 0

        private var dropped = 0

        private var folded = CharArray(128)

        val size: Int get() = entries
        val overflowed: Int get() = dropped

        /** The room left, in characters, for the rest of the dictionary. */
        val roomLeft: Int get() = budgetChars - pooled

        /**
         * One entry, and whether it went in. An entry that cannot be matched
         * -- an empty key, a key with nothing in it but whitespace, a key
         * longer than [MAX_KEY_CHARS], a replacement longer than
         * [MAX_SAY_CHARS] -- is refused without being counted as dropped,
         * because it was never a word anyone could have said.
         */
        fun add(word: String, say: String): Boolean {
            if (entries >= maxEntries) {
                dropped++
                return false
            }
            if (word.isEmpty() || say.isEmpty()) return false
            val key = fold(word)
            if (key.isEmpty()) return false
            if (key.length > MAX_KEY_CHARS) {
                dropped++
                return false
            }
            if (say.length > MAX_SAY_CHARS) {
                dropped++
                return false
            }
            val need = key.length + say.length
            if (pooled + need > budgetChars) {
                dropped++
                return false
            }
            if (entries == entryKeyAt.size) grow()
            if (pooled + need > pool.size) growPool(pooled + need)
            val keyAt = pooled
            key.toCharArray(pool, pooled)
            pooled += key.length
            val sayAt = pooled
            say.toCharArray(pool, pooled)
            pooled += say.length
            entryKeyAt[entries] = keyAt
            entryKeyLen[entries] = key.length
            entrySayAt[entries] = sayAt
            entrySayLen[entries] = say.length
            entryFirst[entries] = firstTokenLength(pool, keyAt, key.length)
            entries++
            return true
        }

        /** The whole of what was added, in one index, ready to match with. */
        fun build(): WordIndex {
            if (entries == 0) {
                return WordIndex(
                    CharArray(0), IntArray(0), IntArray(0), IntArray(0), IntArray(0),
                    IntArray(0), IntArray(0), IntArray(0), 0,
                    IntArray(0), IntArray(0), 0, dropped, 0
                )
            }
            var capacity = Integer.highestOneBit(entries * 4 - 1) shl 1
            if (capacity < 16) capacity = 16
            var table = IntArray(capacity)
            var hashes = IntArray(capacity)
            var mask = capacity - 1
            val next = IntArray(entries) { -1 }
            for (e in 0 until entries) {
                val length = entryFirst[e]
                val hash = hashOf(pool, entryKeyAt[e], length)
                var slot = hash and mask
                while (true) {
                    val held = table[slot]
                    if (held == 0) {
                        table[slot] = e + 1
                        hashes[slot] = hash
                        break
                    }
                    if (hashes[slot] == hash) {
                        // The same first word, or a key that begins with it:
                        // the new entry joins that chain rather than taking
                        // a slot of its own.
                        next[e] = held - 1
                        table[slot] = e + 1
                        break
                    }
                    slot = (slot + 1) and mask
                }
            }
            // A key whose first character is not a word begins nowhere a
            // word does, so those are indexed by that character instead and
            // tried at the offsets they can start at.
            val punctHead = IntArray(PUNCT_BUCKETS)
            var punctCount = 0
            for (e in 0 until entries) {
                if (entryFirst[e] != 0) continue
                punctCount++
            }
            val punctNext = IntArray(entries) { -1 }
            if (punctCount > 0) {
                for (e in 0 until entries) {
                    if (entryFirst[e] != 0) continue
                    // The fold here is the character one, written out
                    // rather than called: this is a nested class, and a
                    // nested class in Kotlin has no outer instance to reach
                    // the character fold through -- only its own, which
                    // takes a word.
                    val c = Character.toLowerCase(pool[entryKeyAt[e]])
                    val bucket = c.code and (PUNCT_BUCKETS - 1)
                    punctNext[e] = punctHead[bucket] - 1
                    punctHead[bucket] = e + 1
                }
            }
            return WordIndex(
                pool.copyOf(pooled),
                entryKeyAt.copyOf(entries),
                entryKeyLen.copyOf(entries),
                entrySayAt.copyOf(entries),
                entrySayLen.copyOf(entries),
                next,
                table,
                hashes,
                mask,
                if (punctCount == 0) IntArray(0) else punctHead,
                if (punctCount == 0) IntArray(0) else punctNext,
                entries,
                dropped,
                pooled
            )
        }

        private fun grow() {
            val want = entries * 2
            entryKeyAt = entryKeyAt.copyOf(want)
            entryKeyLen = entryKeyLen.copyOf(want)
            entrySayAt = entrySayAt.copyOf(want)
            entrySayLen = entrySayLen.copyOf(want)
            entryFirst = entryFirst.copyOf(want)
        }

        private fun growPool(need: Int) {
            var want = pool.size
            while (want < need) want *= 2
            pool = pool.copyOf(want)
        }

        /**
         * The key as it is stored: lower case, tabs and newlines to spaces,
         * runs of spaces down to one, the ends trimmed. The folded copy is
         * reused between entries rather than made per entry, which is one
         * small array against a hundred thousand short-lived ones.
         */
        private fun fold(word: String): String {
            if (folded.size < word.length) folded = CharArray(word.length * 2)
            var n = 0
            var spaced = true
            for (c in word) {
                val foldedChar = when {
                    c in 'A'..'Z' -> (c + 32)
                    c == '\t' || c == '\n' || c == '\r' || c == '\u000B' || c == '\u000C' -> ' '
                    else -> Character.toLowerCase(c)
                }
                if (foldedChar == ' ') {
                    if (spaced) continue
                    spaced = true
                    folded[n++] = ' '
                    continue
                }
                spaced = false
                if (n >= MAX_KEY_CHARS) break
                folded[n++] = foldedChar
            }
            while (n > 0 && folded[n - 1] == ' ') n--
            if (n == 0) return ""
            return String(folded, 0, n)
        }

        /** Where the leading run of word characters of a stored key ends. */
        private fun firstTokenLength(text: CharArray, from: Int, length: Int): Int {
            var at = 0
            while (at < length) {
                val c = text[from + at]
                if (!(c.isLetterOrDigit() || c == '\'')) break
                at++
            }
            return at
        }

        private fun hashOf(text: CharArray, from: Int, length: Int): Int {
            var hash = 1
            for (at in 0 until length) hash = hash * 31 + text[from + at].code
            return hash
        }
    }

    companion object {

        /** The one index with nothing in it, handed out rather than made:
         *  an empty dictionary is the common case and it costs nothing. */
        val EMPTY: WordIndex = Builder(budgetChars = 0, maxEntries = 0).build()

        /** How many characters of keys and replacements one index keeps
         *  when the caller names nothing. Four million characters is around
         *  two hundred thousand ordinary entries -- the same dictionary the
         *  old trie could not hold -- and the whole index is under twenty
         *  megabytes with its tables. [UserDictionary] asks for more than
         *  this on a device whose heap can afford it, and for exactly this
         *  on one whose heap cannot; see [UserDictionary.indexBudgetChars]. */
        const val DEFAULT_BUDGET_CHARS = 4 * 1024 * 1024

        /** And a ceiling on entries as well, because a dictionary of
         *  one-letter words would otherwise reach the character budget with
         *  more entries than the arrays beside them can hold. Sixteen
         *  characters an entry against a four-million-character budget, and
         *  the same ratio as the budget grows. */
        const val DEFAULT_MAX_ENTRIES = 250_000

        /** The longest key kept. Longer than any word anybody has imported,
         *  and short enough that a hostile file cannot make one entry cost
         *  the memory of thousands. */
        const val MAX_KEY_CHARS = 128

        /** The longest replacement kept. The engine's own pronunciation
         *  alphabet is a few characters a phoneme, and the longest entry in
         *  the dictionaries in the wild is under a hundred. */
        const val MAX_SAY_CHARS = 400

        /** Buckets for keys that begin with something that is not a word:
         *  one per first character, which is what has to match anyway. */
        private const val PUNCT_BUCKETS = 256

        /** An index over [entries], built for a caller that has a list rather
         *  than a file -- the tests, and any place that has the pairs already.
         *  Bounded exactly as the file build is. */
        fun of(entries: List<Pair<String, String>>): WordIndex {
            if (entries.isEmpty()) return EMPTY
            val builder = Builder()
            for ((word, say) in entries) {
                if (!builder.add(word, say) && builder.roomLeft <= 0) break
            }
            return builder.build()
        }
    }
}
