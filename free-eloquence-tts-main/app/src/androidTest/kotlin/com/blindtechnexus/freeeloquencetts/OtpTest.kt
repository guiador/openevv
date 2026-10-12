package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which runs of digits are one-time codes and get said a digit at a time,
 * and -- the harder half -- which are not. A year, an order number, a
 * price, a telephone number and a time all look like a code from a distance
 * and must come through as they were written.
 */
@RunWith(AndroidJUnit4::class)
class OtpTest {

	@Test
	fun theUsersExample() {
		assertEquals(
			"Your verification code is 4 9 2 8 0 1. Do not share it.",
			Otp.apply("Your verification code is 492801. Do not share it.")
		)
	}

	@Test
	fun theWordsAfterTheDigitsCountToo() {
		assertEquals("1 2 3 4 5 6 is your Amazon OTP.", Otp.apply("123456 is your Amazon OTP."))
		assertEquals("<#> 7 7 2 9 1 0 is your login code", Otp.apply("<#> 772910 is your login code"))
	}

	@Test
	fun groupsAreKeptAsGroups() {
		assertEquals("Code: 1 2 3, 4 5 6", Otp.apply("Code: 123 456"))
		assertEquals("Your PIN is 1 2 3 4, 5 6 7 8", Otp.apply("Your PIN is 1234-5678"))
	}

	@Test
	fun fourAndEightDigitCodesAreCodes() {
		assertEquals("Your OTP is 4 8 1 5", Otp.apply("Your OTP is 4815"))
		assertEquals("Verification code 1 2 3 4 5 6 7 8", Otp.apply("Verification code 12345678"))
	}

	@Test
	fun otherLanguages() {
		assertEquals("Ihr Bestätigungscode lautet 5 5 1 2 0 9", Otp.apply("Ihr Bestätigungscode lautet 551209"))
		assertEquals("Tu código de verificación es 3 3 0 1 9 8", Otp.apply("Tu código de verificación es 330198"))
		assertEquals("Votre code de vérification est 9 0 4 1 7 7", Otp.apply("Votre code de vérification est 904177"))
		assertEquals("Il tuo codice di verifica è 2 7 8 0 3 5", Otp.apply("Il tuo codice di verifica è 278035"))
	}

	@Test
	fun aMessageThatIsOnlyTheCodeIsACode() {
		assertEquals("4 9 2 8 0 1", Otp.apply("492801"))
	}

	@Test
	fun whatIsNotACode() {
		for (text in listOf(
			"The year is 2026",
			"Your order 123456 has shipped",
			"Invoice 48213 total $1234.50",
			"Call 555 1234 or 9779841000000",
			"Meet at 1400 on 25.12.2026",
			"Battery at 1500 mAh, 2048 MB free",
			"Version 1.2345 build 20260101",
			"Account 12345678 balance 5000"
		)) {
			assertSame(text, Otp.apply(text))
		}
	}

	@Test
	fun aLongerRunIsNeverACode() {
		assertSame("Your code is 1234567890", Otp.apply("Your code is 1234567890"))
	}

	@Test
	fun theScoreIsWhatTheTestsSay() {
		val text = "Your verification code is 492801. Do not share it."
		val found = Otp.find(text)
		assertEquals(1, found.size)
		assertTrue(Otp.scoreOf(text, found[0]) >= Otp.THRESHOLD)
	}

	@Test
	fun theWholeRoadSpellsACodeInEveryNumberMode() {
		val s = Settings(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)
		s.readOtpDigits = true
		for (mode in Numbers.MODES) {
			s.numberMode = mode
			val out = TextPipeline.apply("Your OTP is 492801", s)
			assertTrue(out, out.contains("4 9 2 8 0 1"))
		}
		s.readOtpDigits = false
		s.numberMode = Numbers.AS_IS
		assertTrue(TextPipeline.apply("Your OTP is 492801", s).contains("492801"))
	}
}
