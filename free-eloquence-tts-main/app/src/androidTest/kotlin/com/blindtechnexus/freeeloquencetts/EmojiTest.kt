package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emoji and emoticons, read by name or not read at all.
 *
 * The engine's code set stops at Latin-1, so an emoji handed straight over
 * would be a gap. What is checked is that a known emoji or emoticon becomes
 * its name when reading is on, becomes nothing when it is off, and that one
 * the table does not know is left for [WesternText] to drop rather than
 * mangled here.
 */
@RunWith(AndroidJUnit4::class)
class EmojiTest {

	@Test
	fun anEmojiIsNamedWhenRead() {
		assertEquals("grinning face", Emoji.apply("😀", true))
		assertEquals("Hello grinning face world", Emoji.apply("Hello 😀 world", true))
	}

	@Test
	fun anEmojiIsDroppedWhenNotRead() {
		assertEquals("", Emoji.apply("😀", false))
		assertEquals("Hello world", Emoji.apply("Hello 😀 world", false))
	}

	@Test
	fun aTypedEmoticonIsNamed() {
		assertEquals("smiley face", Emoji.apply(":-)", true))
		assertEquals("heart", Emoji.apply("<3", true))
	}

	@Test
	fun aTypedEmoticonIsDroppedWhenNotRead() {
		assertEquals("", Emoji.apply(":-)", false))
	}

	@Test
	fun plainTextIsUntouched() {
		assertEquals("Just words.", Emoji.apply("Just words.", true))
		assertEquals("Just words.", Emoji.apply("Just words.", false))
	}

	@Test
	fun anEmojiTheTableDoesNotKnowIsLeftAlone() {
		assertEquals("🦄", Emoji.apply("🦄", true))
	}
}
