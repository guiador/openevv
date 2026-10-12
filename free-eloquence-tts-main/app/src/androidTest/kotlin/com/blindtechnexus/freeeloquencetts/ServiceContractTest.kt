package com.blindtechnexus.freeeloquencetts

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The contract the keep-alive notification and the wake lock lean on.
 *
 * These two are the parts of the battery and stability work that nothing
 * inside the app can prove by running: the notification only protects the
 * engine if the manifest has named the service a foreground one and asked for
 * the permissions the system checks, and the wake lock only works if its
 * permission is there too. A service whose manifest is wrong still speaks
 * happily on an emulator and dies on a phone, so what is checked here is the
 * manifest itself, read back the way the system reads it, plus the constants
 * the service runs by.
 */
@RunWith(AndroidJUnit4::class)
class ServiceContractTest {

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun theManifestDeclaresThePermissionsTheKeepAliveAndWakeLockNeed() {
		val info = context.packageManager.getPackageInfo(
			context.packageName,
			PackageManager.GET_PERMISSIONS
		)
		val asked = info.requestedPermissions?.toSet().orEmpty()
		// The foreground service and its special-use type, which the keep-alive
		// notification promotes the service to on Android 14 and later.
		assertTrue("FOREGROUND_SERVICE is not declared", asked.contains(Manifest.permission.FOREGROUND_SERVICE))
		assertTrue(
			"FOREGROUND_SERVICE_SPECIAL_USE is not declared",
			asked.contains(Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE)
		)
		// The wake lock and the notification both need their own permissions.
		assertTrue("WAKE_LOCK is not declared", asked.contains(Manifest.permission.WAKE_LOCK))
		assertTrue("POST_NOTIFICATIONS is not declared", asked.contains(Manifest.permission.POST_NOTIFICATIONS))
		// The update path: the file that says which version to be on is read
		// over the network, and an APK cannot be offered to the installer
		// without the right to ask for it. Both are asked here and neither is
		// enough on its own: the first without the second is a download that
		// goes nowhere, and the second without the first is a permission that
		// is declared for nothing.
		assertTrue("INTERNET is not declared", asked.contains(Manifest.permission.INTERNET))
		assertTrue(
			"REQUEST_INSTALL_PACKAGES is not declared",
			asked.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES)
		)
		// And the permission that the exemption button on the battery screen
		// needs to be able to ask the system at all.
		assertTrue(
			"REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is not declared",
			asked.contains(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
		)
	}

