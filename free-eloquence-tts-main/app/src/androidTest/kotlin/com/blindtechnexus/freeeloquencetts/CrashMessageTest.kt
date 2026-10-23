package com.blindtechnexus.freeeloquencetts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real JNI and TTS service regression for the reported APK-message crash.
 * Reads settings without changing them; creates only its own temporary WAV.
 */
@RunWith(AndroidJUnit4::class)
class CrashMessageTest {
    @Test
    fun messageAndFragmentsSurviveInSpanishModes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = Settings(context)
        val cases = listOf(MESSAGE, MESSAGE.lineSequence().first(), "Version: 9.41.53 (Mod)", "Recibido el a las 9:03 PM")
        for (language in listOf(Eci.SPANISH_SPAIN, Eci.SPANISH_MEXICO)) {
            val engine = checkNotNull(EvvEngine.open(language))
            try {
                engine.setSampleRate(16000)
                for (mode in 0..2) {
                    engine.setSynthesisMode(mode)
                    for ((index, raw) in cases.withIndex()) {
                        val processed = TextPipeline.apply(raw, settings, language = language)
                        for (text in listOf(raw, processed)) {
                            assertTrue("refused language=$language mode=$mode case=$index", engine.speak(text))
                            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
                            val buffer = ByteArray(8192)
                            var count = 0L
                            var audible = false
                            while (true) {
                                assertTrue("synthesis deadline", System.nanoTime() < deadline)
                                val made = engine.read(buffer, 3000)
                                assertTrue("native read timed out", made >= 0)
                                if (made == 0) break
                                count += made
                                audible = audible || (0 until made).any { buffer[it] != 0.toByte() }
                            }
                            assertTrue("empty audio language=$language mode=$mode case=$index", count > 1000 && audible)
                        }
                    }
                }
            } finally {
                engine.close()
            }
        }
    }

    @Test
    fun installedServiceReadsMessageWithCurrentSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        val status = AtomicInteger(-99)
        val tts = TextToSpeech(context, { code -> status.set(code); connected.countDown() }, context.packageName)
        val file = File.createTempFile("openevv-crash-regression-", ".wav", context.cacheDir)
        try {
            assertTrue("TTS connection timed out", connected.await(20, TimeUnit.SECONDS))
            assertTrue("TTS connection failed", status.get() == TextToSpeech.SUCCESS)
            for ((index, text) in listOf(MESSAGE, "La voz continúa después del mensaje.").withIndex()) {
                val finished = CountDownLatch(1)
                val outcome = AtomicInteger(-99)
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String) = Unit
                    override fun onDone(id: String) { outcome.set(0); finished.countDown() }
                    @Deprecated("framework callback")
                    override fun onError(id: String) { outcome.set(-1); finished.countDown() }
                })
                assertTrue("service refused request", tts.synthesizeToFile(text, Bundle(), file, "crash-regression-$index") == TextToSpeech.SUCCESS)
                assertTrue("service did not finish", finished.await(45, TimeUnit.SECONDS))
                assertTrue("service reported error", outcome.get() == 0)
                assertTrue("empty WAV", file.length() > 1000)
            }
        } finally {
            tts.shutdown()
            file.delete()
        }
    }

    companion object {
        val MESSAGE = """, APK archivoYT Music Premium v9.41.53 @blindtechnexus.apk, 113,8 MB
📱 YouTube Music Premium
Version: 9.41.53 (Mod)
Developer: Google LLC
Size: 113 MB

@blindtechnexus
Recibido el a las 9:03 PM
Visto 60 veces"""
    }
}
