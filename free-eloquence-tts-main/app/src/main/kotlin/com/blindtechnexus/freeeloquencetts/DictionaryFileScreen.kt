package com.blindtechnexus.freeeloquencetts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * One imported dictionary: every word it holds, and the same options.
 *
 * The list is the file's own words, searched, added to, edited, taken away,
 * added to from another file, and written back out -- everything the manager
 * does with the words typed on it, because a file kept under its own name is
 * no less a dictionary for it.
 *
 * Files of lakhs of words are why the list is a lazy one that shows the
 * first [SHOWN_WITHOUT_SEARCH] words and says how many more there are: ten
 * thousand rows composed at once is a screen that never arrives. The search
 * above it reads the whole file a line at a time and keeps the matches, so
 * every word is still one search away however many the file holds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryFileScreen(name: String, onBack: () -> Unit, onDeleted: () -> Unit) {
	val context = LocalContext.current
	var shown by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
	var total by remember { mutableStateOf(-1) }
	var query by remember { mutableStateOf("") }
	var searching by remember { mutableStateOf(false) }
	var notice by remember { mutableStateOf("") }
	var adding by remember { mutableStateOf(false) }
	var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
	var editingForEdit by remember { mutableStateOf(false) }
	var askingDelete by remember { mutableStateOf(false) }
	var importing by remember { mutableStateOf<String?>(null) }
	val cancelImport = remember { AtomicBoolean(false) }
	val searchTurn = remember { AtomicInteger(0) }

	fun refresh() {
		val mine = searchTurn.incrementAndGet()
		Thread {
			val q = query
			val entries = if (q.isBlank()) {
				ExtraDictionaries.entries(context, name, SHOWN_WITHOUT_SEARCH)
			} else {
				ExtraDictionaries.search(context, name, q, SEARCH_RESULTS)
			}
			val count = ExtraDictionaries.fileFor(context, name)?.let {
				ExtraDictionaries.count(ExtraDictionaries.Info(name, it, it.length(), it.lastModified()))
			} ?: 0
			context.findActivity()?.runOnUiThread {
				if (mine == searchTurn.get()) {
					shown = entries
					total = count
					searching = false
				}
			}
		}.apply { isDaemon = true }.start()
	}

	LaunchedEffect(name) { refresh() }

	fun searchFor(q: String) {
		query = q
		searching = true
		refresh()
	}

	val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
		if (uri == null) return@rememberLauncherForActivityResult
		cancelImport.set(false)
		importing = context.getString(R.string.dictionary_importing, 0)
		Thread {
			val result = try {
				context.contentResolver.openInputStream(uri)?.use { input ->
					ExtraDictionaries.appendImport(
						context,
						name,
						input,
						progress = { offered, _ ->
							val said = context.getString(R.string.dictionary_importing, offered)
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
				} catch (_: Exception) {
					Toast.makeText(context, context.getString(R.string.dictionary_import_failed), Toast.LENGTH_SHORT).show()
				}
			}
		}.apply { isDaemon = true }.start()
	}

	val exportFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("application/octet-stream")
	) { uri ->
		if (uri != null) {
			Thread {
				val exported = try {
					val from = ExtraDictionaries.fileFor(context, name) ?: throw IllegalStateException()
					context.contentResolver.openOutputStream(uri)?.use { sink ->
						from.inputStream().use { it.copyTo(sink) }
					}
					true
				} catch (e: Exception) {
					false
				}
				context.findActivity()?.runOnUiThread {
					try {
						notice = if (exported) {
							context.getString(R.string.dictionary_exported, total.coerceAtLeast(0))
						} else {
							context.getString(R.string.dictionary_export_failed)
						}
						Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
					} catch (_: Exception) {
						Toast.makeText(context, context.getString(R.string.dictionary_export_failed), Toast.LENGTH_SHORT).show()
					}
				}
			}.apply { isDaemon = true }.start()
		}
	}

	Scaffold(
		topBar = { ScreenTopBar(title = name, onBack = onBack) }
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.padding(bottom = 24.dp)
		) {
			LiveNotice(text = notice, horizontal = 16.dp)
			OutlinedTextField(
				value = query,
				onValueChange = ::searchFor,
				singleLine = true,
				label = { Text(text = stringResource(R.string.dictionary_file_search_hint)) },
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
						onClick = { exportFile.launch("$name.dict") },
						modifier = Modifier.weight(1f)
					) {
						Text(
							text = stringResource(R.string.dictionary_export),
							style = MaterialTheme.typography.labelLarge
						)
					}
				}
				Gap(8.dp)
				OutlinedButton(
					onClick = { askingDelete = true },
					modifier = Modifier.fillMaxWidth()
				) {
					Text(
						text = stringResource(R.string.dictionary_delete),
						style = MaterialTheme.typography.labelLarge
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
			// The search keeps two hundred matches at most, so a count line
			// under a search is exact until it says two hundred.
			val countLine = when {
				total < 0 || searching -> null
				query.isBlank() && total > shown.size -> context.getString(
					R.string.dictionary_file_showing,
					shown.size,
					total
				)
				query.isBlank() -> context.getString(R.string.dictionary_file_words, total)
				else -> context.getString(R.string.dictionary_file_words, shown.size)
			}
			countLine?.let {
				Text(
					text = it,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 4.dp)
				)
			}
			LazyColumn(modifier = Modifier.fillMaxSize()) {
				// By position, not by word: an imported file may hold the
				// same word twice, and two rows with one key are one row too
				// many for the list to tell apart.
				items(shown.size, key = { it }) { at ->
					val (word, say) = shown[at]
					WordRow(
						word = word,
						say = say,
						onPress = { editing = word to say }
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
				if (ExtraDictionaries.add(context, name, word.trim(), say.trim())) {
					refresh()
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
					if (ExtraDictionaries.replace(context, name, word, newWord.trim(), newSay.trim())) {
						refresh()
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
					if (ExtraDictionaries.remove(context, name, word)) {
						refresh()
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

	if (askingDelete) {
		AlertDialog(
			onDismissRequest = { askingDelete = false },
			title = { Text(text = stringResource(R.string.dictionary_file_delete_title)) },
			text = {
				Text(
					text = stringResource(
						R.string.dictionary_file_delete_message,
						name,
						total.coerceAtLeast(0)
					)
				)
			},
			confirmButton = {
				TextButton(
					onClick = {
						askingDelete = false
						if (ExtraDictionaries.delete(context, name)) {
							val said = context.getString(R.string.dictionary_file_deleted, name)
							Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
						}
						onDeleted()
					}
				) {
					Text(text = stringResource(R.string.dictionary_delete))
				}
			},
			dismissButton = {
				TextButton(onClick = { askingDelete = false }) {
					Text(text = stringResource(R.string.cancel))
				}
			}
		)
	}
}

/** How many words the list shows before the search is used. */
private const val SHOWN_WITHOUT_SEARCH = 2000

/** How many matches a search keeps. */
private const val SEARCH_RESULTS = 200
