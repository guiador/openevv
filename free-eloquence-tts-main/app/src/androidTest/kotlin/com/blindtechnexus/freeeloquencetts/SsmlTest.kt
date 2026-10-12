package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stripping SSML and other XML-like tags before the engine sees the text.
 *
 * The engine's own SSML reader is never turned on, so a tag would otherwise
 * be spoken -- or half spoken. What is checked is that a tag is taken out
 * whole, the words between tags stay, and the double spaces a removed tag
 * leaves behind are run back into one.
 */
@RunWith(AndroidJUnit4::class)
class SsmlTest {

	@Test
	fun aTagInAMiddleOfTextIsTakenOut() {
		assertEquals(
			"Hello world",
			Ssml.strip("Hello <break time=\"1s\"/> world")
		)
	}

	@Test
	fun surroundingTagsGoAndTheWordsStay() {
		assertEquals("Hello world", Ssml.strip("<speak>Hello world</speak>"))
	}

	@Test
	fun tagsRunTogetherAreTakenOut() {
		assertEquals("a c d", Ssml.strip("a<b>c</b>d"))
	}

	@Test
	fun textWithoutTagsIsUntouched() {
		assertEquals("no tags here", Ssml.strip("no tags here"))
	}

	@Test
	fun aMalformedTagIsStillTakenOutWhole() {
		assertEquals("3 4", Ssml.strip("3 < 5 > 4"))
	}

	@Test
	fun aStrayAngleWithNoCloseIsLeftForTheSymbols() {
		assertEquals("3 < 5", Ssml.strip("3 < 5"))
	}
}
