package com.blindtechnexus.freeeloquencetts

import android.content.Intent
import android.media.MediaPlayer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * The audio generator: text in, an audio file out, in the voice.
 *
 * The box holds the text, the button speaks it into a file, and the file is
 * then shared with whoever wants it or saved wherever the listener says --
 * a greeting recorded in the voice, a passage kept for a phone with no
 * engine on it. What is generated hears the settings: the voice, the
 * dictionaries, the number reading, all of it.
 *
 * A generation runs on a thread of its own with the screen saying how far it
 * has got, and leaving the screen stops it: a file nobody waited for is a
 * file nobody wanted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioGeneratorScreen(onBack: () -> Unit) {
	val context = LocalContext.current
	val generator = remember { AudioGenerator(context) }
	// One settings for the screen, reading the preferences live at every
	// generation and unhooked when the screen goes: a settings per
	// generation would leave a listener behind it every time.
	val settings = remember { Settings(context) }
	val turn = remember { AtomicInteger(0) }
	var input by remember { mutableStateOf("") }
	var notice by remember { mutableStateOf("") }
	var generating by remember { mutableStateOf(false) }
	var ready by remember { mutableStateOf<File?>(null) }
	// What the playback button says: at rest it offers to play, while the
	// file is sounding it offers to pause, paused it offers to go on, and
	// heard out it offers to play again from the start.
	var playback by remember { mutableStateOf(Playback.IDLE) }
	val player = remember { Player() }
	val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
	DisposableEffect(activity) {
		val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
			if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP &&
				android.os.Build.VERSION.SDK_INT >= 37 && !KeepAliveService.hasForeground &&
				playback == Playback.PLAYING) {
				// Android 17 requires a foreground service for background playback.
				// Keep the playback position and let the user resume in the app.
				try {
					player.media?.pause()
					playback = Playback.PAUSED
				} catch (_: IllegalStateException) {
					player.release()
					playback = Playback.IDLE
				}
			}
		}
		activity?.lifecycle?.addObserver(observer)
		onDispose { activity?.lifecycle?.removeObserver(observer) }
	}

	DisposableEffect(Unit) {
		onDispose {
			player.release()
			generator.cancel()
			settings.close()
		}
	}

	val saveFile = rememberLauncherForActivityResult(
		ActivityResultContracts.CreateDocument("audio/wav")
	) { uri ->
		val file = ready
		if (uri == null || file == null) return@rememberLauncherForActivityResult
		Thread {
			val saved = try {
				context.contentResolver.openOutputStream(uri)?.use { sink ->
					file.inputStream().use { it.copyTo(sink) }
				}
				true
			} catch (e: Exception) {
				false
			}
			context.findActivity()?.runOnUiThread {
				val said = if (saved) {
					context.getString(R.string.audio_saved)
				} else {
					context.getString(R.string.audio_save_failed)
				}
				notice = said
				Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			}
		}.apply { isDaemon = true }.start()
	}

	/** Puts the player away and the button back to rest. */
	fun stopPlayback() {
		player.release()
		playback = Playback.IDLE
	}

	/** Plays [file] from the start on a fresh player. */
	fun startPlayback(file: File) {
		player.release()
		try {
			val sounding = MediaPlayer()
			sounding.setDataSource(file.absolutePath)
			sounding.setOnCompletionListener { playback = Playback.DONE }
			sounding.setOnErrorListener { _, _, _ ->
				player.release()
				playback = Playback.IDLE
				val said = context.getString(R.string.audio_play_failed)
				notice = said
				Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
				true
			}
			sounding.prepare()
			sounding.start()
			player.media = sounding
			playback = Playback.PLAYING
		} catch (_: Throwable) {
			player.release()
			playback = Playback.IDLE
			val said = context.getString(R.string.audio_play_failed)
			notice = said
			Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
		}
	}

	/** What the playback button does: pause while sounding, go on while
	 *  paused, start over when heard out, and start when at rest. A player
	 *  that refuses any of it is thrown away and a fresh one starts, so a
	 *  button that did nothing is never the answer. */
	fun togglePlayback() {
		val file = ready ?: return
		when (playback) {
			Playback.PLAYING -> {
				try {
					player.media?.pause()
					playback = Playback.PAUSED
				} catch (_: Throwable) {
					startPlayback(file)
				}
			}
			Playback.PAUSED -> {
				try {
					player.media?.start()
					playback = Playback.PLAYING
				} catch (_: Throwable) {
					startPlayback(file)
				}
			}
			Playback.DONE -> {
				try {
					player.media?.seekTo(0)
					player.media?.start()
					playback = Playback.PLAYING
				} catch (_: Throwable) {
					startPlayback(file)
				}
			}
			Playback.IDLE -> startPlayback(file)
		}
	}

	fun generate() {
		if (generating) return
		// Whatever was sounding belongs to the file before this one: it
		// stops now rather than talking over the generation.
		stopPlayback()
		if (input.isBlank()) {
			val said = context.getString(R.string.audio_empty)
			notice = said
			Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			return
		}
		if (input.length > AudioGenerator.MAX_CHARS) {
			val said = context.getString(R.string.audio_too_long, AudioGenerator.MAX_CHARS)
			notice = said
			Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			return
		}
		val mine = turn.incrementAndGet()
		generating = true
		ready = null
		notice = context.getString(R.string.audio_generating, 0, input.length)
		val text = input
		generator.render(
			text = text,
			settings = settings,
			progress = { doneChars, totalChars ->
				val said = context.getString(R.string.audio_generating, doneChars, totalChars)
				context.findActivity()?.runOnUiThread {
					if (mine == turn.get()) notice = said
				}
			},
			done = { file ->
				if (mine != turn.get()) return@render
				generating = false
				playback = Playback.IDLE
				ready = file
				val said = if (file != null) {
					context.getString(R.string.audio_ready)
				} else {
					context.getString(R.string.audio_failed)
				}
				notice = said
				Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
			}
		)
	}

	fun share() {
		val file = ready ?: return
		try {
			val uri = FileProvider.getUriForFile(context, Installer.authority(context), file)
			val intent = Intent(Intent.ACTION_SEND).apply {
				type = "audio/wav"
				putExtra(Intent.EXTRA_STREAM, uri)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
			context.startActivity(
				Intent.createChooser(intent, context.getString(R.string.audio_share))
			)
		} catch (e: Exception) {
			val said = context.getString(R.string.audio_save_failed)
			notice = said
			Toast.makeText(context, said, Toast.LENGTH_SHORT).show()
		}
	}

	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.audio_title),
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
			SectionText(text = stringResource(R.string.audio_description))
			OutlinedTextField(
				value = input,
				onValueChange = { input = it },
				label = { Text(text = stringResource(R.string.audio_text_hint)) },
				keyboardOptions = KeyboardOptions(
					capitalization = KeyboardCapitalization.Sentences
				),
				minLines = 6,
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			)
			Gap(12.dp)
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp)
			) {
				Button(
					onClick = ::generate,
					enabled = !generating,
					modifier = Modifier.fillMaxWidth()
				) {
					Text(
						text = stringResource(R.string.audio_generate),
						style = MaterialTheme.typography.labelLarge
					)
				}
				if (ready != null && !generating) {
					Gap(8.dp)
					OutlinedButton(
						onClick = ::togglePlayback,
						modifier = Modifier.fillMaxWidth()
					) {
						Text(
							text = stringResource(
								when (playback) {
									Playback.PLAYING -> R.string.audio_pause
									Playback.DONE -> R.string.audio_replay
									else -> R.string.audio_play
								}
							),
							style = MaterialTheme.typography.labelLarge
						)
					}
					Gap(8.dp)
					Row(modifier = Modifier.fillMaxWidth()) {
						OutlinedButton(
							onClick = ::share,
							modifier = Modifier.weight(1f)
						) {
							Text(
								text = stringResource(R.string.audio_share),
								style = MaterialTheme.typography.labelLarge
							)
						}
						Spacer(modifier = Modifier.width(8.dp))
						OutlinedButton(
							onClick = {
								saveFile.launch(context.getString(R.string.audio_file_name))
							},
							modifier = Modifier.weight(1f)
						) {
							Text(
								text = stringResource(R.string.audio_save),
								style = MaterialTheme.typography.labelLarge
							)
						}
					}
				}
			}
		}
	}
}

/** What the playback button is offering. At rest it plays, while the file
 *  is sounding it pauses, paused it goes on, and heard out it replays. */
private enum class Playback {
	IDLE, PLAYING, PAUSED, DONE
}

/**
 * The player the button above talks to, held outside the composition so
 * that redrawing the screen never touches it.
 *
 * Everything here runs on the main thread -- the button, the generation
 * finishing, and the player calling back -- so there is nothing to race
 * about. [release] answers in every state a MediaPlayer has, including
 * none at all, which is what makes leaving the screen, starting over, and
 * a file the player refused all safe in the same way.
 */
private class Player {

	var media: MediaPlayer? = null

	fun release() {
		val sounding = media
		media = null
		if (sounding == null) return
		try {
			sounding.reset()
		} catch (_: Throwable) {
		}
		try {
			sounding.release()
		} catch (_: Throwable) {
		}
	}
}
