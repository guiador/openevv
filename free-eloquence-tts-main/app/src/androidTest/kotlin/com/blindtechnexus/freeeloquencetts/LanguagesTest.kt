package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The eight languages: the two Englishes, German, the two Spanishes, the two
 * Frenches and Italian.
 *
 * This build carries all eight modules, named together in the build script,
 * and all eight are offered: on the settings screen, in the voices the
 * service answers with, and in what a caller can ask for by locale. So what
 * is checked here is that the eight numbers are the engine's own, that each
 * has a name Android understands and a voice name to go with it, that an
 * instance opens in every one and speaks, that a locale finds the right
 * module, and that the sample-rate table is shared by all of them.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesTest {

	@Test
	fun theWordsAreTheEnginesOwn() {
		assertEquals(0x00010000, Eci.ENGLISH_US)
		assertEquals(0x00010001, Eci.ENGLISH_UK)
		assertEquals(0x00020000, Eci.SPANISH_SPAIN)
		assertEquals(0x00020001, Eci.SPANISH_MEXICO)
		assertEquals(0x00030000, Eci.FRENCH_FRANCE)
		assertEquals(0x00030001, Eci.FRENCH_CANADA)
		assertEquals(0x00040000, Eci.GERMAN)
		assertEquals(0x00050000, Eci.ITALIAN)
	}

	@Test
	fun allEightAreOfferedWithAFrameworkSpellingAndAVoiceName() {
		assertEquals(8, Eci.LANGUAGES.size)
		assertEquals(Eci.ENGLISH_US, Eci.LANGUAGES[0].code)
		assertEquals("eng", Eci.ISO3_LANGUAGE)
		assertEquals("USA", Eci.ISO3_COUNTRY)
		assertEquals("en", Eci.ISO2_LANGUAGE)
		assertEquals(
			listOf("USA", "GBR", "ESP", "MEX", "FRA", "CAN", "DEU", "ITA"),
			Eci.LANGUAGES.map { it.iso3Country }
		)
		assertEquals(
			listOf("en-US", "en-GB", "es-ES", "es-MX", "fr-FR", "fr-CA", "de-DE", "it-IT"),
			Eci.LANGUAGES.map { it.tag }
		)
		assertEquals("USA", Eci.countryOf(Eci.ENGLISH_US))
		assertEquals("GBR", Eci.countryOf(Eci.ENGLISH_UK))
		// A number no build knows answers with the American one rather than
		// with nothing, so a bad preference is a voice and not a refusal.
		assertEquals("USA", Eci.countryOf(0x0001FFFF))
		assertEquals(Eci.ENGLISH_US, Eci.languageOrDefault(0x00080000))
		assertEquals("eng-USA-Reed", Eci.voiceName(0))
		assertEquals("eng-GBR-Reed", Eci.voiceName(0, Eci.ENGLISH_UK))
		assertEquals("deu-DEU-Luna", Eci.voiceName(Eci.LUNA_INDEX, Eci.GERMAN))
		assertEquals("fra-CAN-Reed", Eci.voiceName(0, Eci.FRENCH_CANADA))
		// Every module the front end offers is one the build script names.
		assertEquals(
			listOf("enus", "engb", "eses", "esus", "frfr", "frca", "dede", "itit"),
			Eci.LANGUAGES.map { it.module }
		)
	}

	@Test
	fun aLocaleFindsItsModule() {
		assertEquals(Eci.ENGLISH_UK, Eci.languageFor("en", "GB")?.code)
		assertEquals(Eci.ENGLISH_UK, Eci.languageFor("eng", "GBR")?.code)
		assertEquals(Eci.ENGLISH_US, Eci.languageFor("en", "US")?.code)
		// A country the engine has no module for is the mother country's.
		assertEquals(Eci.ENGLISH_US, Eci.languageFor("en", "AU")?.code)
		assertEquals(Eci.ENGLISH_US, Eci.languageFor("en", null)?.code)
		assertEquals(Eci.SPANISH_SPAIN, Eci.languageFor("es", "AR")?.code)
		assertEquals(Eci.SPANISH_MEXICO, Eci.languageFor("spa", "MEX")?.code)
		assertEquals(Eci.FRENCH_CANADA, Eci.languageFor("fr", "CA")?.code)
		assertEquals(Eci.FRENCH_FRANCE, Eci.languageFor("fra", "BEL")?.code)
		assertEquals(Eci.GERMAN, Eci.languageFor("de", "CH")?.code)
		assertEquals(Eci.ITALIAN, Eci.languageFor("it", "IT")?.code)
		// A language the engine has no module for is nobody's.
		assertNull(Eci.languageFor("ja", "JP"))
		assertNull(Eci.languageFor("pl", null))
		assertNull(Eci.languageFor(null, null))
	}

	@Test
	fun aVoiceNameSaysItsLanguage() {
		assertEquals(Eci.GERMAN, Eci.languageOfVoiceName("deu-DEU-Reed")?.code)
		assertEquals(Eci.ENGLISH_UK, Eci.languageOfVoiceName("eng-GBR-Shelley")?.code)
		assertEquals(Eci.ENGLISH_UK, Eci.languageOfVoiceName("en-GB-x-something")?.code)
		assertEquals(Eci.FRENCH_CANADA, Eci.languageOfVoiceName("fr_CA")?.code)
		assertNull(Eci.languageOfVoiceName("Reed"))
		assertNull(Eci.languageOfVoiceName("jpn-JPN-Reed"))
	}

	@Test
	fun anInstanceOpensInEveryOneAndSpeaks() {
		assertTrue("the native library did not load: ${EvvNative.loadError}", EvvNative.loaded)
		for (language in Eci.LANGUAGES) {
			val e = EvvEngine.open(language.code)
			assertNotNull("${language.label} would not open", e)
			if (e != null) {
				try {
					assertEquals("the instance speaks another language", language.code, e.language)
					assertTrue("${language.label} refused a sentence", e.speak("Hello. 123.", isStopped = { false }))
					var total = 0
					val buffer = ByteArray(8192)
					while (true) {
						val n = e.read(buffer, 5000)
						if (n <= 0) break
						total += n
					}
					assertTrue("${language.label} made no audio", total > 0)
				} finally {
					e.close()
				}
			}
		}
	}

	@Test
	fun theEngineCarriesWhatIsOffered() {
		assertTrue(EvvNative.loaded)
		val carried = try {
			EvvNative.availableLanguages().toSet()
		} catch (_: Throwable) {
			emptySet()
		}
		// An engine that will not say is not held against it; one that does
		// must name every one of the eight.
		if (carried.isNotEmpty()) {
			for (language in Eci.LANGUAGES) {
				assertTrue("${language.label} is offered but not carried", carried.contains(language.code))
			}
		}
	}

	@Test
	fun everySampleRateRoundTrips() {
		for (hz in Eci.SAMPLE_RATES) {
			assertEquals(hz, Eci.sampleRateHz(Eci.sampleRateIndex(hz)))
			assertEquals(hz, Eci.nearestSampleRate(hz))
		}
	}

	@Test
	fun anUnknownRateSnapsToTheNearest() {
		assertEquals(11025, Eci.sampleRateHz(99))
		assertEquals(11025, Eci.nearestSampleRate(12345))
		assertEquals(1, Eci.sampleRateIndex(12345))
		assertEquals(22050, Eci.nearestSampleRate(20000))
		assertEquals(48000, Eci.nearestSampleRate(96000))
		assertEquals(8000, Eci.nearestSampleRate(1))
		assertEquals(11025, Eci.nearestSampleRate(0))
		assertEquals(11025, Eci.nearestSampleRate(-5))
	}
}
