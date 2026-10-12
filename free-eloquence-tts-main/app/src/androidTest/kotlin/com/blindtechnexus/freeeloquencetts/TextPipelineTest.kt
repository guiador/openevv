package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one road all text takes, in the order the engine must see it.
 *
 * Each piece has a test of its own; what is checked here is the order, which
 * is the thing a test of each piece in isolation would miss. The fixes keep a
 * grouped thousand grouped, so the regrouping sees the comma and reads the
 * number whole; the capital annotation is added after the fixes, so nothing
 * walks into it; and the symbols run last, which is why a backtick annotation
 * survives them.
 */
@RunWith(AndroidJUnit4::class)
class TextPipelineTest {

	@Test
	fun ssmlComesOffBeforeAnythingElse() {
		assertEquals("a c", apply("a <b> c", ssml = true))
		assertEquals("a <b> c", apply("a <b> c", ssml = false))
	}

	@Test
	fun anEmojiIsNamedBeforeTheFixesSeeIt() {
		assertEquals("grinning face", apply("😀", emoji = true))
		assertEquals("", apply("😀", emoji = false))
	}

	@Test
	fun aGroupedThousandIsReadWhole() {
		assertEquals("1,000,000", apply("1,000,000", numbers = Numbers.DIGITS))
		assertEquals("10,000", apply("10,000", numbers = Numbers.PAIRS))
	}

	@Test
	fun theCapitalAnnotationSurvivesTheSymbols() {
		assertEquals(
			"`vbst+6 A `vbst-6",
			apply("A", capitals = CapitalLetters.PITCH, symbols = Symbols.ALL)
		)
	}

	@Test
	fun plainTextComesThroughTheWholeRoad() {
		assertEquals("Hello from Eloquence.", apply("Hello from Eloquence."))
	}

	@Test
	fun aDictionaryWordIsReplacedBeforeTheFixes() {
		assertEquals(
			"dee oe jeigh",
			apply("doj", words = listOf("DOJ" to "dee oe jeigh"))
		)
	}

	private fun apply(
		text: String,
		ssml: Boolean = true,
		emoji: Boolean = true,
		numbers: Int = Numbers.AS_IS,
		capitals: Int = CapitalLetters.NONE,
		symbols: Int = Symbols.SOME,
		custom: String = "",
		words: List<Pair<String, String>> = emptyList()
	): String {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val settings = Settings(context)
		settings.stripSsml = ssml
		settings.readEmoji = emoji
		settings.numberMode = numbers
		settings.capitalMode = capitals
		settings.symbolMode = symbols
		settings.customSymbols = custom
		return TextPipeline.apply(text, settings, words)
	}
}
