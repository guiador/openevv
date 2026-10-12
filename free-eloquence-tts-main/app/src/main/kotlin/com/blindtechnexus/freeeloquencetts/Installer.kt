package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Asking Android to put the downloaded APK on the phone.
 *
 * The last step of an update belongs to the system and not to this app: nothing
 * an app installs by itself is trusted, so the arrangement is to hand the file
 * over and let the installer say what it will do about it. That means a content
 * URI rather than a path -- a path into this app's cache is not readable by
 * anything else, and asking for a broader permission over shared storage so that
 * a file can be pointed at would be the worse of two ways to do one thing. So
 * the URI is granted for one read, to the installer, for the length of the
 * install, which is the smallest permission that works.
 *
 * On Android 8 and later there is a door in front of that: an app may not offer
 * to install anything until it has been allowed to install unknown apps, and the
 * allowance is not this app's to grant. What is done here is to ask for it --
 * which opens this app's own entry on the system screen where it is given -- and
 * to say so in the log when even that screen is not there. An app that quietly
 * did nothing when a person pressed "install" is what this whole file exists to
 * avoid.
 *
 * The action, and not a call into a package installer session of our own: the
 * session API can put an APK on a phone without asking anybody, and an update
 * mechanism that can do that is one a person has no part in. What they see in
 * the installer -- the version, the permissions, the replace -- is Android's own
 * wording for their own phone, and it is not this app's business to improve on
 * it.
 */
internal object Installer {

	/** The authority FileProvider is published under, in the manifest. The two
	 *  have to agree, and a test says they do. */
	const val AUTHORITY_SUFFIX = ".fileprovider"

	fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

	/** Whether this app may ask for an install at all. Below Android 8 the
	 *  question was not asked per app: the system's one global switch was
	 *  enough, and it belongs to the person using the phone rather than to
	 *  this app, so the answer here is that asking is worth trying. */
	fun canRequest(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
		return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
	}

	/** Opens the system screen where the allowance is given, for this app alone.
	 *  Answers whether anything took the question. */
	fun askToAllow(context: Context): Boolean {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
		val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
			.setData(Uri.fromParts("package", context.packageName, null))
		return try {
			start(context, intent)
			true
		} catch (nothing: ActivityNotFoundException) {
			Log.e(TAG, "nothing here asks about installing unknown apps")
			false
		} catch (refused: SecurityException) {
			Log.e(TAG, "the system would not say about installing unknown apps", refused)
			false
		}
	}

	/**
	 * Hands [file] to the installer.
	 *
	 * The activity is what starts it, so that the installer's screen lands in
	 * this app's task and back returns to where the download was; from a
	 * service or a receiver Android refuses to start one at all.
	 */
	fun install(activity: Activity, file: File): Boolean {
		val uri = try {
			FileProvider.getUriForFile(activity, authority(activity), file)
		} catch (notShared: IllegalArgumentException) {
			// The file is not under a path the provider was told about, which is
			// a mistake in this app rather than in the device: the manifest's
			// paths and this app's cache have to agree.
			Log.e(TAG, "the update file is not where the file provider publishes", notShared)
			return false
		}
		val intent = Intent(Intent.ACTION_VIEW)
			.setDataAndType(uri, "application/vnd.android.package-archive")
			.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		return try {
			activity.startActivity(intent)
			Tracker.action(activity, "handed an update to the installer")
			true
		} catch (nothing: ActivityNotFoundException) {
			// A device with no package installer at all is rare and is worth one
			// line, since it is otherwise indistinguishable from a broken link.
			Log.e(TAG, "nothing on this device installs packages")
			false
		} catch (refused: SecurityException) {
			// Readable by nobody but us: the grant did not land, or the file has
			// gone. The first is a fault in this app and the second is the cache
			// having been cleaned under it, and both want the same answer.
			Log.e(TAG, "the installer was not given the file", refused)
			false
		}
	}

	/** Whether the app is allowed to install at all, in the only sense that
	 *  matters on the way to a download: asked before the bytes are fetched, so
	 * that a person is not sent the length of an APK only to be told at the end
	 * that it cannot be put down. */
	fun blockedOnPermission(context: Context): Boolean = !canRequest(context)

	/** The package the file holds, which is what the installer's own screen will
	 *  say. Asked of the archive rather than of the installed app, because what
	 *  matters is what is about to be put on the phone. */
	/** Whether the file still reads as a package this phone could install. Asked
	 *  of a file the app put in the cache a while ago, because the cache is
	 *  Android's to empty when it wants, and a download that was broken off is
	 *  half an APK, which is not an APK at all. */
	fun isInstallable(context: Context, file: File): Boolean =
		archiveVersion(context, file).isNotEmpty()

	fun archiveVersion(context: Context, file: File): String = runCatching {
		context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.versionName.orEmpty()
	}.getOrDefault("")

	private fun start(context: Context, intent: Intent) {
		if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(intent)
	}

	private const val TAG = "freeeloquencetts"
}
