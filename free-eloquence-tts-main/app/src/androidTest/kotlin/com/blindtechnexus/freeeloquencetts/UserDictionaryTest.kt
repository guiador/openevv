package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one file the dictionary manager keeps.
 *
 * What is checked is the round trip: a word added is a word read back, an
 * added word replaces an older one of the same spelling, a removal and a
 * replacement each do what they say, a merge brings in only what is new, and
 * a text of entries is parsed the way a shared file is.
 */
@RunWith(AndroidJUnit4::class)
class UserDictionaryTest {

	private lateinit var context: Context

	@Before
	fun setUp() {
		context = InstrumentationRegistry.getInstrumentation().targetContext
		UserDictionary.file(context).delete()
	}

	@After
	fun tearDown() {
		UserDictionary.file(context).delete()
	}

	@Test
	fun aWordAddedIsAWordReadBack() {
		assertTrue(UserDictionary.add(context, "DOJ", "dee oe jeigh"))
		assertEquals(listOf("DOJ" to "dee oe jeigh"), UserDictionary.words(context))
	}

	@Test
	fun addingTheSameWordReplacesIt() {
		UserDictionary.add(context, "DOJ", "dee oe jeigh")
		UserDictionary.add(context, "doj", "dee oh jay")
		val words = UserDictionary.words(context)
		assertEquals(1, words.size)
		assertEquals("doj" to "dee oh jay", words[0])
	}

	@Test
	fun removingTakesTheWordAway() {
		UserDictionary.add(context, "DOJ", "dee oe jeigh")
		UserDictionary.add(context, "DOS", "`[das]")
		assertTrue(UserDictionary.remove(context, "DOJ"))
		assertEquals(listOf("DOS" to "`[das]"), UserDictionary.words(context))
	}

	@Test
	fun replacingSwapsTheWord() {
		UserDictionary.add(context, "DOJ", "dee oe jeigh")
		assertTrue(UserDictionary.replace(context, "DOJ", "DOS", "`[das]"))
		assertEquals(listOf("DOS" to "`[das]"), UserDictionary.words(context))
	}

	@Test
	fun mergingBringsInOnlyWhatIsNew() {
		UserDictionary.add(context, "DOJ", "dee oe jeigh")
		val added = UserDictionary.merge(
			context,
			listOf("DOJ" to "overwritten", "ACLU" to "ay cea ell yue")
		)
		assertEquals("the known word was counted as new", 1, added)
		val words = UserDictionary.words(context)
		assertEquals(2, words.size)
		assertTrue(words.contains("ACLU" to "ay cea ell yue"))
		assertFalse(words.contains("DOJ" to "dee oe jeigh"))
	}

	@Test
	fun aSharedTextParses() {
		assertEquals(
			listOf("DOJ" to "dee oe jeigh", "DOS" to "`[das]"),
			UserDictionary.parse("DOJ\tdee oe jeigh\r\nDOS\t`[das]\r\nno tab here\r\n   \r\n")
		)
	}

	@Test
	fun removingIgnoresCase() {
		UserDictionary.add(context, "DOJ", "dee oe jeigh")
		assertTrue(UserDictionary.remove(context, "doj"))
		assertEquals(emptyList<Pair<String, String>>(), UserDictionary.words(context))
	}

	@Test
	fun aWordIsReplacedWithoutRegardToCase() {
		val entries = listOf("DOJ" to "dee oe jeigh")
		assertEquals("dee oe jeigh", UserDictionary.apply("DOJ", entries))
		assertEquals("dee oe jeigh", UserDictionary.apply("doj", entries))
		assertEquals("dee oe jeigh", UserDictionary.apply("Doj", entries))
		assertEquals(
			"See dee oe jeigh next.",
			UserDictionary.apply("See DOJ next.", entries)
		)
	}

	@Test
	fun aWordDoesNotMatchInsideAnother() {
		val entries = listOf("cat" to "feline")
		assertEquals("category", UserDictionary.apply("category", entries))
		assertEquals("bobcat", UserDictionary.apply("bobcat", entries))
		assertEquals("a feline sat", UserDictionary.apply("a cat sat", entries))
	}

	@Test
	fun aPhraseIsReplaced() {
		val entries = listOf("new york" to "the city")
		assertEquals("the city", UserDictionary.apply("New York", entries))
		assertEquals("new yorker", UserDictionary.apply("new yorker", entries))
	}

	@Test
	fun aPhoneticReplacementIsLeftForTheEngine() {
		val entries = listOf("DOS" to "`[das]")
		assertEquals("`[das]", UserDictionary.apply("dos", entries))
	}

	@Test
	fun ordinaryTextIsUntouched() {
		val entries = listOf("DOJ" to "dee oe jeigh")
		val text = "Hello from Eloquence."
		assertTrue("the original string should come back", UserDictionary.apply(text, entries) === text)
	}
}
