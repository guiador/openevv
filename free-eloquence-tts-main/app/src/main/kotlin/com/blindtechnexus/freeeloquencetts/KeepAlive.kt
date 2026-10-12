package com.blindtechnexus.freeeloquencetts

import android.app.Service
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The persistent notification, up while the switch says it should be and not
 * otherwise.
 *
 * A foreground service is the one thing that tells Android, in the one
 * language Android always listens to, that an app is doing something the user
 * asked for and must not be killed. A text to speech engine is the awkward
 * case: idle almost all the time, needed at once when it is not, which is the
 * shape every battery policy is written to kill. The notification makes the
 * engine visible, so the system's own rules leave it alone.
 *
 * It is one notification and one switch. On, [KeepAliveService] is started
 * and holds the notification for as long as the app exists; off, that service
 * is stopped and the notification goes with it -- and nothing else in this
 * app posts one. There used to be a second poster: the speech service used to
 * claim the foreground for the length of every utterance the switch was off
 * for, so a person who had turned the notification off still met it several
 * times a minute. It was defended as protection, and it was not: an utterance
 * is a second long and the framework has already bound the service for it, so
 * what the claim actually bought was a notification that appeared when it had
 * been refused. The switch decides now, and only the switch.
 *
 * Nothing here polls either. The notification was looked for every few
 * seconds and put back if it had gone, which was written for the case of a
 * notification swiped away or dropped when the system rebuilds its list --
 * and it cost a check every three seconds, for ever, on a phone that is
 * mostly idle, which is exactly the cost the battery screen exists to
 * explain. A notification posted by a foreground service is ongoing and is
 * not swiped away; a service that is stopped takes its notification with it.
 * So the check is gone and the notification is posted once, when the switch
 * moves and when the service starts, which is all that was ever needed.
 *
 * Two things can still go wrong, and both have an answer here:
 *
 * Android 12 and later refuse a foreground service started from the
 * background, which is what starting one from a speech service the system
 * bound looks like to the system. Refused, the app says so in the log and the
 * switch's own screen says what is in force; the notification is not posted
 * by the speech service instead, because a notification is the switch's to
 * promise and not the engine's to make up for.
 *
 * And a device that has refused the notification permission hides whatever is
 * posted. That is the user's own answer, so nothing here argues with it: the
 * service still holds the foreground where the system allows it, and nothing
 * re-posts anything every few seconds.
 */
class KeepAlive(private val service: Service) {

	private val handler = Handler(Looper.getMainLooper())

	/** Whether the user has asked for any of this. */
	private var wanted = false

	/** Turns the notification on or off, matching the setting. A no-op when the
	 *  state has not changed, which is what keeps the utterance path free of
	 *  it: the speech service asks on every utterance and pays a comparison. */
	fun sync(enabled: Boolean) {
		if (enabled == wanted) return
		wanted = enabled
		if (enabled) {
			KeepAlive.ensureChannel(service)
			KeepAliveService.start(service)
		} else {
			KeepAliveService.stop(service)
			// Whatever is up goes with it: the service that was holding the
			// notification may be the speech service's own fallback, and the
			// switch is the whole of what says it should be there.
			handler.post {
				if (!wanted) KeepAliveNotification.cancel(service)
			}
		}
	}

	/** Whether the notification is wanted at all, which is what a screen that
	 *  has just read the setting needs to know. */
	fun isWanted(): Boolean = wanted

	/** Called when the speech service goes away. The app's own service is left
	 *  alone: it is not the speech service's to stop, and leaving it up is the
	 *  whole point. */
	fun release() {
		wanted = false
		handler.removeCallbacksAndMessages(null)
	}

	companion object {
		const val TAG = "freeeloquencetts"
		const val CHANNEL_ID = KeepAliveNotification.CHANNEL_ID
		const val ID = KeepAliveNotification.ID

		/** How often the notification is looked for and put back: never, and
		 *  the nought is the answer rather than an absence. It used to be
		 *  three seconds, on every phone, for ever, for a notification that
		 *  a foreground service does not let be swiped away and that a
		 *  stopped service takes with it. Nothing reads this; it is here so
		 *  that anything still asking -- a screen, a test, a person reading
		 *  the source -- is told that the check is gone rather than left to
		 *  wonder how often it runs. See the note at the top of this file. */
		const val CHECK_MS = 0L

		private val handler = Handler(Looper.getMainLooper())

		/** What the app itself does with the notification, with no speech
		 *  service in sight: the setting is switched on from the battery
		 *  screen, and the keep-alive starts there rather than waiting for
		 *  somebody to speak. */
		fun sync(context: Context, enabled: Boolean) {
			KeepAlive.ensureChannel(context)
			if (enabled) {
				KeepAliveService.start(context)
				// A device that refused the foreground service is told about
				// in the log rather than being silently worse. Once, and not
				// on a clock: a failed start is not mended by asking again
				// every three seconds for the rest of the day.
				handler.postDelayed({
					if (enabled && !KeepAliveService.isRunning) {
						Log.w(TAG, "the keep-alive service would not start; the voice is unprotected until the engine speaks")
					}
				}, 1000L)
			} else {
				KeepAliveService.stop(context)
				KeepAliveNotification.cancel(context)
			}
		}

		/** The channel, made before anything is posted to it. */
		fun ensureChannel(context: Context) = KeepAliveNotification.ensureChannel(context)
	}
}

/**
 * What the user did, written to the system log when they asked for it.
 *
 * A screen reader that has gone quiet has to be fixed by reasoning backwards
 * from what happened, and a log of what was changed is the raw material for
 * that reasoning. Nothing sensitive is ever written: only which switch moved
 * and which way.
 */
object Tracker {

	const val TAG = "freeeloquencetts-actions"

	/** Records [what], when tracking is on, and answers whether it was
	 *  written. The answer is what a test can hold onto, since whether a line
	 *  went to the system log is not readable back out of it. */
	fun action(settings: Settings?, what: String): Boolean {
		if (settings == null || !settings.trackActions) return false
		Log.i(TAG, what)
		return true
	}

	/** The same, for a place that has a context rather than the settings of its
	 *  own. Nothing is registered on the preferences to read one value. */
	fun action(context: Context, what: String) {
		if (!Settings.flag(context, Settings.KEY_TRACK_ACTIONS, false)) return
		Log.i(TAG, what)
	}
}
