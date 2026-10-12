package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What each level of symbol pronunciation does to the text.
 *
 * The engine has no setting for this, so the levels are made out of text
 * changes, and the rules those follow were measured against the engine rather
 * than assumed: a symbol standing on its own is always named by the engine
 * itself, the ones it reads in place ("$5" is "five dollars") are never named
 * here because a replacement would lose the number, and the backtick is never
 * touched because it is the engine's annotation escape. What is checked here
 * is that the filter keeps to those rules.
 */
@RunWith(AndroidJUnit4::class)
class SymbolsTest {

	@Test
	fun someLeavesTheEngineAlone() {
		assertEquals("Hello, world. 50% (yes)", Symbols.apply("Hello, world. 50% (yes)", Symbols.SOME, ""))
	}

	@Test
	fun allNamesThePunctuationTheEngineLeavesSilent() {
		assertEquals("Hello comma world dot", Symbols.apply("Hello, world.", Symbols.ALL, ""))
		assertEquals("Hello dot World question mark", Symbols.apply("Hello. World?", Symbols.ALL, ""))
	}

	@Test
	fun mostNamesBracketsButNotSentencePunctuation() {
		assertEquals(
			"Hello left parenthesis world right parenthesis .",
			Symbols.apply("Hello (world).", Symbols.MOST, "")
		)
		assertEquals("Hello, world.", Symbols.apply("Hello, world.", Symbols.MOST, ""))
	}

	@Test
	fun theEnginesOwnSymbolsAreNeverReplaced() {
		// Naming these ourselves would trade the engine's "five dollars" for
		// "five dollar", so every level that does not silence them leaves them.
		assertEquals("50% of $5", Symbols.apply("50% of $5", Symbols.ALL, ""))
		assertEquals("50% of $5", Symbols.apply("50% of $5", Symbols.MOST, ""))
		// The backslash is one of them, measured like the rest: attached, the
		// engine reads it in place -- "a\b" is "a backslash b" in its own
		// audio, a Windows path is spelled out -- so it is left for it.
		assertEquals("a\\b", Symbols.apply("a\\b", Symbols.ALL, ""))
	}

	@Test
	fun noneSilencesTheBackslashTheEngineNames() {
		// What the engine names has to be taken away at none, or a level that
		// promises silence would still say "backslash" in the middle of a path.
		assertEquals("a b", Symbols.apply("a\\b", Symbols.NONE, ""))
		assertEquals("C: Users", Symbols.apply("C:\\Users", Symbols.NONE, ""))
		// On the list, the engine's own reading is kept.
		assertEquals("C:\\Users", Symbols.apply("C:\\Users", Symbols.CUSTOM, "\\"))
	}

	@Test
	fun noneSilencesEverythingButThePauses() {
		assertEquals("50 of 5", Symbols.apply("50% of $5", Symbols.NONE, ""))
		assertEquals("a b and c d", Symbols.apply("a=b and c+d", Symbols.NONE, ""))
		assertEquals("user example com", Symbols.apply("user@example.com", Symbols.NONE, ""))
		// The marks the engine pauses at stay, and so does the pause.
		assertEquals("Hello. World?", Symbols.apply("Hello. World?", Symbols.NONE, ""))
		// Standing alone it would have been named, so standing alone it goes.
		assertEquals("Is this OK", Symbols.apply("Is this OK ?", Symbols.NONE, ""))
	}

	@Test
	fun numbersAndContractionsBelongToTheEngine() {
		val numbers = "It's 3.14 at 2:30, 1,000 units"
		assertEquals(numbers, Symbols.apply(numbers, Symbols.NONE, ""))
		// At all, the list comma after the time is named, because it is a
		// comma in the text rather than a mark inside a number; the decimal
		// point, the time's colon and the thousands separator are not.
		assertEquals(
			"It's 3.14 at 2:30 comma 1,000 units",
			Symbols.apply(numbers, Symbols.ALL, "")
		)
		assertEquals("don't stop", Symbols.apply("don't stop", Symbols.ALL, ""))
	}

	@Test
	fun customNamesOnlyWhatIsOnTheList() {
		// The dollar is on the list, so the engine keeps reading it in place;
		// the percent is not, so it is silenced.
		assertEquals("50 of $5", Symbols.apply("50% of $5", Symbols.CUSTOM, "$"))
		// A listed bracket is named; an unlisted one is not.
		assertEquals(
			"a# left parenthesis b dot c!",
			Symbols.apply("a# (b). c!", Symbols.CUSTOM, "#(.")
		)
		// An empty list is none.
		assertEquals("a b", Symbols.apply("a (b)", Symbols.CUSTOM, " "))
	}

	@Test
	fun typographicSymbolsBehaveLikePlainOnes() {
		assertEquals("quote hello quote", Symbols.apply("“hello”", Symbols.MOST, ""))
		assertEquals("Wait...", Symbols.apply("Wait…", Symbols.NONE, ""))
	}

	@Test
	fun aSymbolStandingAloneIsTheEnginesOwnToName() {
		assertEquals("* # ?", Symbols.apply("* # ?", Symbols.ALL, ""))
		assertEquals("* # ?", Symbols.apply("* # ?", Symbols.MOST, ""))
	}

	@Test
	fun theBacktickIsNeverTouched() {
		// It is the engine's annotation escape: "`p1" asks for a pause, and
		// replacing the backtick would turn a request into a word.
		assertEquals("`p1 hello", Symbols.apply("`p1 hello", Symbols.NONE, ""))
		assertEquals("`p1 hello", Symbols.apply("`p1 hello", Symbols.ALL, ""))
	}
}
