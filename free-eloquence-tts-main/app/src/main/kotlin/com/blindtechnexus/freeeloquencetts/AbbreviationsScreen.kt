package com.blindtechnexus.freeeloquencetts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The abbreviations and expansions a person writes for themselves.
 *
 * A screen of its own rather than a section of the dictionary screen, because
 * an entry here has two more answers to it than a dictionary word does and a
 * form with four fields in it is not a form that belongs beside a list of
 * two-field ones. What is the same is the shape: the list, a row per entry
 * that says all of it, a press that offers to edit or to take it away, and a
 * file that can be brought in or written out.
 *
 * The two extra answers are where the entry matches -- a whole word, the start
 * of one, or anywhere -- and whether the capital letters have to be the ones
 * that were typed. Both are on the form, both are said in the row, and both
 * have a sentence of their own above them, because "starts with" on its own
 * does not say starts with what.
 *
 * Every change is written at once, and the speech service reads the file's own
 * version on the next utterance, so an entry added here is heard the next time
 * anything is spoken -- no restart, and no save button to forget to press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbbreviationsScreen(onBack: () -> Unit) {
	val context = LocalContext.current
	var entries by remember { mutableStateOf(Abbreviations.entries(context)) }
	var notice by remember { mutableStateOf("") }
	var adding by remember { mutableStateOf(false) }
	var chosen by remember { mutableStateOf<Abbreviations.Entry?>(null) }
	var editing by remember { mutableStateOf(false) }

	fun refresh() {
		entries = try {
			Abbreviations.entries(context)
		} catch (broken: Exception) {
			emptyList()
		}
	}

	val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		val resolver = context.contentResolver
		Thread {
			val read = try {
				resolver.openInputStream(uri)?.use { input ->
					val streamed = Abbreviations.parseStream(input)
					if (streamed.isNotEmpty()) {
						streamed
					} else {
						// A file that parsed to nothing is either empty or in
						// a form the stream reader refused; asking again
						// through the text reader is cheap and answers the
						// question rather than leaving it. Capped, not whole:
						// reading the entire file in would hand a hostile or
						// mistaken multi-gigabyte pick to the allocator, and
						// an out-of-memory on this thread is the app gone.
						resolver.openInputStream(uri)?.use { again ->
							Abbreviations.parse(Dictionaries.decode(again))
						} ?: emptyList()
					}
				} ?: emptyList()
			} catch (broken: Exception) {
				emptyList()
			}
			val taken = if (Abbreviations.write(context, read)) read.size else 0
			context.findActivity()?.runOnUiThread {
				refresh()
				val said = context.getString(
					if (taken > 0) R.string.expansions_imported else R.string.expansions_import_nothing,
					taken
				)
				notice = said
				Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			}
		}.apply { isDaemon = true }.start()
	}

	val exportFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("text/plain")
	) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		val text = Abbreviations.format(entries)
		Thread {
			val written = try {
				context.contentResolver.openOutputStream(uri)?.use { sink ->
					sink.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
				}
				true
			} catch (broken: Exception) {
				false
			}
			context.findActivity()?.runOnUiThread {
				val said = context.getString(
					if (written) R.string.expansions_exported else R.string.expansions_export_failed,
					entries.size
				)
				notice = said
				Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			}
		}.apply { isDaemon = true }.start()
	}

	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.expansions_manager),
				onBack = onBack
			)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(bottom = 24.dp)
		) {
			LiveNotice(text = notice, horizontal = 16.dp)
			SectionText(text = stringResource(R.string.expansions_screen_description))

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			) {
				Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
					Text(
						text = stringResource(R.string.expansions_add),
						style = MaterialTheme.typography.labelLarge
					)
				}
				Gap(8.dp)
				Row(modifier = Modifier.fillMaxWidth()) {
					OutlinedButton(
						onClick = { importFile.launch(arrayOf("*/*")) },
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = stringResource(R.string.expansions_import),
							style = MaterialTheme.typography.labelLarge
						)
					}
					Spacer(modifier = Modifier.width(8.dp))
					OutlinedButton(
						onClick = {
							exportFile.launch(context.getString(R.string.expansions_export_name))
						},
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = stringResource(R.string.expansions_export),
							style = MaterialTheme.typography.labelLarge
						)
					}
				}
			}

			Gap(8.dp)
			if (entries.isEmpty()) {
				Text(
					text = stringResource(R.string.expansions_empty),
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp)
				)
			} else {
				for (entry in entries) {
					AbbreviationRow(entry = entry, onPress = { chosen = entry })
				}
			}
		}
	}

	if (adding) {
		AbbreviationDialog(
			title = stringResource(R.string.expansions_add),
			entry = Abbreviations.Entry("", "", Abbreviations.WHOLE_WORD, true),
			confirm = stringResource(R.string.expansions_add),
			onConfirm = { made ->
				if (Abbreviations.add(context, made)) {
					refresh()
					val said = context.getString(R.string.expansions_added, made.key)
					notice = said
					Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
				}
			},
			onDismiss = { adding = false }
		)
	}

	chosen?.let { entry ->
		if (editing) {
			AbbreviationDialog(
				title = stringResource(R.string.expansions_edit),
				entry = entry,
				confirm = stringResource(R.string.save),
				onConfirm = { made ->
					if (Abbreviations.replace(context, entry, made)) {
						refresh()
						val said = context.getString(R.string.expansions_updated, made.key)
						notice = said
						Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
					}
				},
				onDismiss = {
					chosen = null
					editing = false
				}
			)
		} else {
			AbbreviationOptionsDialog(
				entry = entry,
				onEdit = { editing = true },
				onDelete = {
					if (Abbreviations.remove(context, entry)) {
						refresh()
						val said = context.getString(R.string.expansions_deleted, entry.key)
						notice = said
						Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
					}
					chosen = null
					editing = false
				},
				onDismiss = {
					chosen = null
					editing = false
				}
			)
		}
	}
}

