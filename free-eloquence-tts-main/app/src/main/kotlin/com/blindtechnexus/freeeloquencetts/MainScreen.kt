package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * What the launcher opens, and the five things worth doing from here.
 *
 * This is the screen somebody arrives at with a screen reader on and no idea
 * what the app is, so it is written to be listened to in order: what the app
 * is, then five things to do, each with the reason for it said before the
 * button that does it. Nothing depends on a colour, an icon or a position.
 *
 * The order of a description and its button matters. A reader swipes forward
 * one node at a time, and a button reached first is a button whose purpose has
 * to be guessed and then swiped back for, so each description is the stop
 * before the thing it describes.
 *
 * The permissions are not here. They are asked for on the way in, on every
 * launch, by the activity that owns this screen: a row saying what has and has
 * not been given is a stop to swipe past on every visit for a question that is
 * asked and answered once, and the asking itself is where the explanation
 * belongs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	onConfigure: () -> Unit,
	onOpenSystemSpeechSettings: () -> Boolean,
	onAbout: () -> Unit,
	onAudio: () -> Unit,
	onWelcome: () -> Unit = {}
) {
	// Resolved here rather than where it is used: the click that needs it is
	// not a composition, and a stringResource there would not compile.
	val nothingOpensSpeechSettings = stringResource(R.string.main_system_unavailable)
	var notice by remember { mutableStateOf("") }
	Scaffold(
		topBar = { ScreenTopBar(title = stringResource(R.string.app_name)) }
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
		) {
			LiveNotice(text = notice)
			Text(
				text = stringResource(R.string.main_tagline),
				style = MaterialTheme.typography.bodyLarge,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 24.dp)
			)

			Step(
				description = stringResource(R.string.main_configure_description),
				action = stringResource(R.string.main_configure_action),
				onClick = onConfigure
			)
			Step(
				description = stringResource(R.string.main_system_description),
				action = stringResource(R.string.main_system_action),
				onClick = {
					if (!onOpenSystemSpeechSettings()) notice = nothingOpensSpeechSettings
				}
			)
			Step(
				description = stringResource(R.string.main_about_description),
				action = stringResource(R.string.main_about_action),
				onClick = onAbout
			)
			Step(
				description = stringResource(R.string.main_audio_description),
				action = stringResource(R.string.main_audio_action),
				onClick = onAudio
			)
			Step(
				description = stringResource(R.string.main_welcome_description),
				action = stringResource(R.string.main_welcome_action),
				onClick = onWelcome
			)
		}
	}
}

/**
 * A reason, and then the button that acts on it.
 *
 * Two stops rather than one, which is deliberate: merging them would put the
 * description inside the button's own announcement every time it is reached,
 * and a description is worth hearing once rather than on every visit. They are
 * beside each other in the order they are read, which is what makes two stops
 * here as easy to follow as one.
 */
@Composable
private fun Step(description: String, action: String, onClick: () -> Unit) {
	if (!LocalExpertInterface.current) Text(
		text = description,
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier.fillMaxWidth()
	)
	Gap(if (LocalExpertInterface.current) 4.dp else 12.dp)
	Button(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth()
	) {
		Text(text = action, style = MaterialTheme.typography.labelLarge)
	}
	Gap(if (LocalExpertInterface.current) 12.dp else 24.dp)
}
