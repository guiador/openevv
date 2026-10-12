package com.blindtechnexus.freeeloquencetts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The service whose only job is the notification.
 *
 * The engine's own service cannot be the one to hold it. A text to speech
 * service belongs to the system: it is started when a reader binds to it and
 * destroyed when the last one lets go, which for most phones is most of the
 * time. A notification that comes and goes with that is a keep-alive that
 * keeps nothing alive in the one situation it is for -- the phone idle, the
 * reader quiet, and the next thing said arriving some time later.
 *
 * So this service exists on its own. It is started when the setting is turned
 * on, by whatever is in front of the user at the time, and by the boot receiver
 * when that has been asked for; it is stopped when the setting is turned off.
 * All it does is claim the foreground and hold it, which is the whole of what
 * the protection is made of.
 *
 * It holds it and nothing more. It used to look for its own notification every
 * few seconds and post it again if it had gone, and that watchdog is what a
 * phone that is mostly idle was paying for: a check every three seconds, for
 * ever, on the chance that a notification the system does not let you swipe
 * away had been swiped away. It is gone, and with it the handler and the clock
 * it ran on. What is left is a service that claims once, on the way in, and
 * answers a redelivery by claiming again -- which is what the system asking
 * "are you still there?" is for.
 */
class KeepAliveService : Service() {

	private var active = false

	override fun onCreate() {
		super.onCreate()
		active = true
		isRunning = true
        instance = this
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		KeepAliveNotification.ensureChannel(this)
		// A redelivery after the system took the process away has no intent to
		// go with it, so the setting is what says whether it is wanted now.
		if (!Settings.flag(this, Settings.KEY_PERSISTENT_NOTIFICATION, false)) {
			stopAndQuit()
			return START_NOT_STICKY
		}
		if (!claim()) {
			// Refused, this service is worth nothing: staying up without the
			// notification would cost the same to the battery and buy the
			// engine no protection at all.
			stopAndQuit()
			return START_NOT_STICKY
		}
		hasForeground = true
        WirelessMaintenance.startIfIdle(this)
		return START_STICKY
	}

	override fun onDestroy() {
		super.onDestroy()
		active = false
		isRunning = false
		hasForeground = false
        instance = null
        WirelessMaintenance.stop()
		KeepAliveNotification.release(this, detach = false)
	}

	override fun onBind(intent: Intent?): IBinder? = null

	/** Whether the notification is up, for anything that asks. */
	val holding: Boolean get() = active

	/** Claims the foreground, which is the notification plus the standing the
	 *  system gives whoever posts it. */
	private fun claim(): Boolean = KeepAliveNotification.claim(
		this,
		Settings.flag(this, Settings.KEY_NOTIFICATION_STOP, true)
	)

	private fun stopAndQuit() {
		hasForeground = false
		KeepAliveNotification.cancel(this)
		stopSelf()
	}

	companion object {

		/** True only after Android accepted the foreground-service claim. */
		@Volatile
		internal var hasForeground = false
			private set

		private var instance: KeepAliveService? = null
        fun refreshForeground() {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val service=instance ?: return@post
                hasForeground=service.claim()
                if(!hasForeground) WirelessMaintenance.stop()
            }
        }
        private const val TAG = "freeeloquencetts"

		/** The one thing worth knowing about this service from anywhere in the
		 *  app: whether the notification is already being held up by the
		 *  service that exists to hold it. */
		@Volatile
		var isRunning = false
			private set

		/** Asks for it, and answers whether the system let it be asked for.
		 *  Android 12 and later refuse a foreground service started from the
		 *  background, which is what this looks like when the speech service
		 *  is the one asking. */
		fun start(context: Context): Boolean = try {
			ContextCompat.startForegroundService(context, intent(context))
			true
		} catch (refused: IllegalStateException) {
			// ForegroundServiceStartNotAllowedException is one of these.
			Log.w(TAG, "the system would not let the keep-alive start", refused)
			false
		} catch (refused: SecurityException) {
			Log.w(TAG, "the keep-alive was refused", refused)
			false
		}

		fun stop(context: Context) {
			runCatching { context.stopService(intent(context)) }
		}

		private fun intent(context: Context) = Intent(context, KeepAliveService::class.java)
	}
}
