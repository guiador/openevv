package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.charset.StandardCharsets

/**
 * The dictionaries a file was imported from, each kept as the file it was.
 *
 * What is checked is the shape of it: an import keeps the entries that parse
 * and reports what it did, the name on the screen is the file's own without
 * the suffix, Latin-1 decodes as Latin-1, and every change -- add, edit,
 * append, delete -- lands in the file it names. What is made here is taken
 * away afterwards, so the app's own dictionaries never hear of the test.
 */
@RunWith(AndroidJUnit4::class)
class ExtraDictionariesTest {

	private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

	private val made = mutableListOf<String>()

	@After
	fun takeAwayWhatWasMade() {
		for (name in made) {
			try {
				ExtraDictionaries.delete(context, name)
			} catch (e: Exception) {
			}
		}
		made.clear()
	}

	@Test
	fun anImportKeepsWhatParsesAndReportsIt() {
		val result = import("test-words.dict", "hello\tworld\nfoo\tbar\nnonsense\n\t\n")
		assertNotNull(result)
		assertEquals("test-words", result!!.name)
		assertEquals(2, result.kept)
		assertEquals(2, result.total)
		assertFalse(result.truncated)
		assertEquals(listOf("hello" to "world", "foo" to "bar"), ExtraDictionaries.entries(context, "test-words"))
	}

	@Test
	fun anImportWithNothingInItKeepsNothing() {
		assertNull(import("test-empty.dict", "nonsense\n\n"))
		assertTrue(ExtraDictionaries.list(context).none { it.name == "test-empty" })
	}

	@Test
	fun latin1DecodesAsLatin1() {
		val bytes = "caf\u00e9\tsay\n".toByteArray(StandardCharsets.ISO_8859_1)
		val result = ExtraDictionaries.importStream(context, bytes.inputStream(), "test-latin.dict")
		assertNotNull(result)
		made.add(result!!.name)
		assertEquals(listOf("caf\u00e9" to "say"), ExtraDictionaries.entries(context, result.name))
	}

	@Test
	fun theSecondFileTakesANumberedName() {
		val first = import("test-twice.dict", "a\tb\n")
		val second = import("test-twice.dict", "c\td\n")
		assertNotNull(first)
		assertNotNull(second)
		assertEquals("test-twice", first!!.name)
		assertEquals("test-twice (2)", second!!.name)
	}

	@Test
	fun countsAndSearchesReadTheFileWithoutKeepingIt() {
		import("test-search.dict", "hello\tworld\nhelp\taid\nbye\tnow\n")
		val info = ExtraDictionaries.list(context).first { it.name == "test-search" }
		assertEquals(3, ExtraDictionaries.count(info))
		assertEquals(
			listOf("hello" to "world", "help" to "aid"),
			ExtraDictionaries.search(context, "test-search", "hel")
		)
		assertEquals(
			listOf("bye" to "now"),
			ExtraDictionaries.search(context, "test-search", "NOW")
		)
	}

	@Test
	fun wordsAreAddedEditedAndTakenAway() {
		import("test-edits.dict", "a\tb\n")
		assertTrue(ExtraDictionaries.add(context, "test-edits", "c", "d"))
		assertTrue(ExtraDictionaries.replace(context, "test-edits", "a", "a", "z"))
		assertEquals(
			listOf("c" to "d", "a" to "z"),
			ExtraDictionaries.entries(context, "test-edits").sortedBy { it.first }
		)
		assertTrue(ExtraDictionaries.remove(context, "test-edits", "c"))
		assertEquals(listOf("a" to "z"), ExtraDictionaries.entries(context, "test-edits"))
	}

	@Test
	fun anImportAppendsAfterTheEntriesThere() {
		import("test-append.dict", "a\tb\n")
		val result = ExtraDictionaries.appendImport(
			context,
			"test-append",
			"c\td\n".byteInputStream()
		)
		assertNotNull(result)
		assertEquals(1, result!!.kept)
		assertEquals(
			listOf("a" to "b", "c" to "d"),
			ExtraDictionaries.entries(context, "test-append")
		)
	}

	@Test
	fun aFilePastTheLimitIsTruncatedNotCrashed() {
		// The cap is what a file keeps rather than what an import reads --
		// see [ExtraDictionaries.MAX_ENTRIES_PER_FILE] -- and it is a
		// parameter here so that the test says the same thing about the
		// truncation without writing a million lines to say it. What is
		// checked is the promise: the file stops at the cap, the rest is
		// counted rather than kept, and the import says so instead of
		// throwing.
		val lines = StringBuilder()
		for (i in 0 until 120) lines.append("w$i\ts$i\n")
		val result = ExtraDictionaries.importStream(
			context,
			lines.toString().byteInputStream(),
			"test-huge.dict",
			cap = 100
		)
		assertNotNull(result)
		made.add(result!!.name)
		assertEquals(100, result.kept)
		assertEquals(120, result.total)
		assertTrue("the import did not say it was truncated", result.truncated)
		assertTrue("the truncated file is longer than its cap", result.bytes < lines.length)
	}

	@Test
	fun theKeyMovesWhenAFileDoes() {
		val before = ExtraDictionaries.combinedKey(context)
		import("test-key.dict", "a\tb\n")
		val after = ExtraDictionaries.combinedKey(context)
		assertTrue("the key did not move: $before then $after", before != after)
	}

	private fun import(display: String, content: String): ExtraDictionaries.Result? {
		val result = ExtraDictionaries.importStream(
			context,
			content.byteInputStream(),
			display
		)
		if (result != null) made.add(result.name)
		return result
	}
}
