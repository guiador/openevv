package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The update check, the download and what the screens are to show about them.
 *
 * Three things are decided by where this lives, and all three are about not
 * taking a person's attention when they have not asked for anything.
 *
 * It is asked on every launch, on a thread of its own: the engine is what the app
 * is for, and nothing that happens before the first syllable is spoken should
 * wait for a server in another country. Nothing is said when the answer is that
 * the app is up to date, and nothing is said when there is no network -- a check
 * that announces itself when it has nothing to report is how an optional thing
 * becomes a thing that happens to you.
 *
 * The state is held here rather than in the screen that shows it, because a
 * screen goes away when the phone is turned over and a download does not. An
 * update arriving while the screen is off still has its dialog when it comes
 * back; a download broken by a rotation is the sort of bug that makes an "update
 * available" feature worse than no feature at all.
 *
 * The one exception to saying nothing is a manual ask: the row on the About
 * screen gets an answer whatever it is, including "nothing could be reached",
 * because a button that is silent has not been pressed as far as anybody can
 * tell.
 *
 * No library is used. One small file on a git host is read with what Android
 * already has, in about as many lines as it takes to say what it means; the
 * dependencies an HTTP client and a JSON parser would be are what a project of
 * this size spends its download size on for no other reason. The install itself
 * is Android's, in [Installer], for the reasons said there.
 */
internal object Updater {
	private val enabled = false

	/** What the last answer was, for the screens to turn into sentences. The
	 *  wording is a resource rather than a field so that it is translated with
	 *  the rest of the app. */
	sealed class State {
		object Idle : State()
		object Checking : State()
		object UpToDate : State()
		object Unreachable : State()

		/** What to offer, and where the bytes are. Both came from the release
		 *  channel's file and both stay with the offer, so that a dialog cannot
		 *  find itself naming one version and downloading another. */
		class Available(val version: String, val downloadUrl: String) : State()

		/** Bytes and the length that was promised, or a negative length when the
		 *  server would not say. */
		class Downloading(val done: Long, val total: Long) : State()

		class Ready(val file: File, val size: Long) : State()
		object Failed : State()

		/** An answer the screens are to stop showing: "not now", or a dialog
		 *  closed by the back key. */
		object Dismissed : State()
	}

	/** Watched by the composition, so that a write here is enough to redraw the
	 *  dialog. Written from the check thread, which the snapshot system handles
	 *  as well as it handles the main one. */
	var state by mutableStateOf<State>(State.Idle)
		private set

	/** What a hand-asked check found, which the About screen says out loud and
	 *  the dialog does not care about. */
	var notice by mutableStateOf(Notice.NONE)
		private set

	/** Only the four a manual ask can end in. The launch check does not set
	 *  these, because nothing pressed a button and nothing is waiting for an
	 *  answer; the About screen reads them so that the row which asks again
	 *  answers in its own words even when the standing state has not moved. */
	enum class Notice { NONE, UP_TO_DATE, UNREACHABLE }

	/** The version the person has said "not now" to, for this run of the app.
	 *  Asked about again the next launch, which is the honest reading of the
	 *  word. */
	var ignored: String? = null
		private set

	private val busy = AtomicBoolean(false)
	private val downloading = AtomicBoolean(false)
	private val cancelled = AtomicBoolean(false)

	private lateinit var appContext: Context
	private var askedThisLaunch = false

	/** Called once, from the activity, before anything is asked. The application
	 *  context is what the threads hold: they outlive the activity that started
	 *  them and must not hold on to it, and holding it would be a leak with a
	 *  window on the end of it. */
	fun attach(context: Context) {
		if (::appContext.isInitialized) return
		appContext = context.applicationContext
	}

	/** The background asking, on the way into a launch. [force] is the manual
	 *  row: it asks whatever has already been asked, and it is answered whatever
	 *  the answer is. */
	fun check(force: Boolean = false) {
		if (!enabled) return
		if (!::appContext.isInitialized) return
		if (!force) {
			if (askedThisLaunch) return
			askedThisLaunch = true
		}
		if (!busy.compareAndSet(false, true)) return
		// A download broken off earlier leaves the flag set, and the check reads
		// it too: whatever was cancelled then is not cancelled now.
		cancelled.set(false)
		if (!force && state is State.Available) {
			busy.set(false)
			return
		}
		if (state !is State.Ready) state = State.Checking
		notice = Notice.NONE
		if (force) {
			// Asking again is the taking back of a "not now": the version that
			// was refused earlier in this run of the app is offered again,
			// because a person who has just pressed the row that asks has said
			// so in the clearest way there is on this screen. There is no
			// switch to find first, which is the point of "Later" meaning now
			// rather than never.
			ignored = null
		}
		val thread = Thread({
			val answer = try {
				UpdateInfo.fetch(BuildConfig.VERSION_NAME, cancelled)
			} catch (crash: Throwable) {
				// The one place an update mechanism is allowed to be this blunt:
				// a check that throws is not worth losing the app over, and
				// there is nothing on the screen for the crash to explain.
				Log.e(TAG, "the update check went wrong", crash)
				UpdateInfo.Result.Unreachable
			}
			busy.set(false)
			state = when (answer) {
				is UpdateInfo.Result.Found -> {
					val cached = ApkDownload.fileFor(appContext, answer.available.version)
					if (cached.exists() && Installer.isInstallable(appContext, cached) &&
						(force || state is State.Ready)
					) {
						// The file is already here, and somebody has just asked:
						// offer the install rather than ten more megabytes. This is
						// the step the permission screen's own sentence promises --
						// allow the app, press the row again, install what was
						// fetched before -- and it is done for a press and for a
						// state that was already holding the file, because a launch
						// that went straight to the installer without being asked is
						// not an update check but a nag.
						State.Ready(cached, cached.length())
					} else {
						State.Available(answer.available.version, answer.available.downloadUrl)
					}
				}
				UpdateInfo.Result.UpToDate -> {
					if (force) notice = Notice.UP_TO_DATE
					State.UpToDate
				}
				else -> {
					if (force) notice = Notice.UNREACHABLE
					State.Unreachable
				}
			}
		}, "evv-update")
		thread.isDaemon = true
		thread.start()
	}

