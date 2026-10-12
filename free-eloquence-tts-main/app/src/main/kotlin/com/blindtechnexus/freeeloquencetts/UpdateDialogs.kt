package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * The three dialogs an update turns into, and nothing else.
 *
 * They are one file rather than three screens because they are not screens: a
 * dialog is a question that stops what is behind it, and a question that stops a
 * screen reader in the middle of a list of settings has to be worth it. Each of
 * the three is asked once per answer, the answer is a button or two, and the
 * state that decides which one is up is [Updater]'s rather than any of the
 * screens'. The whole of that arrangement is what makes the update path work with
 * the screen reader on and no eyes on the phone: nothing here is a progress bar
 * to watch, and nothing here is a bar at all without a sentence saying what it
 * means.
 *
 * The middle one is the one with a real risk of being annoying: a download that
 * announces itself four times a second. So the number of bytes is what changes,
 * and it changes with the progress the file is arriving at -- the bar below it is
 * decoration for whoever can see it, and the sentence is the same fact in the
 * form a screen reader will read out when it is swiped past rather than when it
 * changes.
 */
@Composable
internal fun UpdateDialogs() {
	val state = Updater.state
	val context = LocalContext.current
	val activity = androidx.activity.compose.LocalActivity.current
	// Both are keyed on the state rather than remembered once for the life of
	// the screen: a second update arriving has to be able to say its own piece,
	// and an acknowledgement of the first is not an answer about the second.
	var outcome by remember(state) { mutableStateOf<Updater.Outcome?>(null) }
	var acknowledged by remember(state) { mutableStateOf(false) }

	when (state) {
		is Updater.State.Available -> if (Updater.shouldShowDialog()) {
			AvailableDialog(state.version)
		}

		is Updater.State.Downloading -> DownloadingDialog(state)

		is Updater.State.Ready -> {
			// The installer is asked for as soon as the bytes are down, which
			// is what the whole of the download was for, and from here the
			// screen is the installer's own. Asked once per answer rather than
			// once per file: the file is the same one after a trip to the
			// permission screen, and the second answer about it is exactly
			// when a retry is wanted. A new State.Ready is a new question about
			// installing; the same one recomposed is not, which is what keeps
			// this from being a loop that opens the installer again and again.
			LaunchedEffect(state) {
				activity?.let { outcome = Updater.install(it) }
			}
			ReadyDialog(outcome, acknowledged) { acknowledged = true }
		}

		else -> Unit
	}
}

/**
 * The offer, in the words the release channel is answered with.
 *
 * "Later" is the whole of what refuses an update, and it is deliberately not a
 * setting: the same version is asked about again the next time the app is opened,
 * so that a refusal made in a hurry is not a decision made for ever, and so that
 * nobody has to find a switch to be allowed to change their mind.
 */
@Composable
private fun AvailableDialog(version: String) {
	AlertDialog(
		onDismissRequest = { Updater.dismiss() },
		title = { Text(text = stringResource(R.string.update_available_title)) },
		text = {
			Column {
				Text(
					text = stringResource(R.string.update_available_message, version),
					style = MaterialTheme.typography.bodyLarge
				)
				Text(
					text = stringResource(R.string.update_download_question),
					style = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.fillMaxWidth()
				)
			}
		},
		confirmButton = {
			TextButton(onClick = { Updater.download() }) {
				Text(text = stringResource(R.string.update_download))
			}
		},
		dismissButton = {
			TextButton(onClick = { Updater.ignore() }) {
				Text(text = stringResource(R.string.update_later))
			}
		}
	)
}

/** The bytes arriving, with the number of them where a bar would be. */
@Composable
private fun DownloadingDialog(state: Updater.State.Downloading) {
	val total = state.total
	val text = if (total > 0) {
		stringResource(
			R.string.update_progress_of,
			megabytes(state.done),
			megabytes(total)
		)
	} else {
		stringResource(R.string.update_progress_so_far, megabytes(state.done))
	}
	AlertDialog(
		onDismissRequest = { Updater.cancelDownload() },
		title = { Text(text = stringResource(R.string.update_downloading_title)) },
		text = {
			Column {
				Text(text = text, style = MaterialTheme.typography.bodyLarge)
				if (total > 0) {
					LinearProgressIndicator(
						progress = { Updater.fraction() },
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 12.dp)
					)
				} else {
					LinearProgressIndicator(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 12.dp)
					)
				}
			}
		},
		confirmButton = {
			TextButton(onClick = { Updater.cancelDownload() }) {
				Text(text = stringResource(R.string.update_cancel))
			}
		}
	)
}

/** What the file is for: the installer, or the permission that stands in front
 *  of it, or the sentence that says neither was reached. */
@Composable
private fun ReadyDialog(outcome: Updater.Outcome?, acknowledged: Boolean, onAcknowledge: () -> Unit) {
	if (acknowledged) return
	when (outcome) {
		Updater.Outcome.NeedsPermission -> AlertDialog(
			// The back key closes this one too. Nothing is lost by closing it --
			// the file stays in the cache -- and a dialog that cannot be got rid
			// of is a dialog that has to be read again.
			onDismissRequest = onAcknowledge,
			title = { Text(text = stringResource(R.string.update_permission_title)) },
			text = { Text(text = stringResource(R.string.update_permission_message)) },
			confirmButton = {
				TextButton(onClick = onAcknowledge) {
					Text(text = stringResource(R.string.update_ok))
				}
			}
		)

		Updater.Outcome.Refused, Updater.Outcome.NoFile -> AlertDialog(
			onDismissRequest = onAcknowledge,
			title = { Text(text = stringResource(R.string.update_ready_title)) },
			text = {
				Text(
					text = stringResource(
						if (outcome == Updater.Outcome.NoFile) R.string.update_nothing_to_install
						else R.string.update_refused
					)
				)
			},
			confirmButton = {
				TextButton(onClick = onAcknowledge) {
					Text(text = stringResource(R.string.update_ok))
				}
			}
		)

		// Started, or nothing yet: the installer's screen is up, or is about to
		// be, and a second dialog would only be something to find one's way out
		// of. The file stays where it is until then.
		else -> Unit
	}
}

/** A size in megabytes to one decimal place, which is what a download is worth
 *  hearing in. Bytes are too many digits and a percentage is a fact about the
 *  file rather than about the wait. Shared with the About screen, which says the
 *  same progress in its own words: one arithmetic rather than two that can be
 *  made to disagree about how far the download has got. */
internal fun megabytes(bytes: Long): String {
	val mb = bytes / (1024.0 * 1024.0)
	return if (bytes < 1024 * 100) {
		String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
	} else {
		String.format(Locale.getDefault(), "%.1f MB", mb)
	}
}