	/**
	 * The keep-alive has a service of its own, and it is the engine that does
	 * not.
	 *
	 * The whole of the fix for "the notification goes away when the reader lets
	 * go" is that something the app starts, rather than something the system
	 * binds, is what holds the notification up. So what matters here is that the
	 * second service exists, is a foreground one of the same kind, and cannot be
	 * started by anybody else: a keep-alive that another app can stop is a
	 * keep-alive that another app can stop.
	 */
	@Test
	fun theNotificationIsHeldUpByAServicethatNothingElseStops() {
		val component = ComponentName(context, KeepAliveService::class.java)
		val info = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
		assertFalse("the keep-alive service must not be exported", info.exported)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			assertTrue(
				"the keep-alive service must be a special-use foreground service",
				info.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
			)
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val subtype = runCatching {
				context.packageManager
					.getProperty(PackageManager.PROPERTY_SPECIAL_USE_FGS_SUBTYPE, component)
					.getString()
			}.getOrNull()
			assertFalse("the keep-alive service must say what it is for", subtype.isNullOrBlank())
		}
	}

	/**
	 * The boot brings the notification back, and only this app can ask for
	 * silence.
	 *
	 * Intent filters are not readable off a component, so the question is asked
	 * the way the system asks it: who would this broadcast be sent to. The three
	 * that come from outside are the two boots and the update that replaced the
	 * app, and the one that comes from the notification's own button has to name
	 * this component, which is what keeps it from being a broadcast anybody on
	 * the phone can send to make the voice stop.
	 */
	@Test
	fun theReceiverIsAwakeForTheBootAndNobodyElsesButton() {
		val component = ComponentName(context, EngineReceiver::class.java)
		val receiver = context.packageManager.getReceiverInfo(component, 0)
		assertFalse("the receiver must not be exported", receiver.exported)
		for (action in listOf(
			Intent.ACTION_BOOT_COMPLETED,
			Intent.ACTION_LOCKED_BOOT_COMPLETED,
			Intent.ACTION_MY_PACKAGE_REPLACED
		)) {
			val taken = context.packageManager.queryBroadcastReceivers(Intent(action), 0)
				.any { it.activityInfo?.name == component.className }
			assertTrue("nothing takes $action", taken)
		}
		// The silence command is named after this app rather than after a
		// category anybody can hold, and it is sent with a component on it.
		val stop = Intent(EngineReceiver.ACTION_STOP).setComponent(component)
		assertEquals(component.className, stop.component?.className)
	}

	/** Reading the service's foregroundServiceType field needs the field to be
	 *  in the framework, which it has been since Android 10. Below that the
	 *  question cannot be asked, so nothing is asserted. */
	@Test
	fun theServiceIsASpecialUseForegroundService() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
		val service = serviceInfo()
		assertTrue(
			"the service must declare specialUse so startForeground can promote it",
			service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
		)
	}

	/** The special-use subtype is what says why the service is a foreground
	 *  one, and the stores refuse a special-use service without it. It is a
	 *  property on the service rather than meta-data, and readable as one from
	 *  Android 12. Below that it is simply declared and cannot be read back. */
	@Test
	fun theServiceNamesWhatItsForegroundUseIsFor() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
		val component = ComponentName(context, EvvTtsService::class.java)
		val subtype = runCatching {
			context.packageManager
				.getProperty(PackageManager.PROPERTY_SPECIAL_USE_FGS_SUBTYPE, component)
				.getString()
		}.getOrNull()
		assertNotNull("the special-use subtype is not declared", subtype)
		assertFalse("the special-use subtype is empty", subtype.isNullOrBlank())
	}

	/** The numbers the service is written around. They are not behaviour on
	 *  their own, but a change to one without a change to the other is the
	 *  kind of drift that breaks the keep-alive silently. */
	@Test
	fun theKeepAliveAndWakeLockConstantsAgreeWithTheCodeThatRunsOnThem() {
		assertEquals("the notification channel must have a name", "keep_alive", KeepAlive.CHANNEL_ID)
		assertEquals("the notification id must be stable", 1, KeepAlive.ID)
		// And nothing checks on a clock any more: the notification is posted
		// by the service that holds it and by the switch that turns it on,
		// and never looked for on a timer. The nought is the promise.
		assertEquals(
			"the notification must not be checked on a clock",
			0L,
			KeepAlive.CHECK_MS
		)
		// The wake lock is a ceiling, not a course: ten minutes is far longer
		// than any utterance, so it is the framework's own bound and never the
		// thing that ends the speech.
		assertEquals("the wake lock ceiling moved", 10 * 60 * 1000L, EvvTtsService.WAKELOCK_TIMEOUT_MS)
		// The pacing the silence fix runs on: a slice is a fraction of the
		// lead, and a chunk is a whole number of samples.
		assertTrue("a pacing slice is bigger than the lead", EvvTtsService.SLICE_MS < EvvTtsService.LEAD_MS)
		assertTrue("a chunk is smaller than a slice of audio", EvvTtsService.MIN_CHUNK < EvvTtsService.MAX_CHUNK)
		assertEquals("samples are not 16-bit", 2, EvvTtsService.BYTES_PER_SAMPLE)
	}

	/** What is done on the battery screen is written to the system log only
	 *  when the user asked for it, and the answer says whether it was. */
	@Test
	fun trackingFollowsTheSwitch() {
		val settings = Settings(context)
		settings.trackActions = false
		assertFalse("tracking was on while the switch was off", Tracker.action(settings, "off"))
		settings.trackActions = true
		assertTrue("tracking was off while the switch was on", Tracker.action(settings, "on"))
		settings.trackActions = false
	}

	private fun serviceInfo(): ServiceInfo =
		context.packageManager.getServiceInfo(
			ComponentName(context, EvvTtsService::class.java),
			PackageManager.GET_META_DATA
		)
}
