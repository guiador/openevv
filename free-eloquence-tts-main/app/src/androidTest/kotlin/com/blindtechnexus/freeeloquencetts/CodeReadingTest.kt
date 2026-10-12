package com.blindtechnexus.freeeloquencetts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Source code said as code, one switch at a time.
 *
 * What is checked is not that the words are the ones another synthesiser would
 * choose -- they are ours -- but the three things that are easy to get wrong
 * and hard to hear: that an operator is met as the whole of itself rather than
 * as two shorter ones, that prose is left alone by a pass that was written for
 * code, and that the parts are switches rather than one mode, so that wanting
 * the paths does not cost the pauses of the indentation.
 */
class CodeReadingTest {

	@Test
	fun camelCaseNamesBecomeWords() {
		assertEquals("get element by id", say("getElementById", camel = true, snake = true))
		assertEquals("parse http response", say("parseHTTPResponse", camel = true, snake = true))
		// An acronym on its own is a word, and taking it apart would spell it.
		assertEquals("HTTP", say("HTTP", camel = true, snake = true))
	}

	@Test
	fun snakeCaseNamesBecomeWords() {
		assertEquals("my total count", say("my_total_count", camel = true, snake = true))
		assertEquals("my_total_count", say("my_total_count", camel = true, snake = false))
	}

	@Test
	fun proseKeepsItsCapitals() {
		// The first letter of a word is the start of the word and not the
		// start of a part. Lowering it would take the capital off every
		// sentence this pass reached, and it reaches all of them.
		assertEquals("Hello world", say("Hello world", camel = true, snake = true))
	}

	@Test
	fun anOperatorIsMetAsTheWholeOfItself() {
		assertEquals("a not equals b", say("a != b", operators = true))
		assertEquals("x triple equals y", say("x === y", operators = true))
		assertEquals("i plus plus", say("i++", operators = true))
		assertEquals("a arrow b", say("a -> b", operators = true))
		assertEquals("std double colon vector", say("std::vector", operators = true))
	}

	@Test
	fun bracketsAndMarksAreNamed() {
		assertEquals(
			"if left paren x right paren left brace",
			say("if (x) {", braces = true)
		)
		assertEquals("a semicolon b", say("a; b", braces = true))
	}

	@Test
	fun aPathIsSaidAsAPath() {
		assertEquals(
			"path home slash user slash notes dot txt",
			say("/home/user/notes.txt", paths = true)
		)
		assertEquals(
			"path src slash main slash App dot kt",
			say("src/main/App.kt", paths = true)
		)
		// Two words with a slash between them are prose, and saying "slash"
		// in the middle of a sentence because they were joined would be worse
		// than the silence.
		assertEquals("and/or", say("and/or", paths = true))
	}

	@Test
	fun anAddressIsSaidAsAnAddress() {
		assertEquals(
			"url example dot com slash a slash b",
			say("https://www.example.com/a/b", urls = true, operators = true)
		)
	}

	@Test
	fun indentationIsACount() {
		assertEquals("indent 1 foo", say("\tfoo", indentation = true, operators = false))
		assertEquals("indent 4 foo", say("    foo", indentation = true, operators = false))
		// A tab is one and a space is one, because that is a count of the
		// characters and not a guess at the width of somebody's editor.
		assertEquals(
			"indent 1 foo\nindent 2 bar",
			say("\tfoo\n\t\tbar", indentation = true, operators = false)
		)
	}

	@Test
	fun nothingHappensWhenTheMasterSwitchIsOff() {
		val text = "if (a != b) { getElementById(); }"
		assertEquals(text, CodeReading.apply(text, CodeReading.OFF))
	}

	@Test
	fun indentationAndPathsDoNotUndoEachOther() {
		// The two switches meet on a line of a stack trace, and both have to
		// come out: the file is said as a path and the tab in front of it is
		// counted, because the indentation pass runs last and reads the
		// whitespace as it was written rather than as an earlier pass left it.
		assertEquals(
			"indent 1 at path com slash App dot kt",
			say("\tat com/App.kt", paths = true, indentation = true)
		)
	}

	private fun say(
		text: String,
		camel: Boolean = false,
		snake: Boolean = false,
		operators: Boolean = false,
		braces: Boolean = false,
		indentation: Boolean = false,
		paths: Boolean = false,
		urls: Boolean = false
	): String = CodeReading.apply(
		text,
		CodeReading.Options(
			camelCase = camel,
			snakeCase = snake,
			operators = operators,
			braces = braces,
			indentation = indentation,
			paths = paths,
			urls = urls
		)
	)
}
