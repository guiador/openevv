package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The engine's answers that need no engine: what carries nothing to say,
 * which the service skips rather than handing over, since an utterance of
 * nothing is a wait for audio that is never coming.
 */
@RunWith(AndroidJUnit4::class)
class EngineStaticTest {

	@Test
	fun blankSpeechIsBlank() {
		assertTrue(EvvEngine.isBlankSpeech(""))
		assertTrue(EvvEngine.isBlankSpeech("   "))
		assertTrue(EvvEngine.isBlankSpeech("`p200"))
		assertTrue(EvvEngine.isBlankSpeech("  `vbst+6  `p100 "))
	}

	@Test
	fun wordsAreNotBlank() {
		assertFalse(EvvEngine.isBlankSpeech("hello"))
		assertFalse(EvvEngine.isBlankSpeech("`p200 hello"))
		assertFalse(EvvEngine.isBlankSpeech("  a  "))
	}
}
