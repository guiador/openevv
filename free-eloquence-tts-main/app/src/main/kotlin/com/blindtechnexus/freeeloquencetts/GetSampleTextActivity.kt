package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech

/** The sentence the system settings screen speaks when someone taps Listen. */
class GetSampleTextActivity : Activity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val requested = Eci.languageFor(
			intent?.getStringExtra("language"),
			intent?.getStringExtra("country")
		)
		val localized = if (requested == null) SpeechLocale.context(this)
			else SpeechLocale.context(this, requested.code)
		val result = Intent().apply {
			putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, localized.getString(R.string.sample_text))
		}
		setResult(TextToSpeech.LANG_AVAILABLE, result)
		finish()
	}
}
