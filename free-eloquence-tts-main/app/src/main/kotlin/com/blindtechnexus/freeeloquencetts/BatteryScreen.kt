package com.blindtechnexus.freeeloquencetts

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Everything this app can do to keep Android from stopping the voice.
 *
 * The screen is in two parts, because the two things they are for happen at
 * two different times.
 *
 * While the phone is idle: the exemption from battery optimization, which is the
 * single most important and cannot be granted from inside the app -- the button
 * hands over to the system's own screen, and the state shown here is read again
 * on every resume, because the answer can change while that screen is up; the
 * notification that holds the app in the foreground, which now stands whether or
 * not anything is bound to the engine, since a service that keeps it up of its
 * own has been added for the purpose -- and which appears when that switch is
 * on and at no other time, because nothing else in this app posts one; and the
 * same notification coming back after a restart, which is the one thing a
 * reboot takes away.
 *
 * How the engine behaves while speaking -- the foreground, the wake lock, how
 * far ahead of the speech the samples may run, how long an instance is kept,
 * and how long a silence is waited out -- is fixed in [Stability] and
 * [Pacing] instead. None of those has a second good answer, and a choice
 * between a fast answer and a slow one on a screen about keeping the voice
 * alive is a way of choosing the slow one by accident.
 *
 * And for a fault: the switch that writes what is changed here to the system
 * log, so that a voice that stopped can be traced back to what was changed
 * before it did.
 *
 * Every one of the rows has its sentence above it, the way the rest of the
 * settings do: the names do not carry their own meaning, and a description
 * folded into a control would be read out again on every visit with no way past
 * it. What is in force at the moment is said once, at the bottom, in the terms
 * of what the phone is doing rather than in the names of the switches -- because
 * the question this screen is opened to answer is not what was set but whether
 * it is working.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryScreen(onBack: () -> Unit, onRequestExemption: () -> Unit) {
	val context = LocalContext.current
	val settings = remember { Settings(context.applicationContext) }
	// Resolved before the sentence that starts with one: a screen whose
	// first line says what is in force has to know it before it can say it.
	// The clicks below are not compositions, so a sentence asked for in one
	// would not compile -- which is why every sentence is read here.
	val noBatteryScreen = stringResource(R.string.battery_no_screen)
	val bootNeedsNotification = stringResource(R.string.battery_boot_needs_notification)
	val notificationOn = stringResource(R.string.battery_notification_on)
	val notificationOff = stringResource(R.string.battery_notification_off)

	var granted by remember { mutableStateOf(BatteryExemption.isGranted(context)) }
	var persistent by remember { mutableStateOf(settings.persistentNotification) }
	var startOnBoot by remember { mutableStateOf(settings.startOnBoot) }
	var notice by remember {
		mutableStateOf(if (settings.persistentNotification) notificationOn else notificationOff)
	}
	var stopButton by remember { mutableStateOf(settings.notificationStopButton) }
	var tracking by remember { mutableStateOf(settings.trackActions) }

	// The exemption is granted on a screen belonging to the system, and the
	// answer is read again when that screen is left. The keep-alive's own state
	// is read with it, for the same reason: the notification may have been
	// refused, swiped away or turned off in the app's own settings while this
	// one was in the background.
	val activity = androidx.activity.compose.LocalActivity.current as? ComponentActivity
	DisposableEffect(settings) {
		onDispose { settings.close() }
	}
	DisposableEffect(activity) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				granted = BatteryExemption.isGranted(context)
				persistent = settings.persistentNotification
			}
		}
		activity?.lifecycle?.addObserver(observer)
		onDispose { activity?.lifecycle?.removeObserver(observer) }
	}

	Scaffold(
		topBar = {
			ScreenTopBar(title = stringResource(R.string.section_battery), onBack = onBack)
		}
	) { inset ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(inset)
				.verticalScroll(rememberScrollState())
				.padding(bottom = 24.dp)
		) {
			SectionText(text = stringResource(R.string.battery_screen_description))
			LiveNotice(text = notice)

			SectionHeading(text = stringResource(R.string.battery_idle_heading))
			ExemptionRow(
				granted = granted,
				onClick = {
					Tracker.action(settings, "asked for the battery optimization exemption")
					onRequestExemption()
				}
			)
			SwitchRow(
				label = stringResource(R.string.persistent_notification_label),
				checked = persistent,
				description = stringResource(R.string.persistent_notification_description),
				onChange = { on ->
					persistent = on
					settings.persistentNotification = on
					// The setting and the service are one thing: a switch that
					// only wrote a number would leave the notification standing
					// until the next time the engine was spoken to, and a person
					// who has just turned it off would be looking at it.
					// The app's own service is what holds the notification;
					// KeepAlive is the one that knows what to do when the system
					// would not let that service start.
					KeepAlive.sync(context, on)
					// Nothing else in this app posts a notification, so this
					// is not a note about a setting: it is what the phone is
					// doing now.
					notice = if (on) notificationOn else notificationOff
					Tracker.action(settings, "persistent notification ${if (on) "on" else "off"}")
				}
			)
			SwitchRow(
				label = stringResource(R.string.notification_stop_label),
				checked = stopButton,
				description = stringResource(R.string.notification_stop_description),
				onChange = { on ->
					stopButton = on
					settings.notificationStopButton = on
					// The notification is rebuilt by whoever is holding it up,
					// within the check that runs every few seconds.
					Tracker.action(settings, "notification stop button ${if (on) "on" else "off"}")
				}
			)
			SwitchRow(
				label = stringResource(R.string.start_on_boot_label),
				checked = startOnBoot,
				description = stringResource(R.string.start_on_boot_description),
				onChange = { on ->
					startOnBoot = on
					settings.startOnBoot = on
					if (on && !persistent) {
						// One is the other's means: what comes back after a
						// restart is the notification, and turning on the asking
						// without the thing asked for is a switch that promises a
						// thing it cannot do. So it is turned on too, and the
						// sentence above says as much rather than hiding it.
						persistent = true
						settings.persistentNotification = true
						KeepAlive.sync(context, true)
						notice = bootNeedsNotification
					}
					Tracker.action(settings, "start on boot ${if (on) "on" else "off"}")
				}
			)
			ActionRow(
				label = stringResource(
					if (ManufacturerScreens.hasOwnScreen()) R.string.battery_manufacturer_open
					else R.string.battery_android_open
				),
				description = stringResource(R.string.battery_manufacturer_description),
				onClick = {
					Tracker.action(settings, "asked for the phone's own background settings")
					if (!ManufacturerScreens.open(context) && !ManufacturerScreens.appDetails(context)) {
						notice = noBatteryScreen
					}
				}
			)

			SectionHeading(text = stringResource(R.string.battery_fault_heading))
			SwitchRow(
				label = stringResource(R.string.track_actions_label),
				checked = tracking,
				description = stringResource(R.string.track_actions_description),
				onChange = { on ->
					tracking = on
					settings.trackActions = on
					Tracker.action(settings, "action tracking ${if (on) "on" else "off"}")
				}
			)
			Paragraph(
				text = stringResource(
					R.string.battery_now,
					if (persistent) stringResource(R.string.battery_now_wanted)
					else stringResource(R.string.battery_now_unwanted),
					if (KeepAliveService.isRunning) stringResource(R.string.battery_now_service)
					else stringResource(R.string.battery_now_none),
					if (KeepAliveNotification.notificationsAllowed(context))
						stringResource(R.string.battery_now_allowed)
					else stringResource(R.string.battery_now_refused)
				)
			)
		}
	}
}

/** The one setting this app cannot grant, with whether it has been given. */
@Composable
private fun ExemptionRow(granted: Boolean, onClick: () -> Unit) {
	val state = stringResource(
		if (granted) R.string.battery_exemption_granted else R.string.battery_exemption_not_granted
	)
	val label = stringResource(R.string.battery_exemption_label)
	Described(description = stringResource(R.string.battery_exemption_description)) {
		OutlinedButton(
			onClick = onClick,
			modifier = Modifier
				.fillMaxWidth()
				.clearAndSetSemantics {
					contentDescription = label
					stateDescription = state
					role = Role.Button
				}
		) {
			Text(text = label)
			Text(text = " $state")
		}
	}
}
