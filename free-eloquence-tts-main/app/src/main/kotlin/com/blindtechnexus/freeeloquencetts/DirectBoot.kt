package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Where the engine keeps what it needs before the phone has been unlocked.
 *
 * A screen reader is the first thing wanted on a locked phone and the last
 * thing that can wait for a PIN, so the speech service is marked direct-boot
 * aware and starts while the user's own storage is still encrypted. Ordinary
 * app storage is not there yet at that point: reading it throws, and settings
 * read through it would come back as the defaults, so the voice would be the
 * wrong one until somebody unlocked the phone and restarted the engine.
 *
 * Device-protected storage is readable from the moment the phone boots, and is
 * what everything the engine reads at startup lives in. It is a different
 * directory rather than a different kind of file, so what is stored there is
 * moved once, the first time a build with this in it runs after an unlock.
 *
 * The answer is worked out once and kept. Both halves of it -- asking the
 * system for a second context and moving the settings into it -- are file
 * operations, and the speech service asks for the dictionary's file on every
 * utterance to decide whether it has changed. Paid once per process that is
 * nothing; paid once per utterance it is a few milliseconds of latency in
 * front of every word the phone says, which is the one cost a screen reader
 * notices. The memo is keyed on the application context, so it cannot be read
 * across two processes or after the app object has been replaced.
 */
object DirectBoot {

	@Volatile
	private var storage: Context? = null

	@Volatile
	private var forApp: Context? = null

	@Volatile
	private var dictionaryDir: File? = null

	/** The context to read settings and the files they name through. */
	fun storage(context: Context): Context {
		val app = context.applicationContext
		storage?.let { if (forApp === app) return it }
		return synchronized(this) {
			storage?.let { if (forApp === app) return it }
			val made = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) app else {
				app.createDeviceProtectedStorageContext() ?: app
			}
			// Only when the move has not been done is it worth asking. The
			// call is a rename attempt, and a rename of a file that is
			// already where it wants to be says nothing except that it looked.
			if (made !== app) made.moveSharedPreferencesFrom(app, prefsName(app))
			storage = made
			forApp = app
			made
		}
	}

	/** The directory the user dictionary lives in, wherever it is being kept. */
	fun dictionaries(context: Context): File {
		val app = context.applicationContext
		dictionaryDir?.let { if (forApp === app) return it }
		return synchronized(this) {
			val into = File(storage(context).filesDir, DICTIONARIES)
			dictionaryDir = into
			into
		}
	}

	/**
	 * The dictionary directory, with the directory made if it is not there.
	 *
	 * [storage] is asked for the directory rather than for the permission to
	 * create it: `mkdirs` on a directory that already exists is a system call
	 * for nothing, and the service asks on every utterance whether the file
	 * inside has changed.
	 */
	fun dictionariesReady(context: Context): File {
		val into = dictionaries(context)
		if (!into.isDirectory) into.mkdirs()
		return into
	}

	/** Forgets what was worked out. Only a test needs this, after it has moved
	 *  the app's files or replaced the context under it. */
	fun forget() {
		synchronized(this) {
			storage = null
			forApp = null
			dictionaryDir = null
		}
	}

	private fun prefsName(app: Context) = "${app.packageName}_preferences"

	const val DICTIONARIES = "dictionaries"
}
