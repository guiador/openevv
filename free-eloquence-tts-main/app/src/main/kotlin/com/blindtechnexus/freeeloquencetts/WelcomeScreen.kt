package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The one screen that is read rather than filled in, shown once on a first
 * launch and never again unless somebody asks for it.
 *
 * It is what a person meets with a screen reader already talking, so the first
 * thing it says is what the app is and the second is what it is about to ask
 * for. Two permissions come straight after this screen, one of which the
 * engine cannot work without, and a confirmation screen that arrives out of
 * nowhere is refused far more often than one that has been explained -- which
 * is a voice that stops working later, for a reason nobody was told.
 *
 * The title is a heading, the features are a headed list with an icon each,
 * and the rest are paragraphs, which is the whole of the structure: TalkBack
 * can jump between the headings, and everything under them is in order and
 * says one thing each. The icons carry no meaning of their own -- they are
 * silent to a screen reader and the row's own words say the thing -- so there
 * is nothing here that depends on a colour, an image or a position.
 *
 * What is on it is what somebody would want to know before they use it rather
 * than a list of features: that it is free and finished, that there are nine
 * voices and two Englishes, that web pages and code are read as what they are,
 * and that the two permissions coming next are worth allowing and are not
 * needed to hear anything today. A welcome screen that says what the app costs
 * and what it will ask for is a welcome screen somebody arrives at rather than
 * one they get past.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(onContinue: () -> Unit, revisit: Boolean = false) {
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
			Text(
				text = stringResource(R.string.welcome_title),
				style = MaterialTheme.typography.headlineSmall,
				modifier = Modifier
					.fillMaxWidth()
					.padding(bottom = 16.dp)
					.semantics { heading() }
			)
			Paragraph(text = stringResource(R.string.welcome_alternative))
			Paragraph(text = stringResource(R.string.welcome_free))
			Paragraph(text = stringResource(R.string.welcome_intro))
			Text(
				text = stringResource(R.string.welcome_features),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier
					.fillMaxWidth()
					.padding(top = 8.dp, bottom = 4.dp)
					.semantics { heading() }
			)
			Feature(
				icon = Icons.Filled.RecordVoiceOver,
				text = stringResource(R.string.welcome_voices),
				description = stringResource(R.string.welcome_voices_description)
			)
			Feature(
				icon = Icons.Filled.Language,
				text = stringResource(R.string.welcome_languages),
				description = stringResource(R.string.welcome_languages_description)
			)
			Feature(
				icon = Icons.Filled.Web,
				text = stringResource(R.string.welcome_web),
				description = stringResource(R.string.welcome_web_description)
			)
			Feature(
				icon = Icons.Filled.Code,
				text = stringResource(R.string.welcome_code),
				description = stringResource(R.string.welcome_code_description)
			)
			Feature(
				icon = Icons.Filled.Spellcheck,
				text = stringResource(R.string.welcome_spelling),
				description = stringResource(R.string.welcome_spelling_description)
			)
			Feature(
				icon = Icons.Filled.Book,
				text = stringResource(R.string.welcome_dictionaries),
				description = stringResource(R.string.welcome_dictionaries_description)
			)
			Feature(
				icon = Icons.Filled.Pin,
				text = stringResource(R.string.welcome_codes),
				description = stringResource(R.string.welcome_codes_description)
			)
			Feature(
				icon = Icons.Filled.Speaker,
				text = stringResource(R.string.welcome_routing),
				description = stringResource(R.string.welcome_routing_description)
			)
			Gap(8.dp)
			// The permissions are asked for on the way past this screen the
			// first time; read again from the main screen, there is nothing
			// coming next to warn about.
			if (!revisit) Paragraph(text = stringResource(R.string.welcome_permissions))
			Paragraph(text = stringResource(R.string.welcome_about))
			Gap(16.dp)
			Button(
				onClick = onContinue,
				modifier = Modifier.fillMaxWidth()
			) {
				Text(
					text = stringResource(if (revisit) R.string.welcome_back_to_main else R.string.welcome_continue),
					style = MaterialTheme.typography.labelLarge
				)
			}
		}
	}
}

/** One thing the app does, with its icon beside it. The icon is silent: the
 *  words are what a screen reader reads, with or without eyes on the row. */
@Composable
private fun Feature(icon: ImageVector, text: String, description: String) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			imageVector = icon,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.primary,
			modifier = Modifier.size(32.dp)
		)
		Column(modifier = Modifier.padding(start = 16.dp)) {
			Text(
				text = text,
				style = MaterialTheme.typography.bodyLarge
			)
			Text(
				text = description,
				style = MaterialTheme.typography.bodySmall
			)
		}
	}
}
