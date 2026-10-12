package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fetching an APK, and saying how far it has got.
 *
 * The file is written to the cache, and nowhere else: a download that outlives
 * the app is a file nobody asked to keep, and the cache is where Android itself
 * cleans up when the install has happened or the phone is short of room. It is
 * also the one place a FileProvider can hand to the package installer without
 * this app having to hold a permission over shared storage, which is what the
 * install step in [Installer] does.
 *
 * Progress is measured in bytes rather than percent, because a body of unknown
 * length is normal on a redirecting host and a bar that cannot fill is worse to
 * listen to than a number that says what has arrived. Whoever draws the dialog
 * turns it into a percent when there is a total to divide by, and into a running
 * count of megabytes when there is not.
 *
 * Cancel is a flag rather than an interrupt. The thread is inside a socket read
 * for most of the life of a download, and no interrupt reaches a socket in this
 * platform's Java; the flag is checked between the reads, which is every few
 * tens of kilobytes at most, so the wait for a cancel to land is a fraction of a
 * second and no more. The partial file is deleted on the way out, since half an
 * APK is worth nothing to anybody and costs room the phone may want.
 */
internal object ApkDownload {

	/** How much has arrived and how much was expected, in bytes. [total] is
	 *  negative when the server would not say. */
	class Progress(val done: Long, val total: Long)

	sealed class Outcome {
		class Installed(val file: File, val size: Long) : Outcome()
		object Cancelled : Outcome()
		object Failed : Outcome()
	}

	/** Where an update goes, and the name it goes under. The version is in the
	 *  name so that the installer's own sentence -- which names the file -- says
	 *  what is being put on the phone. */
	fun fileFor(context: Context, version: String): File {
		val directory = File(context.cacheDir, DIRECTORY)
		runCatching { directory.mkdirs() }
		return File(directory, "alt-eloquence-tts-" + sanitize(version) + ".apk")
	}

	private fun sanitize(version: String): String =
		version.map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '-' }
			.joinToString("").take(40)

	/**
	 * Writes [address] to [file] and answers what happened.
	 *
	 * Blocks, and is meant to be called from a thread of its own. [onProgress]
	 * is called from that thread, at no more than a few times a second, which is
	 * what makes it safe to hand a state holder to it: the drawing is done
	 * wherever the state is watched, and nothing here touches a view.
	 */
	fun to(
		context: Context,
		address: String,
		file: File,
		cancelled: AtomicBoolean,
		onProgress: (Progress) -> Unit
	): Outcome {
		runCatching { if (file.exists()) file.delete() }
		val connection = UpdateInfo.openStream(address) ?: return Outcome.Failed
		var written = 0L
		try {
			val code = connection.responseCode
			if (code !in 200..299) {
				Log.e(TAG, "the download answered $code")
				return Outcome.Failed
			}
			val declared = connection.contentLength
			val total = if (declared > 0) declared.toLong() else -1L
			connection.inputStream.use { input ->
				file.outputStream().buffered(BUFFER).use { output ->
					val buffer = ByteArray(BUFFER)
					var lastReport = 0L
					while (true) {
						if (cancelled.get()) {
							file.delete()
							return Outcome.Cancelled
						}
						val read = input.read(buffer)
						if (read < 0) break
						output.write(buffer, 0, read)
						written += read
						// A tenth of a second is often enough to look live and is
						// rarely enough to be the reason the file is slow.
						val now = System.currentTimeMillis()
						if (now - lastReport >= REPORT_MS || (total > 0 && written >= total)) {
							lastReport = now
							onProgress(Progress(written, total))
						}
					}
				}
			}
			if (total > 0 && written < total) {
				// The body stopped short. Half an APK installs as nothing at all
				// -- or worse, as a refusal the installer does not explain -- so it
				// is not handed on, and the number of bytes is what makes the log
				// line readable as a truncated download rather than a bad file.
				Log.e(TAG, "the download ended at $written of $total bytes")
				file.delete()
				return Outcome.Failed
			}
			if (!isAnApk(context, file)) {
				Log.e(TAG, "what came down is not a package this phone could install")
				file.delete()
				return Outcome.Failed
			}
			onProgress(Progress(written, if (total > 0) total else written))
			return Outcome.Installed(file, written)
		} catch (stopped: IOException) {
			Log.i(TAG, "the download was broken off", stopped)
			runCatching { file.delete() }
			return if (cancelled.get()) Outcome.Cancelled else Outcome.Failed
		} finally {
			runCatching { connection.disconnect() }
		}
	}

	/** Whether the file is a package this Android could install, asked of the
	 *  package manager rather than of the file name. It is the last thing
	 *  standing between a wrong address in the published file and a person being
	 *  sent to the installer with something that is not this app; the signature
	 *  is checked there, by Android, and by nothing here.
	 *
	 *  The archive is not installed, so the package manager of any context will
	 *  answer the question about it -- which is why this takes the application
	 *  context rather than holding one. */
	private fun isAnApk(context: Context, file: File): Boolean = runCatching {
		context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) != null
	}.getOrDefault(false)

	private const val TAG = "freeeloquencetts"
	private const val DIRECTORY = "update"
	private const val BUFFER = 64 * 1024
	private const val REPORT_MS = 120L
}
