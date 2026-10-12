package com.blindtechnexus.freeeloquencetts

import android.provider.OpenableColumns
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The dictionary manager, which is the whole of the dictionary feature.
 *
 * There are two kinds of words here. The words typed on this screen live in
 * the one list, added, edited and taken away one at a time. A file brought
 * in from elsewhere -- a .dict shared online, an export from another phone --
 * is kept beside it under its own name instead of being poured into the same
 * list: the imported dictionaries below say what came from where, and tapping
 * one opens it, with every word it holds and the same options this screen
 * has. Both are heard the next time anything is spoken.
 *
 * Every change is written at once, and the speech service notices the file's
 * own revision on the next utterance, so there is no save and no restart. A
 * word matches whatever capital letters it is written in: "DOJ" and "doj" are
 * the same entry, and both are heard.
 *
 * Imports are streaming, which is what makes files of lakhs of words -- and
 * the worst of them millions -- an import rather than a crash. The file is
 * read a line at a time and written straight to its own file, the screen says
 * how far it has got, and what does not fit is counted and reported rather
 * than kept.
 *
 * The search above the words finds either kind where it stands: a word by
 * what is written or what is said, and a file by its name, whatever capital
 * letters anything is written in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(onBack: () -> Unit, onOpenFile: (String) -> Unit) {
	val context = LocalContext.current
	var words by remember { mutableStateOf(UserDictionary.words(context)) }
	var files by remember { mutableStateOf(ExtraDictionaries.list(context)) }
	var counts by remember { mutableStateOf(emptyMap<String, Int>()) }
	var notice by remember { mutableStateOf("") }
	// What the voice is actually matching with: how many of the dictionary's
	// words are in force, and how many were left out for want of the room
	// this phone's heap could give them. Counted rather than assumed,
	// because the screen says "Imported 1,000,000 words" either way and the
	// second number is the one that explains a word not being said.
	var inForce by remember { mutableStateOf<Pair<Int, Int>?>(null) }
	// What the search box holds. Both lists below -- the words and the
	// imported files -- answer it, whatever capital letters anything is
	// written in.
	var query by remember { mutableStateOf("") }
	var adding by remember { mutableStateOf(false) }
	var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
	var editingForEdit by remember { mutableStateOf(false) }
	var importing by remember { mutableStateOf<String?>(null) }
	val cancelImport = remember { AtomicBoolean(false) }

	fun refresh() {
		words = try {
			UserDictionary.words(context)
		} catch (e: Exception) {
			emptyList()
		}
		files = ExtraDictionaries.list(context)
	}

	fun recount() {
		val names = files.map { it.name }
		Thread {
			val made = HashMap<String, Int>()
			for (info in ExtraDictionaries.list(context)) {
				if (info.name !in names) continue
				made[info.name] = ExtraDictionaries.count(info)
			}
			context.findActivity()?.runOnUiThread { counts = made }
		}.apply { isDaemon = true }.start()
	}

	/** Asks the dictionary for the index it is matching with, off the main
	 *  thread: a rebuild that is still running is waited for here, and the
	 *  screen has the numbers of the last one until this answers. */
	fun recountInForce() {
		Thread {
			val snapshot = try {
				UserDictionary.snapshot(context)
			} catch (broken: Exception) {
				null
			}
			val made = snapshot?.let { it.index.size to it.dropped }
			context.findActivity()?.runOnUiThread { inForce = made }
		}.apply { isDaemon = true }.start()
	}

	// The counts are a pass over every file, which is work for a thread
	// rather than for the composition -- and for after the screen is up,
	// since the names are what is swiped through first.
	LaunchedEffect(Unit) {
		recount()
		recountInForce()
	}

	val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		if (ExtraDictionaries.list(context).size >= ExtraDictionaries.MAX_FILES) {
			val said = context.getString(R.string.dictionary_files_full)
			notice = said
			Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			return@rememberLauncherForActivityResult
		}
		val display = try {
			context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
				val at = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
				if (at >= 0 && cursor.moveToFirst()) cursor.getString(at) else null
			}
		} catch (e: Exception) {
			null
		} ?: "dictionary"
		cancelImport.set(false)
		importing = context.getString(R.string.dictionary_importing, 0)
		Thread {
			val result = try {
				context.contentResolver.openInputStream(uri)?.use { input ->
					ExtraDictionaries.importStream(
						context,
						input,
						display,
						progress = { total, _ ->
							val said = context.getString(R.string.dictionary_importing, total)
							context.findActivity()?.runOnUiThread {
								importing = said
							}
						},
						isCancelled = { cancelImport.get() }
					)
				}
			} catch (e: Exception) {
				null
			}
			context.findActivity()?.runOnUiThread {
				try {
					importing = null
					refresh()
			recountInForce()
					val said = when {
						result == null -> context.getString(R.string.dictionary_import_nothing)
						result.truncated -> context.getString(
							R.string.dictionary_imported_file_truncated,
							result.kept,
							result.total,
							result.name
						)
						else -> context.getString(
							R.string.dictionary_imported_file,
							result.kept,
							result.name
						)
					}
					notice = said
					Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
					recount()
				} catch (_: Exception) {
					Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
				}
			}
		}.apply { isDaemon = true }.start()
	}
	val exportFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/octet-stream")
	) { uri ->
		if (uri != null) {
			val snapshot = words
			Thread {
				val exported = try {
					context.contentResolver.openOutputStream(uri)?.use { sink ->
						// Stream write to avoid building huge string
						sink.bufferedWriter(Charsets.UTF_8).use { w ->
							for ((word, say) in snapshot) {
								if (word.isEmpty() || say.isEmpty()) continue
								w.write(word.replace("\n", " ").replace("\t", " "))
								w.write('\t'.code)
								w.write(say.replace("\n", " "))
								w.write('\n'.code)
							}
						}
					}
					true
				} catch (e: Exception) {
					false
				}
				context.findActivity()?.runOnUiThread {
					try {
						notice = if (exported) {
							context.getString(R.string.dictionary_exported, snapshot.size)
						} else {
							context.getString(R.string.dictionary_export_failed)
						}
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					} catch (_: Exception) {
						Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
					}
				}
			}.apply { isDaemon = true }.start()
		}
	}

	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.dictionary_manager),
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
			SectionText(text = stringResource(R.string.dictionary_screen_description))

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			) {
				Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
					Text(
						text = stringResource(R.string.dictionary_add),
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
							text = stringResource(R.string.dictionary_import),
							style = MaterialTheme.typography.labelLarge
						)
					}
					Spacer(modifier = Modifier.width(8.dp))
					OutlinedButton(
						onClick = {
							exportFile.launch(
								context.getString(R.string.dictionary_export_name)
							)
						},
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = stringResource(R.string.dictionary_export),
							style = MaterialTheme.typography.labelLarge
						)
					}
				}
					inForce?.let { (kept, dropped) ->
					Gap(4.dp)
					Text(
						text = if (dropped > 0) {
							stringResource(R.string.dictionary_in_force_some, kept, dropped)
						} else {
							stringResource(R.string.dictionary_in_force, kept)
						},
						style = MaterialTheme.typography.bodySmall
					)
				}
				importing?.let { progress ->
					Gap(8.dp)
					Row(modifier = Modifier.fillMaxWidth()) {
						Text(
							text = progress,
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.weight(1f)
						)
						Spacer(modifier = Modifier.width(8.dp))
						TextButton(onClick = { cancelImport.set(true) }) {
							Text(text = stringResource(R.string.cancel))
						}
					}
				}
			}

			Gap(8.dp)
			OutlinedTextField(
				value = query,
				onValueChange = { query = it },
				singleLine = true,
				label = { Text(text = stringResource(R.string.dictionary_search_hint)) },
				keyboardOptions = KeyboardOptions(
					capitalization = KeyboardCapitalization.None,
					autoCorrect = false,
					keyboardType = KeyboardType.Text
				),
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			)
			Gap(8.dp)
			// Both lists answer the search: a word matches what is written
			// or what is said, and a file matches its name. Empty search is
			// both lists whole, exactly as they always were.
			val want = query.trim()
			val shownWords = if (want.isEmpty()) words else words.filter {
				it.first.contains(want, ignoreCase = true) ||
					it.second.contains(want, ignoreCase = true)
			}
			val shownFiles = if (want.isEmpty()) files else files.filter {
				it.name.contains(want, ignoreCase = true)
			}
			if (want.isEmpty() && words.isEmpty()) {
				Text(
					text = stringResource(R.string.dictionary_empty),
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp)
				)
			} else {
				if (want.isNotEmpty()) {
					Text(
						text = stringResource(R.string.dictionary_search_words, shownWords.size),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 16.dp, vertical = 4.dp)
					)
				}
				for ((word, say) in shownWords) {
					WordRow(
						word = word,
						say = say,
						onPress = { editing = word to say }
					)
				}
			}

			Gap(16.dp)
			SectionHeading(text = stringResource(R.string.dictionary_files_heading))
			SectionText(text = stringResource(R.string.dictionary_files_description))
			if (want.isEmpty() && files.isEmpty()) {
				Text(
					text = stringResource(R.string.dictionary_files_empty),
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 12.dp)
				)
			} else {
				for (info in shownFiles) {
					DictionaryFileRow(
						name = info.name,
						subtitle = counts[info.name]?.let {
							context.getString(R.string.dictionary_file_words, it)
						} ?: "",
						onPress = { onOpenFile(info.name) }
					)
				}
			}
		}
	}

	if (adding) {
		WordDialog(
			title = stringResource(R.string.dictionary_add),
			word = "",
			say = "",
			confirm = stringResource(R.string.dictionary_add),
			onConfirm = { word, say ->
				if (UserDictionary.add(context, word.trim(), say.trim())) {
					refresh()
			recountInForce()
					val said = context.getString(R.string.dictionary_added, word.trim())
					notice = said
					Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
				}
			},
			onDismiss = { adding = false }
		)
	}

	editing?.let { (word, say) ->
		if (editingForEdit) {
			WordDialog(
				title = stringResource(R.string.dictionary_edit),
				word = word,
				say = say,
				confirm = stringResource(R.string.save),
				onConfirm = { newWord, newSay ->
					if (UserDictionary.replace(context, word, newWord.trim(), newSay.trim())) {
						refresh()
			recountInForce()
						notice = context.getString(R.string.dictionary_updated, newWord.trim())
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					}
				},
				onDismiss = {
					editing = null
					editingForEdit = false
				}
			)
		} else {
			WordOptionsDialog(
				word = word,
				onEdit = { editingForEdit = true },
				onDelete = {
					if (UserDictionary.remove(context, word)) {
						refresh()
			recountInForce()
						notice = context.getString(R.string.dictionary_deleted, word)
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					}
					editing = null
					editingForEdit = false
				},
				onDismiss = {
					editing = null
					editingForEdit = false
				}
			)
		}
	}
}

