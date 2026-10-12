package com.blindtechnexus.freeeloquencetts

/**
 * Long text, cut into pieces the engine can be stopped between.
 *
 * The engine cannot abandon an utterance. That is settled rather than open --
 * openevv's own documents say six ways of adding an abandonment point were
 * tried and all fault -- so asking for silence waits out whatever is in flight.
 * For one line of a list that is a thirtieth of a second and nobody hears it.
 * For a paragraph it is not, and a reader swiping every couple of hundred
 * milliseconds has everything that lands inside the wait dropped: speech goes
 * quiet for several items and then catches up on the last one.
 *
 * Handing the text over in pieces makes the wait one piece rather than one
 * message. Where the piece ends is the whole of the problem, because ending
 * one is not free: the engine pauses at the end of an utterance as it would at
 * a full stop, so a boundary it would not have made itself is an audible gap.
 *
 * The rule here is openevv's, ported from its NVDA driver, and the numbers in
 * it are measured rather than chosen. A sentence end costs nothing -- one
 * message came to 252,010 samples whole and the same 252,010 in six pieces --
 * and anywhere else costs about four tenths of a second. So a full stop has to
 * argue that it is one: a word carrying a dot inside it is an abbreviation
 * ("e.g.", "U.S.") and a short word starting with a capital is an initial or a
 * title ("J.", "Mr."), and cutting at those measured 1.48 s and 0.70 s longer
 * than the same sentences whole. Only past [PIECE_CAP] characters does a piece
 * end at plain whitespace, which bounds the case that offers no sentence end
 * at all.
 *
 * A cut that had to be made is marked as one, in [Piece.forced], and the
 * service answers it with the engine's own short-pause annotation. That pause
 * is not the engine's prosody, which is reading the text as it stands: it is
 * an artefact of where this file happened to cut, and thousands of words of
 * text with few full stops in it used to carry four tenths of a second of
 * silence every [PIECE_CAP] characters -- some thirty seconds of waiting in a
 * ten minute passage, added to nothing. Everything the text says is still
 * said: the pieces are the same bytes in the same order, and only the gap
 * between them is what the unsplit text would have had.
 *
 * Everything here is one pass over the text, at indices, because this runs on
 * the way to every utterance. It used to cut the text into a list of runs
 * first, which is two allocations per word and a second pass over all of it,
 * and on a chapter of a book that is a hundred thousand small objects made to
 * be thrown away -- the sort of cost that reads as "the engine is slow" and
 * has nothing to do with the engine.
 */
object TextPieces {

	/** How far a piece runs before it ends at whitespace for want of a
	 *  sentence end. Reduced to 250 for very low latency interruption:
	 *  smaller pieces = faster stop, at cost of more engine calls. */
	const val PIECE_CAP = 250

	/** One piece of text, and whether its end is a place the engine would have
	 *  paused anyway. [forced] says it is not, which is what earns the
	 *  shortened pause described above. */
	class Piece(val text: String, val forced: Boolean)

	/** Closing marks that may sit after the punctuation that ends a sentence. */
	private const val CLOSERS = "\"')]}»”’"

	private const val ENDERS = "?!…。！？"

	/** Whether a word ends a sentence rather than being a dotted abbreviation. */
	fun endsSentence(word: String): Boolean = endsSentence(word, 0, word.length)

	/** The same question, asked of `text[from, to)` without cutting a string
	 *  out to ask it. */
	fun endsSentence(text: CharSequence, from: Int, to: Int): Boolean {
		var end = to
		while (end > from && text[end - 1] in CLOSERS) end--
		if (end == from) return false
		val last = text[end - 1]
		if (last in ENDERS) return true
		if (last != '.' || end - from < 2 || text[end - 2].isDigit()) return false
		var inside = false
		for (at in from until end - 1) {
			if (text[at] == '.') {
				inside = true
				break
			}
		}
		if (inside) return false
		return !(end - 1 - from <= 3 && text[from].isUpperCase())
	}

	/**
	 * [text] as the pieces to hand over one at a time. Every character of the
	 * original is in exactly one piece and in the same order, so the speech is
	 * the same speech.
	 */
	fun split(text: String): List<String> = pieces(text).map { it.text }

	/** [text] as the pieces to hand over, with what each end is. */
	fun pieces(text: String): List<Piece> = pieceSequence(text).toList()

	/**
	 * The same pieces as [pieces], produced one at a time rather than all at
	 * once.
	 *
	 * The difference is the whole of the point and it is about a document. A
	 * list is every piece made before the first one is used, so a chapter is
	 * cut in full -- and then rewritten in full by whoever consumes it --
	 * before a single syllable is spoken. A sequence makes the next piece when
	 * it is asked for, which puts the first syllable one piece of work behind
	 * the request instead of one document behind it.
	 *
	 * One piece is held back from being yielded at a time, which is what makes
	 * this a sequence rather than the list above with `yield` in it: trailing
	 * whitespace belongs to the piece in front of it rather than to a piece of
	 * its own, and the piece in front of it has to still be in hand when the
	 * end of the text says so. Holding one is a reference, and it is the same
	 * one piece of lookahead the consumer of this sequence needs anyway to
	 * know whether the piece it has is the last.
	 */
	fun pieceSequence(text: String, cap: Int = PIECE_CAP, hardCut: Boolean = false): Sequence<Piece> = sequence {
        require(cap in 32..1000)
        if (hardCut) {
            var start = 0
            while (start < text.length) {
                if (Thread.currentThread().isInterrupted) return@sequence
                var end = minOf(start + cap, text.length)
                // Never bisect a UTF-16 surrogate pair. Linguistic boundaries may be cut.
                if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
                yield(Piece(text.substring(start, end), end < text.length))
                start = end
            }
            return@sequence
        }
		if (text.length <= cap && !text.any { it.isWhitespace() }) {
			yield(Piece(text, false))
			return@sequence
		}
		var held: Piece? = null
		var yielded = false
		var start = 0
		var sentence = false
		var at = 0
		while (at < text.length) {
			val space = text[at].isWhitespace()
			val from = at
			while (at < text.length && text[at].isWhitespace() == space) at++
			if (!space) {
				sentence = endsSentence(text, from, at)
				continue
			}
			// A piece ends after the space that follows the word, so no word is
			// split and nothing is left leaning on the next piece.
			if (sentence || at - start >= cap) {
				val previous = held
				if (previous != null) {
					yield(previous)
					yielded = true
				}
				held = Piece(text.substring(start, at), !sentence)
				start = at
			}
			sentence = false
		}
		if (start < text.length) {
			val rest = text.substring(start)
			val previous = held
			when {
				rest.isNotBlank() -> {
					if (previous != null) {
						yield(previous)
						yielded = true
					}
					held = Piece(rest, false)
				}
				// Trailing space belongs to the piece in front of it rather
				// than to a piece of its own, which would be an utterance
				// saying nothing.
				previous != null -> held = Piece(previous.text + rest, previous.forced)
				else -> held = null
			}
		}
		val last = held
		if (last != null) {
			yield(last)
			yielded = true
		}
		// A text with nothing in it to cut at -- whitespace and no words, say
		// -- is still one piece, because the caller asked for the text in
		// pieces and nought of them is an answer to nothing.
		if (!yielded) yield(Piece(text, false))
	}
}
