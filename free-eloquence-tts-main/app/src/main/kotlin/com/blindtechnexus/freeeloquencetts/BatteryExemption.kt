package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The exemption from battery optimization, which this engine cannot do
 * without.
 *
 * A text to speech engine is the awkward case among background apps: it is
 * idle almost all of the time and has to answer at once when it is not, which
 * is the shape every battery policy is written to kill. Doze takes the network
 * and the alarms away from anything not exempt, and the manufacturer policies
 * stacked on top of it are stricter than Doze and do not say what they did.
 * The voice then stops in the middle of a sentence, or does not start at all
 * once the phone has been still for a while, and nothing inside the app can
 * see that happening in order to explain it. For somebody whose screen reader
 * it is, that is not an inconvenience but a phone that has stopped working.
 *
 * So the exemption is required rather than wanted. The app asks for it in
 * words of its own as well as through the system's screen, which is a yes or
 * no with nothing said about why, and goes on saying whether it has it.
 */
object BatteryExemption {

	/** Whether the device will leave this app alone. Below Android 6 there is
	 *  no such policy to be exempt from, so the answer is yes. */
	fun isGranted(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
		val power = context.getSystemService(PowerManager::class.java) ?: return true
		return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }
			.getOrDefault(false)
	}

	/**
	 * The screens that can grant it, in order.
	 *
	 * The first asks about this app by name and is what the permission in the
	 * manifest is for; some manufacturers take it out of their builds. The
	 * second is the list of every app on the device, which is slower to use and
	 * better than a button that does nothing.
	 *
	 * Built rather than started, which is what lets a test say what is asked
	 * for instead of putting a system dialog on a device to find out.
	 */
	fun ways(context: Context): List<Intent> = listOf(
		Intent(
			Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
			Uri.parse("package:${context.packageName}")
		),
		Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
	)

	/** Asks for it, and answers whether a screen opened. Nothing here can
	 *  grant it; the answer comes back through [isGranted] on the next resume. */
	fun request(context: Context): Boolean = startFirst(context, ways(context))
}
