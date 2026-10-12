package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The pieces every screen here is built out of.
 *
 * There are four screens, and somebody who cannot see them has to be able to
 * tell which one is up, get back from it, and jump between the sections of a
 * long one. So the title bar, the way back, a heading, a paragraph and a
 * message are written once and used the same way in all of them.
 *
 * Two of these are worth saying something about.
 *
 * A heading is a heading because it says so in the semantics and not because
 * of the size of its type. TalkBack can move from heading to heading, which
 * turns a screen of forty controls into the list of its sections, and a title
 * in a larger font that does not carry `heading()` is not on that list at all.
 *
 * A message about what just happened goes in a live region, and a live region
 * is only a live region if it was there before it changed. A node that appears
 * is not a change to one and may never be announced, so the message is always
 * composed and is empty most of the time.
 */

/** The title every screen carries, with the way back beside it when there is
 *  somewhere to go back to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(title: String, onBack: (() -> Unit)? = null) {
	CenterAlignedTopAppBar(
		title = {
			Text(
				text = title,
				style = MaterialTheme.typography.titleLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.semantics { heading() }
			)
		},
		navigationIcon = {
			// Taken into a local first: no smart cast to be lost inside a
			// lambda that is not inlined.
			val back = onBack
			if (back != null) BackButton(onBack = back)
		}
	)
}

/**
 * The way back, in words.
 *
 * It is the first thing on the screen and so the first thing a reader meets,
 * which is where somebody who cannot see wants the way out to be. It says what
 * it does rather than showing an arrow: an arrow with no label is announced as
 * an unlabelled button, and where it goes is then a guess.
 *
 * The padding is not decoration. It is what makes the target the 48dp Android
 * asks for, which is a finger's width rather than a word's.
 */
@Composable
fun BackButton(onBack: () -> Unit) {
	val label = stringResource(R.string.go_back)
    TextButton(onClick = onBack) { Text(text = label) }
}

/** A section's name, which is what a reader jumps to. [horizontal] is the
 *  indent the screen it is on has not already given it. */
@Composable
fun SectionHeading(text: String, horizontal: Dp = 16.dp) {
	Text(
		text = text,
		style = MaterialTheme.typography.titleMedium,
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = horizontal, end = horizontal, top = 24.dp, bottom = 4.dp)
			.semantics { heading() }
	)
}

/** What the section under a heading is for. A stop of its own rather than part
 *  of the heading: a reader who jumps to the heading goes on to hear why the
 *  section exists before the controls in it, and one who does not can skip it. */
@Composable
fun SectionText(text: String) {
    if (LocalExpertInterface.current) return
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
	)
}

/** A paragraph of prose, for a screen that is read rather than filled in. */
@Composable
fun Paragraph(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyLarge,
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 16.dp)
	)
}

/**
 * What just happened, for whoever cannot see it happen.
 *
 * Composed whether or not there is anything to say, and empty when there is
 * not, because a live region that arrives with its first message is a new node
 * rather than a changed one and TalkBack may say nothing about it at all. An
 * action that quietly did nothing is the worst thing a screen can do to
 * somebody who cannot see that it did nothing.
 */
@Composable
fun LiveNotice(text: String, horizontal: Dp = 0.dp) {
	Text(
		text = text,
		style = MaterialTheme.typography.bodyMedium,
		color = if (text.isEmpty()) MaterialTheme.colorScheme.onSurface
		else MaterialTheme.colorScheme.error,
		modifier = Modifier
			.fillMaxWidth()
			.padding(start = horizontal, end = horizontal, bottom = 8.dp)
			.semantics { liveRegion = LiveRegionMode.Polite }
	)
}

/** Air between one thing and the next. A spacer carries no semantics at all,
 *  which is the point: a gap that a reader had to swipe past would be a stop
 *  that says nothing. */
@Composable
fun Gap(height: Dp = 16.dp) {
	Spacer(modifier = Modifier.height(height))
}
