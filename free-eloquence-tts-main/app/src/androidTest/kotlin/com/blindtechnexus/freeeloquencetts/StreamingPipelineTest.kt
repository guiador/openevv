package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A document is spoken from its first sentence, not from its last.
 *
 * The road the text took before was: cut it into every piece it has, rewrite
 * every one of them, and only then ask the engine for the first syllable. A
 * chapter is a few thousand pieces, so the wait before the first sound grew
 * with the length of what was being read, and a person who swiped away two
 * seconds later had paid for the whole chapter and heard one sentence.
 *
 * The pieces are now a sequence, and a piece is rewritten when the utterance
 * reaches it. Two things follow, and both are what is checked here: the
 * pieces have to be the same pieces, in the same order, saying the same thing,
 * and asking for the first of them has to cost the work of one of them rather
 * than the work of all of them.
 */
@RunWith(AndroidJUnit4::class)
class StreamingPipelineTest {

	private lateinit var context: Context
	private lateinit var settings: Settings

	@Before
	fun setUp() {
		context = InstrumentationRegistry.getInstrumentation().targetContext
		settings = Settings(context)
	}

	@Test
	fun thePiecesAreThePiecesTheListGave() {
		for (text in SAMPLES) {
			val asList = TextPieces.pieces(text)
			val asSequence = TextPieces.pieceSequence(text).toList()
			assertEquals("different number of pieces for ${show(text)}", asList.size, asSequence.size)
			for (at in asList.indices) {
				assertEquals("piece $at of ${show(text)}", asList[at].text, asSequence[at].text)
				assertEquals("piece $at of ${show(text)}", asList[at].forced, asSequence[at].forced)
			}
			// And the whole of the text is still there, in the order it was
			// written, because a piece that held back its trailing whitespace
			// and never gave it back would be a word joined to the next one.
			assertEquals(text, asSequence.joinToString("") { it.text })
		}
	}

	@Test
	fun askingForTheFirstPieceCostsOnePieceOfWork() {
		val document = sentence(400)
		var made = 0
		val stream = TextPipeline
			.pieces(document, settings, emptyList(), emptyList())
			.onEach { made++ }
		stream.take(2).toList()
		assertEquals(2, made)
		// The rest of it is still there to be had.
		val all = made + stream.drop(2).count()
		assertTrue("a document this long has more than two pieces in it", all > 2)
	}

	@Test
	fun aLongDocumentIsCutIntoItsPiecesAndNotIntoFewer() {
		val document = sentence(400)
		assertTrue(document.length > EvvTtsService.STREAM_FROM_CHARS)
		val raw = TextPieces.pieceSequence(document).toList()
		val streamed = TextPipeline
			.pieces(document, settings, emptyList(), emptyList())
			.toList()
		// The pipeline drops a piece only when the settings reduce it to
		// nothing, which prose is not reduced to. A piece fewer here would be
		// a sentence of the document that was never said, and that is the one
		// way a streaming road can go wrong without saying anything at all.
		assertEquals(raw.size, streamed.size)
	}

	@Test
	fun theFirstPieceIsOnePieceAndNotTheWholeDocument() {
		val document = sentence(400)
		val streamed = TextPipeline
			.pieces(document, settings, emptyList(), emptyList())
			.toList()
		// This is the whole of what the change buys: what the first syllable
		// waits for is the work of one piece, and a piece is a couple of
		// hundred characters whatever the document it came out of is.
		assertTrue(streamed.size > 50)
		assertTrue(
			"the first piece is ${streamed.first().text.length} characters " +
				"out of a document of ${document.length}",
			streamed.first().text.length < document.length / 20
		)
	}

	@Test
	fun aShortTextIsNotStreamed() {
		// Below the threshold the service takes the single pass, and the
		// threshold is a number the service owns: the test reads it rather
		// than repeating it, so a change of it is a change of one line.
		val short = "Settings."
		assertTrue(short.length < EvvTtsService.STREAM_FROM_CHARS)
		assertEquals(
			TextPipeline.apply(short, settings, emptyList(), emptyList()),
			TextPipeline.pieces(short, settings, emptyList(), emptyList())
				.toList()
				.joinToString("") { it.text }
		)
	}

	@Test
	fun nothingIsLostOnTheWayThrough() {
		for (text in SAMPLES) {
			if (text.isBlank()) continue
			val said = TextPipeline
				.pieces(text, settings, emptyList(), emptyList())
				.toList()
			assertTrue("a text is never reduced to nothing by being cut up", said.isNotEmpty())
			assertTrue(
				"every piece of ${show(text)} says something",
				said.all { it.text.isNotBlank() }
			)
		}
	}

	private fun sentence(times: Int): String = buildString {
		for (at in 1..times) {
			append("This is the sentence number ")
			append(at)
			append(" of a document that goes on for some way. ")
		}
	}

	private fun show(text: String): String =
		if (text.length > 40) text.take(37) + "..." else text

	private companion object {
		val SAMPLES = listOf(
			"",
			"Settings.",
			"One two three. Four five six. Seven eight nine.",
			"Mr. Jones and J. R. R. Tolkien met at 3.15 on the U.S. side.",
			"   leading and trailing whitespace   ",
			"a word that goes on and on with no punctuation at all",
			"First line.\nSecond line.\n\nThird paragraph.",
			"trailing space only.   "
		)
	}
}
