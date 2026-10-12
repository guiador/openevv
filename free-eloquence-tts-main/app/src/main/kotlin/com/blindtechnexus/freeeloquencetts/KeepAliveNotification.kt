package com.blindtechnexus.freeeloquencetts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log

/**
 * The notification itself, built the one way for whoever posts it.
 *
 * Two things post it: the speech service, while a reader is bound to it, and
 * [KeepAliveService], which is what keeps it up when nothing is. Both have to
 * agree on the id, the channel and the shape of it, because a foreground
 * service is only as protected as the notification that says it is, and two
 * copies of the builder are two ways for them to stop agreeing.
 *
 * The notification is not only a price paid for staying alive. It is the one
 * control of this app that is on screen while the engine is doing its work, so
 * it carries a button to stop the speech and it opens the screen that can
 * change what the engine does. Both are named in words, and what it opens is
 * this app's own settings rather than a launcher icon or nothing.
 */
object KeepAliveNotification {

	const val CHANNEL_ID = "keep_alive"
	const val ID = 1

	/** What the channel is called, and what it is for. A channel a person can
	 *  silence from the system settings is a keep-alive they can switch off by
	 *  accident without meaning to, so the name says what losing it means. */
	fun ensureChannel(base: Context) {
        val context = SpeechLocale.context(base)
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val manager = context.getSystemService(NotificationManager::class.java) ?: return
		val channel = NotificationChannel(
			CHANNEL_ID,
			context.getString(R.string.keep_alive_channel),
			NotificationManager.IMPORTANCE_LOW
		)
		channel.description = context.getString(R.string.keep_alive_channel_description)
		channel.setShowBadge(false)
		manager.createNotificationChannel(channel)
	}

	/** The notification to post. [withStop] puts the button to stop speaking on
	 *  it, which is the caller's setting rather than one read here: the speech
	 *  service and the keep-alive service both know what was asked for already,
	 *  and making another [Settings] object to ask would register another
	 *  listener on the same preferences for a value that was in hand. */
	fun build(base: Context, withStop: Boolean): Notification {
        val context = SpeechLocale.context(base)
		val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Notification.Builder(context, CHANNEL_ID)
		} else {
			@Suppress("DEPRECATION")
			Notification.Builder(context)
		}
		builder
			.setSmallIcon(R.drawable.ic_launcher_foreground)
			.setContentTitle(context.getString(R.string.keep_alive_title))
			.setContentText(context.getString(R.string.keep_alive_text))
			.setOngoing(true)
			.setShowWhen(false)
			.setContentIntent(openSettings(context))
		val controls = NotificationControls.add(builder, context)
		if (withStop || controls) {
			builder.addAction(
				Notification.Action.Builder(
					null,
					context.getString(R.string.notification_text_18),
					stopSpeech(context)
				).build()
			)
		}
		return builder.build()
	}

	/**
	 * Asks for the foreground, and answers whether the system let it have it.
	 *
	 * A build whose manifest does not carry the permission, a special-use
	 * service the store rules do not believe, and Android 12's rule against a
	 * foreground service started from the background are all answered here
	 * rather than by a crash: the engine speaks either way, and a service that
	 * dies and takes the voice with it is the worse of the two outcomes by a
	 * long way.
	 */
	fun claim(service: Service, withStop: Boolean): Boolean = try {
		val notification = build(service, withStop)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			service.startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    if (WirelessMaintenance.active) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
		} else {
			service.startForeground(ID, notification)
		}
		true
	} catch (refused: SecurityException) {
		Log.e(TAG, "the system refused the keep-alive notification", refused)
		false
	} catch (refused: IllegalStateException) {
		// ForegroundServiceStartNotAllowedException is one of these, and it is
		// the answer a service started from the background gets on the devices
		// that have that rule.
		Log.e(TAG, "the system would not let this be a foreground service", refused)
		false
	}

	/** Takes the foreground back. [detach] leaves the notification standing,
	 *  which is what the caller wants when whatever it was handed to is up to
	 *  posting it; otherwise the notification goes with the claim. */
	fun release(service: Service, detach: Boolean) {
		runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				service.stopForeground(
					if (detach) Service.STOP_FOREGROUND_DETACH else Service.STOP_FOREGROUND_REMOVE
				)
			} else {
				// The variant that takes a flag of its own is Android 8's.
				// Below it the only question is whether the notification goes
				// too, which is what [detach] is for, and the answer is the
				// same either way: it does, and whoever still wants it is
				// posting it again inside three seconds.
				@Suppress("DEPRECATION")
				service.stopForeground(true)
			}
		}
	}

	/** Whether the notification is in the system's list of them, whatever the
	 *  permission says. Whoever asks has to decide whether a missing
	 *  notification that the user chose to hide is one to put back: see
	 *  [notificationsAllowed]. */
	fun isPosted(context: Context): Boolean {
		val manager = context.getSystemService(NotificationManager::class.java) ?: return false
		return manager.activeNotifications.any { it.id == ID }
	}

	/** Whether this app may put a notification up at all. On Android 13 and
	 *  later a missing permission hides it on the system's own decision, and
	 *  re-posting one the user has refused is noise for nothing. */
	fun notificationsAllowed(context: Context): Boolean {
		val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 24 && manager?.areNotificationsEnabled() == false) return false
        if (Build.VERSION.SDK_INT >= 26 && manager?.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
		return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
			PackageManager.PERMISSION_GRANTED
	}

	fun cancel(context: Context) {
		val manager = context.getSystemService(NotificationManager::class.java) ?: return
		runCatching { manager.cancel(ID) }
	}

	/** Opening the app's own settings, on the screen that owns all of this. */
	private fun openSettings(context: Context): PendingIntent {
		val intent = Intent(context, SettingsActivity::class.java)
			.putExtra(SettingsActivity.EXTRA_SCREEN, "notification")
			.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
		return PendingIntent.getActivity(context, REQUEST_OPEN, intent, flags())
	}

	/** Telling the engine to be quiet, which only it can do: nothing outside a
	 *  speech service can reach the audio it has already handed to Android. */
	private fun stopSpeech(context: Context): PendingIntent {
		val intent = Intent(context, EngineReceiver::class.java).setAction(EngineReceiver.ACTION_STOP)
		return PendingIntent.getBroadcast(context, REQUEST_STOP, intent, flags())
	}

	private fun flags(): Int {
		var want = PendingIntent.FLAG_UPDATE_CURRENT
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) want = want or PendingIntent.FLAG_IMMUTABLE
		return want
	}

	private const val TAG = "freeeloquencetts"
	private const val REQUEST_OPEN = 20
	private const val REQUEST_STOP = 21
}
