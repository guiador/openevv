package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The rewrites that stop the engine spelling words out.
 *
 * The text checks say each rule fires where it should and, just as important,
 * nowhere else. The last one goes through the engine, because a rule that reads
 * well on paper is only worth having if the speech gets shorter.
 */
@RunWith(AndroidJUnit4::class)
class TextFixesTest {

	@Test
	fun aDigitAfterAWordGetsASpace() {
		assertEquals("teamtalk 5", TextFixes.apply("teamtalk5"))
		assertEquals("covid 19", TextFixes.apply("covid19"))
		assertEquals("win 10", TextFixes.apply("win10"))
	}

	/** The other way round is right already and gets worse when split. */
	@Test
	fun aLetterAfterADigitIsLeftAlone() {
		assertEquals("1st", TextFixes.apply("1st"))
		assertEquals("2nd", TextFixes.apply("2nd"))
		assertEquals("5G", TextFixes.apply("5G"))
	}

	@Test
	fun anOpeningMarkAfterAWordGetsASpace() {
		assertEquals("hello (world)", TextFixes.apply("hello(world)"))
		assertEquals("abc #", TextFixes.apply("abc#"))
		assertEquals("abc {", TextFixes.apply("abc{"))
	}

	/** A closing mark costs nothing where it is and twice as much with a space
	 *  in front of it. */
	@Test
	fun aClosingMarkIsLeftAlone() {
		assertEquals("abc)", TextFixes.apply("abc)"))
		assertEquals("abc]", TextFixes.apply("abc]"))
	}

	@Test
	fun theSSuffixIsPulledBackOn() {
		assertEquals("books(s)", TextFixes.apply("books (s)"))
	}

	/** The opening-mark rule splits it and this one puts it back, so a string
	 *  that was already right stays right. */
	@Test
	fun theSSuffixSurvivesBeingRightAlready() {
		assertEquals("books(s)", TextFixes.apply("books(s)"))
	}

	@Test
	fun aSpaceBeforeAMarkGoes() {
		assertEquals("word.", TextFixes.apply("word ."))
		assertEquals("word,", TextFixes.apply("word ,"))
	}

	/** The comma is what says "ten thousand" rather than "one zero zero zero
	 *  zero", so it stays: the regrouping reads a comma grouping as the
	 *  number whole, in every mode. */
	@Test
	fun commasStayInRoundNumbers() {
		assertEquals("1,000,000", TextFixes.apply("1,000,000"))
		assertEquals("1,000,000 items", TextFixes.apply("1,000,000 items"))
		assertEquals("2,000,000,000", TextFixes.apply("2,000,000,000"))
	}

	/** Only a round thousand qualifies. 12,345 measured the same either way. */
	@Test
	fun otherNumbersKeepTheirCommas() {
		assertEquals("12,345", TextFixes.apply("12,345"))
		assertEquals("September 5, 2026", TextFixes.apply("September 5, 2026"))
	}

	/** What a page leaves behind in the text it hands over: the characters
	 *  that are not words and that the engine would otherwise say something
	 *  for. A zero-width space inside a word must not become a break in it,
	 *  and the space an author insisted on must become an ordinary one. */
	@Test
	fun theCharactersAWebPageCarriesAreTakenOut() {
		assertEquals("hello world", TextFixes.apply("hello\u00A0world"))
		assertEquals("hello world", TextFixes.apply("hello\u2009world"))
		assertEquals("hello world", TextFixes.apply("hello\u3000world"))
		// A zero-width space in the middle of a word is inside the word.
		assertEquals("teamtalk", TextFixes.apply("team\u200Btalk"))
		// A soft hyphen is a place a word could be split, not a hyphen.
		assertEquals("eloquence", TextFixes.apply("elo\u00ADquence"))
		assertEquals("hello", TextFixes.apply("\uFEFFhello"))
		// An object replacement character is where a picture was.
		assertEquals("see here", TextFixes.apply("see \uFFFC here"))
	}

	@Test
	fun theWebCharactersDoNotBreakTheOrdinaryFixes() {
		assertEquals("teamtalk 5", TextFixes.apply("teamtalk\u00A05"))
		assertEquals("word.", TextFixes.apply("word \u00A0."))
	}

	@Test
	fun ordinaryTextIsUntouched() {
		for (text in listOf("3.14", "1.5", "e.g.", "Navigate up", "")) {
			assertEquals(text, TextFixes.apply(text))
		}
	}

	/**
	 * Why the service fixes the text before it annotates it. Backwards, the
	 * digit rule walks into the pause annotation and the engine says it.
	 */
	@Test
	fun fixesRunBeforePauses() {
		val right = Pauses.apply(TextFixes.apply("Navigate up"), Pauses.ALL, true)
		assertTrue("the annotation did not survive: $right", right.endsWith("`p100"))
		assertEquals("Navigate up `p 100", TextFixes.apply(Pauses.apply("Navigate up", Pauses.ALL, true)))
	}

	/** Out of the engine rather than off the page. */
	@Test
	fun theSpellingActuallyStops() {
		val e = EvvEngine.open(ENGLISH)
		assertNotNull(e)
		try {
			e!!.setSampleRate(11025)
			e.applyVoice(0)
			for (text in listOf("teamtalk5", "windows11", "hello(world)")) {
				val before = say(e, text)
				val after = say(e, TextFixes.apply(text))
				android.util.Log.e(TAG, "$text: $before then $after")
				assertTrue("no shorter: $text, $before then $after", after < before * 0.9)
			}
		} finally {
			e?.close()
		}
	}

	/** Hex picks up a space it has no use for. The engine spells it out either
	 *  way, byte for byte, so it is left as it falls out of the rule rather
	 *  than carved around. */
	@Test
	fun hexSoundsTheSameEitherWay() {
		assertEquals("0x 1F", TextFixes.apply("0x1F"))
	}

	private fun say(e: EvvEngine, text: String): Int {
		if (!e.speak(text)) return -1
		var total = 0
		val buffer = ByteArray(4096)
		while (true) {
			val n = e.read(buffer)
			if (n <= 0) break
			total += n
		}
		return total
	}

	private companion object {
		const val TAG = "evvfixes"
		const val ENGLISH = 0x00010000
	}
}