/** One word of the dictionary: what is written, and what is said. */
@Composable
internal fun WordRow(word: String, say: String, onPress: () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(role = Role.Button, onClick = onPress)
			.semantics(mergeDescendants = true) {
				contentDescription = "$word, said as $say"
				role = Role.Button
			}
			.padding(horizontal = 16.dp, vertical = 12.dp)
	) {
		Text(text = word, style = MaterialTheme.typography.bodyLarge)
		Text(
			text = say,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/** One imported dictionary: its name, and how many words it holds. */
@Composable
private fun DictionaryFileRow(name: String, subtitle: String, onPress: () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.clickable(role = Role.Button, onClick = onPress)
			.semantics(mergeDescendants = true) {
				contentDescription = if (subtitle.isEmpty()) name else "$name, $subtitle"
				role = Role.Button
			}
			.padding(horizontal = 16.dp, vertical = 12.dp)
	) {
		Text(text = name, style = MaterialTheme.typography.bodyLarge)
		if (subtitle.isNotEmpty()) {
			Text(
				text = subtitle,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

/** The form for a word and what to say instead of it. */
@Composable
internal fun WordDialog(
	title: String,
	word: String,
	say: String,
	confirm: String,
	onConfirm: (String, String) -> Unit,
	onDismiss: () -> Unit
) {
	var current by remember { mutableStateOf(word) }
	var replacement by remember { mutableStateOf(say) }
	val keys = KeyboardOptions(
		capitalization = KeyboardCapitalization.None,
		autoCorrect = false,
		keyboardType = KeyboardType.Text
	)
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = title) },
		text = {
			Column {
				OutlinedTextField(
					value = current,
					onValueChange = { current = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.dictionary_word_hint)) },
					keyboardOptions = keys,
					modifier = Modifier.fillMaxWidth()
				)
				OutlinedTextField(
					value = replacement,
					onValueChange = { replacement = it },
					singleLine = true,
					label = { Text(text = stringResource(R.string.dictionary_say_hint)) },
					keyboardOptions = keys,
					modifier = Modifier
						.fillMaxWidth()
						.padding(top = 8.dp)
				)
			}
		},
		confirmButton = {
			TextButton(
				onClick = {
					if (current.isNotBlank() && replacement.isNotBlank()) {
						onConfirm(current, replacement)
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

/** What to do with one word: change it, or take it away. */
@Composable
internal fun WordOptionsDialog(
	word: String,
	onEdit: () -> Unit,
	onDelete: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(text = word) },
		text = { Text(text = stringResource(R.string.dictionary_word_choice)) },
		confirmButton = {
			TextButton(onClick = onEdit) {
				Text(text = stringResource(R.string.dictionary_edit))
			}
		},
		dismissButton = {
			TextButton(onClick = onDelete) {
				Text(text = stringResource(R.string.dictionary_delete))
			}
		}
	)
}
