package com.blindtechnexus.freeeloquencetts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spelling a word, letter by letter or by its NATO name.
 *
 * The part worth a test of its own is the capitals setting, because it is the
 * one that has to know a word when it sees one: `NASA` is an acronym and
 * `OK` is not, and a setting that spelled every word would be worse than
 * none, which is the whole reason it is called "capitals" rather than
 * "letters".
 */
class SpellingTest {

	@Test
	fun aWordIsSpelledLetterByLetter() {
		assertEquals("c a t", Spelling.apply("cat", Spelling.LETTERS))
		assertEquals("H e l l o , w o r l d", Spelling.apply("Hello, world", Spelling.LETTERS))
	}

	@Test
	fun aWordIsSpelledByItsNatoName() {
		assertEquals("Charlie Alpha Tango", Spelling.apply("cat", Spelling.NATO))
		// Digits have names too, because a model number is the thing this
		// setting exists for.
		assertEquals("Alpha One Bravo Two", Spelling.apply("A1B2", Spelling.NATO))
	}

	@Test
	fun theCapitalsSettingKnowsAnAcronymFromAWord() {
		assertEquals("The D O J met", Spelling.apply("The DOJ met", Spelling.CAPITALS))
		assertEquals("The Delta Oscar Juliett met", Spelling.apply("The DOJ met", Spelling.CAPITALS_NATO))
		// Two capitals is a word, and spelling it would be wrong.
		assertEquals("OK is fine", Spelling.apply("OK is fine", Spelling.CAPITALS))
		assertEquals("I am", Spelling.apply("I am", Spelling.CAPITALS))
		// A lower case letter anywhere in the word ends the question.
		assertEquals("NASA and nasa", Spelling.apply("NASA and nasa", Spelling.CAPITALS))
	}

	@Test
	fun offLeavesTheTextAlone() {
		val text = "NASA met the DOJ at 3.15"
		assertEquals(text, Spelling.apply(text, Spelling.OFF))
	}

	@Test
	fun everyModeIsInModeS() {
		assertEquals(5, Spelling.MODES.size)
		assertTrue(Spelling.MODES.contains(Spelling.OFF))
		assertTrue(Spelling.MODES.contains(Spelling.CAPITALS_NATO))
		// The settings screen walks this list and the model stores what it
		// holds, so a mode added without being listed would be reachable from
		// nowhere and a mode listed without existing would not compile.
		assertEquals(Spelling.MODES.distinct().size, Spelling.MODES.size)
	}
}
