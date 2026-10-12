package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How a run of digits is regrouped, which is the whole of the number
 * processing: the engine has no setting for it, so the modes are made out of
 * spaces. What is checked is the strong rule -- a run of up to five digits
 * is one number, a separator or a decimal point inside a number means the
 * number whole, and a run of six or more is grouped from the left -- and
 * that anything that is not a run of the ten digits is left alone.
 */
@RunWith(AndroidJUnit4::class)
class NumbersTest {

	@Test
	fun asIsLeavesTheNumberAlone() {
		assertEquals("123456", Numbers.apply("123456", Numbers.AS_IS))
		assertEquals("3.14", Numbers.apply("3.14", Numbers.AS_IS))
	}

	@Test
	fun theUsersExamples() {
		assertEquals("9 8 5 2 5 6", Numbers.apply("985256", Numbers.DIGITS))
		assertEquals("98 52 56", Numbers.apply("985256", Numbers.PAIRS))
		assertEquals("985 256", Numbers.apply("985256", Numbers.TRIPLETS))
	}

	@Test
	fun aShortRunIsOneNumberInEveryMode() {
		for (mode in listOf(Numbers.DIGITS, Numbers.PAIRS, Numbers.TRIPLETS)) {
			assertEquals("1234", Numbers.apply("1234", mode))
			assertEquals("12345", Numbers.apply("12345", mode))
			assertEquals("hello 42", Numbers.apply("hello 42", mode))
			assertEquals("The year is 2026", Numbers.apply("The year is 2026", mode))
			assertEquals("1500", Numbers.apply("1500", mode))
			assertEquals("3.14", Numbers.apply("3.14", mode))
		}
	}

	@Test
	fun aTextWithNothingToDoIsTheSameString() {
		val text = "Nothing here but 2026 and 12345 and 3.14"
		assertSame(text, Numbers.apply(text, Numbers.DIGITS))
	}

	@Test
	fun digitsSpaceALongRun() {
		assertEquals("1 2 3 4 5 6", Numbers.apply("123456", Numbers.DIGITS))
		assertEquals("call 1 2 3 4 5 6 7 now", Numbers.apply("call 1234567 now", Numbers.DIGITS))
	}

	@Test
	fun aSeparatorMeansTheNumberWholeInEveryMode() {
		assertEquals("10,000", Numbers.apply("10,000", Numbers.DIGITS))
		assertEquals("1,000,000", Numbers.apply("1,000,000", Numbers.DIGITS))
		assertEquals("1,000,000", Numbers.apply("1,000,000", Numbers.PAIRS))
		assertEquals("1,000,000", Numbers.apply("1,000,000", Numbers.TRIPLETS))
		assertEquals("10.000.000", Numbers.apply("10.000.000", Numbers.DIGITS))
		assertEquals("1,234.5", Numbers.apply("1,234.5", Numbers.DIGITS))
		assertEquals("September 5, 2026", Numbers.apply("September 5, 2026", Numbers.DIGITS))
	}

	@Test
	fun aDecimalIsAQuantityWhole() {
		assertEquals("3.1415926", Numbers.apply("3.1415926", Numbers.DIGITS))
		assertEquals("985256.78", Numbers.apply("985256.78", Numbers.DIGITS))
		assertEquals("985256,78", Numbers.apply("985256,78", Numbers.PAIRS))
	}

	@Test
	fun pairsTakeTwoFromTheLeft() {
		assertEquals("12 34 56", Numbers.apply("123456", Numbers.PAIRS))
		// Odd length keeps the short group at the end, telephone style.
		assertEquals("12 34 56 7", Numbers.apply("1234567", Numbers.PAIRS))
		assertEquals("98 76 54 32 10", Numbers.apply("9876543210", Numbers.PAIRS))
	}

	@Test
	fun tripletsTakeThreeFromTheLeft() {
		assertEquals("123 456", Numbers.apply("123456", Numbers.TRIPLETS))
		assertEquals("123 456 7", Numbers.apply("1234567", Numbers.TRIPLETS))
		assertEquals("123 456 789", Numbers.apply("123456789", Numbers.TRIPLETS))
	}

	@Test
	fun aGroupThatOpensWithAZeroIsSaidDigitByDigit() {
		assertEquals("12 0 4 56", Numbers.apply("120456", Numbers.PAIRS))
		assertEquals("0 1 2 345", Numbers.apply("012345", Numbers.TRIPLETS))
	}

	@Test
	fun punctuationSplitsTheRuns() {
		assertEquals("12:34", Numbers.apply("12:34", Numbers.PAIRS))
		assertEquals("1 2 3 4 5 6:78", Numbers.apply("123456:78", Numbers.DIGITS))
	}

	@Test
	fun theDiallingPlusIsKept() {
		assertEquals("+ 911", Numbers.apply("+911", Numbers.DIGITS))
		assertEquals("+ 9 7 7 9 8 4 1", Numbers.apply("+9779841", Numbers.DIGITS))
	}

	@Test
	fun anAnnotationIsCarriedThrough() {
		assertEquals("`vbst+6 A `vbst-6 1 2 3 4 5 6", Numbers.apply("`vbst+6 A `vbst-6 123456", Numbers.DIGITS))
	}

	@Test
	fun anUnknownModeIsAsIs() {
		assertEquals("123456", Numbers.apply("123456", 42))
		assertEquals(Numbers.AS_IS, Numbers.clamp(-1))
	}

	@Test
	fun theWordsAroundARunAreLeftAlone() {
		assertEquals("The year is 2026", Numbers.apply("The year is 2026", Numbers.DIGITS))
		assertEquals("call 12", Numbers.apply("call 12", Numbers.TRIPLETS))
		assertEquals("id 1 2 3 4 5 6 done", Numbers.apply("id 123456 done", Numbers.DIGITS))
	}
}
