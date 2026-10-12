package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Starting something on a device that may not have it.
 *
 * An implicit intent is a question, and the answer depends on what is
 * installed: a phone with no browser has nothing that takes a web link, a
 * watch often has no mail client at all, and a scheme belonging to one app
 * needs that app. Asking first is no use either, because resolveActivity
 * answers "nothing" for an app the caller is not allowed to see, which on
 * Android 11 and later is most of them unless the manifest lists the ones to
 * look for -- and a list of the messaging apps of the world is not a thing to
 * put in a manifest. So the only reliable question is the one that tries it,
 * and the only honest answer to a failure is something said out loud: a button
 * that quietly does nothing is the worst thing a screen can do to somebody who
 * cannot see that it did nothing.
 */
internal fun startFirst(context: Context, candidates: List<Intent>): Boolean {
	// Started from an activity, which is where these screens all are, no task
	// of its own is needed. From anywhere else Android refuses to start one.
	val newTask = context.findActivity() == null
	for (candidate in candidates) {
		try {
			if (newTask) candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			context.startActivity(candidate)
			return true
		} catch (nothing: ActivityNotFoundException) {
			// On to the next way of asking, if there is one.
		} catch (refused: SecurityException) {
			// Likewise.
		}
	}
	return false
}

/**
 * The three ways to reach the people who made this.
 *
 * Each asks twice, in the order that works on the most devices: the link
 * anybody with a browser can follow, and then the app's own scheme for a
 * device that has the app and no browser. WhatsApp and Telegram both have one,
 * and both are how their users expect to be reached.
 *
 * The addresses are here rather than in the strings file because they are
 * addresses and not prose: they are what an intent carries, and a translation
 * of one would be a broken link. What is said about them is in the strings.
 *
 * The ways of asking are built without asking, which is what lets a test say
 * that the address is the one that was given rather than launching somebody's
 * WhatsApp to find out.
 */
object ExternalLinks {

	const val WHATSAPP_NUMBER = "9779708340992"
	const val TELEGRAM_CHANNEL = "blindtechnexus"
	const val EMAIL_ADDRESS = "blindtechvisionary@gmail.com"

	/**
	 * For bugs, feedback and feature requests.
	 *
	 * The address is `wa.me`, which is the link WhatsApp itself hands out, and
	 * not `w.me`: that is the short form of the same thing only for a signed-in
	 * desktop session, and opened on a phone with the app installed it goes to
	 * WhatsApp's web page, which says it cannot be loaded there. A button
	 * labelled "Contact on WhatsApp" that arrives at an error page is what this
	 * address used to be, and it is the reason the other two are here as well.
	 *
	 * `wa.me` redirects to `api.whatsapp.com/send`, and both are given because
	 * a device whose browser is old or blocked follows the redirect badly: the
	 * direct api. address is the one that works when the short link does not.
	 * The `whatsapp:` scheme is last rather than first, since a link the
	 * browser can carry is worth more than one that needs the app to answer a
	 * scheme not every build registers.
	 *
	 * The number is in international form with no plus, which is what both of
	 * the web addresses want; a plus in the query is read as a space.
	 */
	fun whatsAppWays(): List<Intent> = listOf(
		view("https://wa.me/$WHATSAPP_NUMBER"),
		view("https://api.whatsapp.com/send?phone=$WHATSAPP_NUMBER"),
		view("whatsapp://send?phone=$WHATSAPP_NUMBER")
	)

	/** Update broadcasts, more apps and tools, and everyday information. */
	fun telegramWays(): List<Intent> = listOf(
		view("https://t.me/$TELEGRAM_CHANNEL"),
		view("tg://resolve?domain=$TELEGRAM_CHANNEL")
	)

	/**
	 * The reliable one, with the address and a subject already filled in.
	 *
	 * SENDTO with a mailto: opens a mail app rather than a chooser of
	 * everything that can take text, which is what ACTION_SEND does. The
	 * fallback is the same question put to whichever mail app will answer it,
	 * for the devices where an address in the data is what they choke on.
	 */
	fun emailWays(subject: String): List<Intent> = listOf(
		Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL_ADDRESS"))
			.putExtra(Intent.EXTRA_SUBJECT, subject),
		Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
			.putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_ADDRESS))
			.putExtra(Intent.EXTRA_SUBJECT, subject)
	)

	fun whatsApp(context: Context): Boolean = startFirst(context, whatsAppWays())

	fun telegram(context: Context): Boolean = startFirst(context, telegramWays())

	fun email(context: Context, subject: String): Boolean =
		startFirst(context, emailWays(subject))

	private fun view(uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
}
