package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The audio generator's refusals, which are the paths that never reach the
 * engine: nothing to say, and more than a generation takes. Both answer
 * nothing, on the main thread, without opening anything.
 */
@RunWith(AndroidJUnit4::class)
class AudioGeneratorTest {

	@Test
	fun nothingToSayGeneratesNothing() {
		assertNull(render("   "))
	}

	@Test
	fun moreThanAGenerationTakesIsRefused() {
		assertNull(render("x".repeat(AudioGenerator.MAX_CHARS + 1)))
	}

	private fun render(text: String): File? {
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val generator = AudioGenerator(context)
		val settings = Settings(context)
		val gate = CountDownLatch(1)
		var made: File? = null
		var answered = false
		generator.render(
			text = text,
			settings = settings,
			progress = { _, _ -> },
			done = { file ->
				made = file
				answered = true
				gate.countDown()
			}
		)
		assertTrue("the generation never answered", gate.await(15, TimeUnit.SECONDS))
		assertTrue("the generation never answered", answered)
		return made
	}
}
