package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The expansions a person writes themselves.
 *
 * Two halves. The first is the matching, which is where the three settings
 * earn their keep: a whole word only, from the start of a word, or anywhere at
 * all -- because `st` is a street and `Dr` is a doctor and `xx` is a placeholder
 * in a template, and those are three different things that a fourth setting,
 * case, cuts across. The second is the file, because a list that does not come
 * back out of the file it went into is a list the person has to write twice.
 */
@RunWith(AndroidJUnit4::class)
class AbbreviationsTest {

	private lateinit var context: Context

	@Before
	fun setUp() {
		context = InstrumentationRegistry.getInstrumentation().targetContext
		Abbreviations.file(context).delete()
	}

	@After
	fun tearDown() {
		Abbreviations.file(context).delete()
	}

	// ---- the matching ----------------------------------------------------

	@Test
	fun aWholeWordIsTheOnlyThingAWholeWordMatches() {
		val one = listOf(entry("st", "street", Abbreviations.WHOLE_WORD))
		assertEquals("street", say(one, "st"))
		assertEquals("the street", say(one, "the st"))
		assertEquals("a street. b", say(one, "a st. b"))
		// Inside a longer word it is not the abbreviation, and the setting is
		// the one that says so.
		assertEquals("street", say(one, "street"))
		assertEquals("first street", say(one, "first st"))
	}

	@Test
	fun fromTheStartOfAWordIsFromTheStartOfAWord() {
		val one = listOf(entry("dr", "doctor", Abbreviations.STARTS_WITH))
		assertEquals("doctor smith", say(one, "dr smith"))
		// Not at the start of one.
		assertEquals("address", say(one, "address"))
	}

	@Test
	fun anywhereMeansAnywhere() {
		val one = listOf(entry("xx", "eks eks", Abbreviations.ANYWHERE))
		assertEquals("aeks eksb", say(one, "axxb"))
	}

	@Test
	fun caseIsItsOwnSetting() {
		val fussy = listOf(entry("DOJ", "dee oe jeigh", Abbreviations.WHOLE_WORD, false))
		assertEquals("dee oe jeigh", say(fussy, "DOJ"))
		assertEquals("doj", say(fussy, "doj"))
		assertEquals("Doj", say(fussy, "Doj"))
		val easy = listOf(entry("DOJ", "dee oe jeigh", Abbreviations.WHOLE_WORD, true))
		assertEquals("dee oe jeigh", say(easy, "doj"))
		assertEquals("dee oe jeigh", say(easy, "Doj"))
	}

	@Test
	fun theLongerKeyWins() {
		// One pass rather than one per entry, so a shorter key cannot take the
		// characters a longer one wanted.
		val two = listOf(
			entry("st", "street", Abbreviations.WHOLE_WORD),
			entry("str", "string", Abbreviations.WHOLE_WORD)
		)
		assertEquals("string", say(two, "str"))
		assertEquals("street", say(two, "st"))
	}

	@Test
	fun aPhraseIsAKeyToo() {
		val one = listOf(entry("new york", "the city", Abbreviations.WHOLE_WORD))
		assertEquals("the city", say(one, "New York"))
		assertEquals("new yorker", say(one, "new yorker"))
	}

	@Test
	fun textWithNothingInItIsTextWithNothingDoneToIt() {
		assertEquals("hello there", say(listOf(entry("zz", "zee zee", Abbreviations.WHOLE_WORD)), "hello there"))
		assertEquals("hello there", Abbreviations.apply("hello there", emptyList()))
	}

	// ---- the file --------------------------------------------------------

	@Test
	fun anEntryAddedIsAnEntryReadBack() {
		assertTrue(
			Abbreviations.add(
				context,
				Abbreviations.Entry("st", "street", Abbreviations.WHOLE_WORD, true)
			)
		)
		val back = Abbreviations.entries(context)
		assertEquals(1, back.size)
		assertEquals("st", back[0].key)
		assertEquals("street", back[0].say)
		assertEquals(Abbreviations.WHOLE_WORD, back[0].match)
		assertTrue(back[0].ignoreCase)
	}

	@Test
	fun everySettingSurvivesTheRoundTrip() {
		assertTrue(Abbreviations.write(context, Abbreviations.MATCHES.map { mode ->
			Abbreviations.Entry("k$mode", "said $mode", mode, mode != Abbreviations.ANYWHERE)
		}))
		val back = Abbreviations.entries(context)
		assertEquals(3, back.size)
		assertEquals(Abbreviations.MATCHES.sorted(), back.map { it.match }.sorted())
	}

	@Test
	fun anEntryAddedTwiceIsOneEntry() {
		val one = Abbreviations.Entry("st", "street")
		assertTrue(Abbreviations.add(context, one))
		assertTrue(Abbreviations.add(context, one))
		assertEquals(1, Abbreviations.entries(context).size)
	}

	@Test
	fun aRemovalAndAReplacementEachDoWhatTheySay() {
		assertTrue(Abbreviations.add(context, Abbreviations.Entry("st", "street")))
		val made = Abbreviations.entries(context)[0]
		assertTrue(Abbreviations.replace(context, made, Abbreviations.Entry("st", "saint")))
		assertEquals("saint", Abbreviations.entries(context)[0].say)
		assertTrue(Abbreviations.remove(context, Abbreviations.entries(context)[0]))
		assertEquals(0, Abbreviations.entries(context).size)
	}

	@Test
	fun aWriteMovesTheVersionOn() {
		val before = Abbreviations.version
		assertTrue(Abbreviations.add(context, Abbreviations.Entry("st", "street")))
		assertNotEquals(before, Abbreviations.version)
	}

	@Test
	fun aSharedTextIsParsedTheWayTheFileIs() {
		// The columns the file itself writes: w, s or a for how the key is
		// matched, and i or s for whether case is kept.
		val parsed = Abbreviations.parse("st\tstreet\nDr\tdoctor\ts\nxx\teks\ta\ts")
		assertEquals(3, parsed.size)
		assertEquals("st", parsed[0].key)
		assertEquals(Abbreviations.WHOLE_WORD, parsed[0].match)
		assertTrue(parsed[0].ignoreCase)
		assertEquals(Abbreviations.STARTS_WITH, parsed[1].match)
		assertEquals(Abbreviations.ANYWHERE, parsed[2].match)
		assertFalse(parsed[2].ignoreCase)
	}

	@Test
	fun whatIsWrittenOutIsWhatIsReadBack() {
		val entries = listOf(
			Abbreviations.Entry("st", "street", Abbreviations.WHOLE_WORD, true),
			Abbreviations.Entry("dr", "doctor", Abbreviations.STARTS_WITH, false)
		)
		val parsed = Abbreviations.parse(Abbreviations.format(entries))
		assertEquals(entries.size, parsed.size)
		for (at in entries.indices) {
			assertTrue(entries[at].sameAs(parsed[at]))
		}
	}

	@Test
	fun aBlankLineIsNotAnEntry() {
		assertEquals(0, Abbreviations.parse("\n\n   \n").size)
		assertEquals(0, Abbreviations.parse("no tab here").size)
		assertEquals(1, Abbreviations.parse("st\tstreet\n").size)
	}

	private fun entry(key: String, say: String, match: Int, ignoreCase: Boolean = true) =
		Abbreviations.Entry(key, say, match, ignoreCase)

	private fun say(entries: List<Abbreviations.Entry>, text: String): String =
		Abbreviations.apply(text, entries)
}
