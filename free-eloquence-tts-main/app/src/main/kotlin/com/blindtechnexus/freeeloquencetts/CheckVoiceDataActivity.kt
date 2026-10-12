package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * What Android asks before it will use the engine. Everything is in the APK,
 * so the eight languages are already there and nothing is missing.
 */
class CheckVoiceDataActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val available = ArrayList<String>()
		if (EvvNative.loaded) {
			for (language in Eci.LANGUAGES) {
				available.add("${language.iso3Language}-${language.iso3Country}")
			}
		}
		val result = Intent().apply {
			putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
			putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList())
		}
		val code = if (available.isEmpty()) {
			TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL
		} else {
			TextToSpeech.Engine.CHECK_VOICE_DATA_PASS
		}
		setResult(code, result)
		finish()
	}
}
