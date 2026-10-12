package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * The system's own text to speech screen, which is where this engine is
 * chosen.
 *
 * Choosing an engine is not this app's to do. The preferred engine lives in
 * the system's secure settings and only the system writes it, so the most
 * important thing on the main screen is a way there: an engine nobody has
 * chosen speaks to nobody, however well it is built.
 *
 * The way there differs by device. The action named first is a convention
 * rather than an interface -- the settings app is not part of Android's public
 * API, and manufacturers rename, move and drop its screens -- so three are
 * tried in order and the last is the top of the settings app, from which the
 * speech screen is a couple of taps away and is at least somewhere.
 */
object SystemSpeechSettings {

	/** The screens to try, in order. Built rather than started, which is what
	 *  lets a test say what is asked for instead of opening a system screen on
	 *  a device to find out. */
	fun ways(): List<Intent> = listOf(
		Intent(TTS_SETTINGS),
		Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
		Intent(Settings.ACTION_SETTINGS)
	)

	/** Opens it, and answers whether anything opened, so that the screen can
	 *  say so out loud when nothing did. */
	fun open(context: Context): Boolean = startFirst(context, ways())

	private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
}
