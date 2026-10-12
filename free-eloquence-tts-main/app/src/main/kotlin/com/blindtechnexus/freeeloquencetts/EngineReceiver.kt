package com.blindtechnexus.freeeloquencetts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The two things that arrive from outside the app while it is not in front of
 * anybody.
 *
 * The boot. A screen reader is wanted from the lock screen onwards, which is
 * why the speech service is direct-boot aware; what is not automatic is the
 * app's own standing. The keep-alive notification, the one thing that keeps
 * Android from putting the engine to sleep, has nowhere to come from after a
 * restart unless the app is opened again, and a phone that has been rebooted
 * in the night is exactly the phone whose voice is needed before anybody has
 * thought to unlock it. So when that has been asked for, the receiver asks for
 * the service the boot has cleared away. [Intent.ACTION_LOCKED_BOOT_COMPLETED]
 * is what the direct-boot aware half of this app gets while the rest of the
 * phone is still behind a PIN, and [Intent.ACTION_BOOT_COMPLETED] is the answer
 * for the devices that do not deliver one so early; whichever arrives, the work
 * is the same and starting twice costs nothing.
 *
 * The notification's own button. Nothing outside a speech service can reach the
 * audio a service has already handed to Android, so a reader that has started a
 * paragraph cannot be stopped from the notification unless the notification can
 * say so to the service -- which is what this is for, and the reason it is a
 * broadcast to a named component rather than anything anybody else could send.
 *
 * An update replaces the app, and with it the service that was holding the
 * notification, without asking. [Intent.ACTION_MY_PACKAGE_REPLACED] is the one
 * broadcast that says so to this app alone.
 */
class EngineReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		when (intent.action) {
			ACTION_STOP -> { WirelessMaintenance.stop(); stopSpeaking(context) }
            NotificationControls.ACTION -> NotificationControls.adjust(context, intent.getIntExtra("delta", 1).coerceIn(-1,1),
                if(intent.hasExtra("parameter")) intent.getIntExtra("parameter",0) else null)
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_LOCKED_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED -> afterBoot(context)
			else -> Unit
		}
	}

	private fun afterBoot(context: Context) {
		if (!Settings.flag(context, Settings.KEY_START_ON_BOOT, false)) return
		if (!Settings.flag(context, Settings.KEY_PERSISTENT_NOTIFICATION, false)) {
			// Nothing to bring back: the notification was never asked for, and
			// starting a service that would post one nobody wanted is the one
			// thing this screen promises not to do.
			Log.i(TAG, "start on boot is on but the persistent notification is not, so nothing started")
			return
		}
		KeepAliveService.start(context)
		Tracker.action(context, "the keep-alive came back after a restart")
	}

	private fun stopSpeaking(context: Context) {
		val service = EvvTtsService.instance
		if (service == null) {
			// Nothing is speaking, which is worth saying in the log rather
			// than leaving as a button that did nothing at all.
			Log.i(TAG, "asked for silence with no engine running")
			return
		}
		service.stopSpeakingNow(maintainIdle=false)
		Tracker.action(context, "silence asked for from the notification")
	}

	companion object {

		private const val TAG = "freeeloquencetts"

		/** What the notification's button sends. Named after this app because
		 *  only this app's own receiver takes it, the intent being built with a
		 *  component on it rather than as a question. */
		const val ACTION_STOP = "com.blindtechnexus.freeeloquencetts.action.STOP_SPEECH"
	}
}
