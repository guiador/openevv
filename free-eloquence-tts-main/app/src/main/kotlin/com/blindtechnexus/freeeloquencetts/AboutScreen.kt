package com.blindtechnexus.freeeloquencetts

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
	val context = LocalContext.current
	val subject = stringResource(R.string.about_email_subject)
	val noWhatsApp = stringResource(
		R.string.about_unavailable, stringResource(R.string.about_whatsapp_action)
	)
	val noTelegram = stringResource(
		R.string.about_unavailable, stringResource(R.string.about_telegram_action)
	)
	val noEmail = stringResource(
		R.string.about_unavailable, stringResource(R.string.about_email_action)
	)
	var notice by remember { mutableStateOf("") }
	Scaffold(
		topBar = {
			ScreenTopBar(
				title = stringResource(R.string.about_title),
				onBack = onBack
			)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
		) {
			LiveNotice(text = notice)

			Paragraph(text = stringResource(R.string.about_app))
			Paragraph(text = stringResource(R.string.about_accessibility))
			Paragraph(text = stringResource(R.string.about_free))
			Paragraph(text = stringResource(R.string.about_unofficial))

			SectionHeading(text = stringResource(R.string.about_developer_heading), horizontal = 0.dp)
			Paragraph(text = stringResource(R.string.about_developer))
			Paragraph(text = stringResource(R.string.about_team))
			Paragraph(text = stringResource(R.string.local_credits))

			SectionHeading(text = stringResource(R.string.about_contact_heading), horizontal = 0.dp)
			Contact(
				description = stringResource(R.string.about_contact_whatsapp),
				action = stringResource(R.string.about_whatsapp_action),
				onOpen = { ExternalLinks.whatsApp(context) },
				onRefused = { notice = noWhatsApp }
			)
			Contact(
				description = stringResource(R.string.about_contact_telegram),
				action = stringResource(R.string.about_telegram_action),
				onOpen = { ExternalLinks.telegram(context) },
				onRefused = { notice = noTelegram }
			)
			Contact(
				description = stringResource(R.string.about_contact_email),
				action = stringResource(R.string.about_email_action),
				onOpen = { ExternalLinks.email(context, subject) },
				onRefused = { notice = noEmail }
			)

            SectionHeading(text = stringResource(R.string.about_engine_updates), horizontal = 0.dp)
            Paragraph(text = stringResource(R.string.about_distribution))
            Paragraph(text = stringResource(R.string.about_installation, BuildConfig.APPLICATION_ID))
			SectionHeading(text = stringResource(R.string.about_build_heading), horizontal = 0.dp)
			Paragraph(text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
		}
	}
}

@Composable
private fun Contact(
	description: String,
	action: String,
	onOpen: () -> Boolean,
	onRefused: () -> Unit
) {
	Text(
		text = description,
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier.fillMaxWidth()
	)
	Gap(12.dp)
	OutlinedButton(
		onClick = { if (!onOpen()) onRefused() },
		modifier = Modifier.fillMaxWidth()
	) {
		Text(text = action, style = MaterialTheme.typography.labelLarge)
	}
	Gap(24.dp)
}
