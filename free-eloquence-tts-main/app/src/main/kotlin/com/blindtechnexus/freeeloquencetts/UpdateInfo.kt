package com.blindtechnexus.freeeloquencetts

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * What the release channel says about the newest build, and whether it is worth
 * getting up for.
 *
 * The whole of the arrangement is one small file on GitHub holding two
 * values -- the version and the address of the APK -- and the app asking for it
 * in the background when it starts. There is no account, no library and no
 * server of this app's own, and nothing about a person who has the app can be
 * learned from the asking, which is what makes it an acceptable way to tell
 * people that their voice has been fixed.
 *
 * A version is *different*, not newer. That is the rule the release channel is
 * written for: whoever publishes the file has said what the version everybody
 * should be on is, and an app on any other version -- one back or one forward --
 * is on something the channel does not stand behind. Comparing the numbers
 * instead would need an order for strings like "1.2-beta3" and "1.10", where a
 * number-by-number read says 1.10 is older than 1.2 and a text read says it is
 * newer; the two are a distance apart and neither is right, and the answer to a
 * question nobody can settle is not to settle it. So the one comparison that is
 * exact is made, and anything unequal is offered.
 *
 * The blank is the whole of the failure handling: no network, a file that is not
 * there, an answer that is not JSON and a version nobody has published are all
 * the same thing to the person using the app, who is told nothing and loses
 * nothing. The difference between them is written in the log, where whoever is
 * asking can find it, because an update check that has stopped working is worth
 * knowing about and is not worth a dialog.
 */
internal object UpdateInfo {

	/** Where the release channel lives. It is data on a git host and not an API:
	 *  there is nothing to authenticate to and nothing to rate limit. */
	// No release channel is configured for this independent offline edition.
	const val CHANNEL = ""

	/** How long to wait for the file. Longer than a phone on a train should
	 *  spend finding out, shorter than anything a person notices. */
	private const val CONNECT_TIMEOUT_MS = 10000
	private const val READ_TIMEOUT_MS = 15000

	/** What was published. */
	class Available(val version: String, val downloadUrl: String)

	/**
	 * Reads an answer. [json] is the body of what came back and [current] is
	 * this build's version name.
	 *
	 * Answers null when there is nothing to offer, which is the same answer as
	 * "up to date" to everybody but the log. It is separate from the asking so
	 * that a test can hold the parsing without a network: the two rules in here
	 * -- a version that has to be there and an address that has to be https --
	 * are the only things that stand between a typo in the published file and a
	 * phone being told to fetch an executable from somewhere else.
	 */
	fun parse(json: String, current: String): Available? {
		val published = runCatching { JSONObject(json) }.getOrNull() ?: return null
		val version = published.optString("version").trim()
		if (version.isEmpty()) return null
		if (version == current.trim()) return null
		val download = published.optString("url").trim()
		if (download.isEmpty()) return null
		if (!download.startsWith("https://")) {
			// A plain http address is an APK anybody between here and the server
			// can replace on the way, and installing it is the most privileged
			// thing this app can be asked to do. Refusing is the whole of the
			// defence there is, so it is not a warning and a carry on.
			Log.e(TAG, "the published download address is not https, so it is not used")
			return null
		}
		return Available(version, download)
	}

	/** Asks, parses and answers. Called on a thread of its own: nothing here is
	 *  to be done on the one the screen is drawn on. */
	fun fetch(current: String, cancelled: AtomicBoolean): Result {
		if (CHANNEL.isBlank()) return Result.Unreachable
		// Asked before the request rather than only after it, because a check
		// that has been called off has nothing to gain from a socket: the app is
		// on its way out, and the answer will not be read.
		if (cancelled.get()) return Result.Cancelled
		return try {
			val body = get(CHANNEL) ?: return Result.Unreachable
			if (cancelled.get()) return Result.Cancelled
			val found = parse(body, current)
			if (found == null) Result.UpToDate else Result.Found(found)
		} catch (failed: Exception) {
			Log.i(TAG, "nothing could be learned about updates", failed)
			Result.Unreachable
		}
	}

	/** The four answers a check can have. A test names them; only [Unreachable]
	 *  is worth saying out loud, and only when somebody asked. */
	sealed class Result {
		class Found(val available: Available) : Result()
		object UpToDate : Result()
		object Unreachable : Result()
		object Cancelled : Result()
	}

	/** The body at [address], or null. It reads the length out of the reply
	 *  rather than trusting a Content-Length, and stops at a ceiling so that a
	 *  server sending an endless body cannot make this app hold a phone up. */
	private fun get(address: String): String? {
		var connection: HttpURLConnection? = null
		return try {
			connection = (URL(address).openConnection() as HttpURLConnection).apply {
				connectTimeout = CONNECT_TIMEOUT_MS
				readTimeout = READ_TIMEOUT_MS
				instanceFollowRedirects = true
				setRequestProperty("User-Agent", "alt-eloquence-tts")
				setRequestProperty("Accept", "application/json")
			}
			val code = connection.responseCode
			if (code !in 200..299) {
				Log.i(TAG, "the release channel answered $code")
				return null
			}
			connection.inputStream.use { input ->
				val out = java.io.ByteArrayOutputStream(MAX_BODY)
				val buffer = ByteArray(8 * 1024)
				var total = 0
				while (true) {
					val read = input.read(buffer)
					if (read < 0) break
					out.write(buffer, 0, read)
					total += read
					if (total > MAX_BODY) {
						Log.e(TAG, "the release channel sent more than a small file's worth")
						return null
					}
				}
				out.toString("UTF-8")
			}
		} catch (nothingThere: java.io.IOException) {
			Log.i(TAG, "the release channel could not be reached")
			null
		} finally {
			connection?.disconnect()
		}
	}

	/** The same reading, asked for by the download of the APK itself: one place
	 *  where the timeouts and the refusal of a plain http address live. */
	fun openStream(address: String): HttpURLConnection? {
		if (!address.startsWith("https://")) return null
		return try {
			(URL(address).openConnection() as HttpURLConnection).apply {
				connectTimeout = CONNECT_TIMEOUT_MS
				readTimeout = READ_TIMEOUT_MS
				instanceFollowRedirects = true
				setRequestProperty("User-Agent", "alt-eloquence-tts")
			}
		} catch (nothingThere: java.io.IOException) {
			Log.i(TAG, "the download could not be started")
			null
		}
	}

	private const val TAG = "freeeloquencetts"

	/** Sixty-four kilobytes is a great deal for two values in a file, and the
	 *  ceiling is there for the file that is not what it says it is rather
	 *  than for the one that is. */
	private const val MAX_BODY = 64 * 1024
}