	/** "Later", which is not no for ever. */
	fun ignore() {
		ignored = (state as? State.Available)?.version
		state = State.Dismissed
	}

	/** Whether the dialog is to be shown for what has been found: an answer of
	 *  "available" that the person has already refused this run is not to come
	 *  back at them from another screen. */
	fun shouldShowDialog(): Boolean = when (val now = state) {
		is State.Available -> now.version != ignored
		is State.Downloading, is State.Ready -> true
		else -> false
	}

	/** The bytes, on a thread of its own, with the progress written where the
	 *  dialog can see it. Answers whether the asking was taken up at all, a
	 *  second press while one is running being not a thing. */
	fun download(): Boolean {
		if (!enabled) return false
		val available = state as? State.Available ?: return false
		if (!downloading.compareAndSet(false, true)) return false
		cancelled.set(false)
		val file = ApkDownload.fileFor(appContext, available.version)
		if (file.exists() && Installer.isInstallable(appContext, file)) {
			// It is already here. Somebody pressed the button once, the bytes
			// came, and something between then and now -- the permission
			// Android insists on, a screen that closed, a phone that restarted
			// the app -- put them back at the question rather than at the
			// answer. Fetching the same 50 MB again on a phone that is paying
			// for each of them by the megabyte would be the app's mistake, not
			// theirs.
			state = State.Ready(file, file.length())
			return true
		}
		state = State.Downloading(0, -1)
		val thread = Thread({
			val outcome = try {
				ApkDownload.to(appContext, available.downloadUrl, file, cancelled) { progress ->
					state = State.Downloading(progress.done, progress.total)
				}
			} catch (crash: Throwable) {
				Log.e(TAG, "the download went wrong", crash)
				ApkDownload.Outcome.Failed
			}
			downloading.set(false)
			state = when (outcome) {
				is ApkDownload.Outcome.Installed -> State.Ready(outcome.file, outcome.size)
				ApkDownload.Outcome.Cancelled -> State.Dismissed
				else -> State.Failed
			}
		}, "evv-download")
		thread.isDaemon = true
		thread.start()
		return true
	}

	/** Stops a download and throws away what has come so far -- the dialog's
	 *  Cancel button, and the only way out of a download that has started. */
	fun cancelDownload() {
		cancelled.set(true)
		downloading.set(false)
		(state as? State.Ready)?.let { runCatching { it.file.delete() } }
		if (state is State.Downloading) state = State.Dismissed
	}

	/** The last step, and the one Android insists on being asked about: the
	 *  installer's own screen, with this app's allowance for unknown apps asked
	 *  for first when it has not been given. */
	fun install(activity: Activity): Outcome {
		if (!enabled) return Outcome.Refused
		val ready = state as? State.Ready ?: return Outcome.NoFile
		if (Installer.blockedOnPermission(activity)) {
			return if (Installer.askToAllow(activity)) Outcome.NeedsPermission else Outcome.Refused
		}
		return if (Installer.install(activity, ready.file)) Outcome.Started else Outcome.Refused
	}

	sealed class Outcome {
		object Started : Outcome()
		object NeedsPermission : Outcome()
		object NoFile : Outcome()
		object Refused : Outcome()
	}

	/** The fraction of the download that has arrived, or nought when the length
	 *  was never promised. A bar that cannot fill is worse to listen to than a
	 *  number that says what has arrived, so a screen that gets nought back is
	 *  the screen that says megabytes instead. */
	fun fraction(): Float {
		val now = state as? State.Downloading ?: return 0f
		if (now.total <= 0) return 0f
		return (now.done.toFloat() / now.total.toFloat()).coerceIn(0f, 1f)
	}

	/** For a log line and a test. */
	val currentVersion: String get() = BuildConfig.VERSION_NAME

	/** A screen that has closed the dialog by other means than answering it.
	 *  Only ever an idle answer: a download in progress is not something the
	 *  back key takes away, and the file is not left in the cache with nobody
	 *  holding the way to it. */
	fun dismiss() {
		when (state) {
			is State.Downloading, is State.Ready -> Unit
			else -> state = State.Dismissed
		}
	}

	private const val TAG = "freeeloquencetts"
}