/** One entry, said in full: what is written, what is said instead, and the two
 *  answers that decide where in a sentence it is heard. All four are in the
 *  row's own announcement, so a reader swiping the list hears the entry and
 *  not half of it. */
@Composable
private fun AbbreviationRow(entry: Abbreviations.Entry, onPress: () -> Unit) {
	val said = stringResource(
		R.string.expansions_row,
		entry.key,
		entry.say,
		stringResource(matchNameOf(entry.match)),
		stringResource(
			if (entry.ignoreCase) R.string.expansions_any_capitals else R.string.expansions_exact_capitals
		)
	)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(role = Role.Button, onClick = onPress)
			.semantics(mergeDescendants = true) {
				contentDescription = said
				role = Role.Button
			}
			.padding(horizontal = 16.dp, vertical = 12.dp)
	) {
		Text(text = entry.key, style = MaterialTheme.typography.bodyLarge)
		Text(
			text = entry.say,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		Text(
			text = "${stringResource(matchNameOf(entry.match))}, " +
				stringResource(
					if (entry.ignoreCase) {
						R.string.expansions_any_capitals
					} else {
						R.string.expansions_exact_capitals
					}
				),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/** The form for one entry. Four fields, two of them choices, and the choices
 *  are radio buttons rather than a button that opens a list: they are two of
 *  four things being answered at once and a reader filling the form in should
 *  not have to leave it to find out what the options are. */
@Composable
private fun AbbreviationDialog(
	title: String,
	entry: Abbreviations.Entry,
	confirm: String,
	onConfirm: (Abbreviations.Entry) -> Unit,
	onDismiss: () -> Unit
) {
	var key by remember { mutableStateOf(entry.key) }
	var say by remember { mutableStateOf(entry.say) }
	var match by remember { mutableStateOf(entry.match) }
	var ignoreCase by remember { mutableStateOf(entry.ignoreCase) }
	val keys = KeyboardOptions(
		capitalization = KeyboardCapitalization.None,
		autoCorrect = false,
		keyboardType = KeyboardType.Text
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = title) },
		text = {
			Column(
				modifier = Modifier.verticalScroll(rememberScrollState())
			) {
				OutlinedTextField(
					value = key,
					onValueChange = { key = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.expansions_key_hint)) },
					keyboardOptions = keys,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = say,
					onValueChange = { say = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.expansions_say_hint)) },
					keyboardOptions = keys,
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
				)
				SectionText(text = stringResource(R.string.expansions_match_description))
				Column(modifier = Modifier.selectableGroup()) {
					Abbreviations.MATCHES.forEach { option ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.selectable(
									selected = match == option,
									onClick = { match = option },
									role = Role.RadioButton
								)
								.padding(vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							RadioButton(selected = match == option, onClick = null)
							Column(modifier = Modifier.padding(start = 12.dp)) {
								Text(
									text = stringResource(matchNameOf(option)),
									style = MaterialTheme.typography.bodyLarge
								)
								Text(
									text = stringResource(matchDescriptionOf(option)),
									style = MaterialTheme.typography.bodySmall
								)
							}
						}
					}
				}
				SwitchRow(
					label = stringResource(R.string.expansions_case_label),
					description = stringResource(R.string.expansions_case_description),
					checked = ignoreCase,
					onChange = { ignoreCase = it }
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					val written = key.trim()
					val said = say.trim()
					if (written.isNotEmpty() && said.isNotEmpty()) {
						onConfirm(Abbreviations.Entry(written, said, match, ignoreCase))
						onDismiss()
					}
				}
			) {
				Text(text = confirm)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(R.string.cancel))
			}
		}
	)
}

/** What to do with one entry: change it, or take it away. */
@Composable
private fun AbbreviationOptionsDialog(
	entry: Abbreviations.Entry,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = entry.key) },
		text = { Text(text = stringResource(R.string.expansions_choice)) },
		confirmButton = {
			TextButton(onClick = onEdit) {
				Text(text = stringResource(R.string.expansions_edit))
			}
		},
		dismissButton = {
			TextButton(onClick = onDelete) {
				Text(text = stringResource(R.string.expansions_delete))
			}
		}
	)
}

private fun matchNameOf(match: Int): Int = when (match) {
	Abbreviations.STARTS_WITH -> R.string.expansions_match_starts
	Abbreviations.ANYWHERE -> R.string.expansions_match_anywhere
	else -> R.string.expansions_match_whole
}

private fun matchDescriptionOf(match: Int): Int = when (match) {
	Abbreviations.STARTS_WITH -> R.string.expansions_match_starts_description
	Abbreviations.ANYWHERE -> R.string.expansions_match_anywhere_description
	else -> R.string.expansions_match_whole_description
}
