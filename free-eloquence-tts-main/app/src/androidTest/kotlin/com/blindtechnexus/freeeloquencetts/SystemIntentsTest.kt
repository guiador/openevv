package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every intent this app hands to the system, checked as data.
 *
 * These are the parts of the app that leave it, and a mistake in one is
 * invisible from inside: a button that opens something is a button that
 * works, whether it opened the team's WhatsApp or somebody else's, whether it
 * asked about this app's battery use or listed every app on the phone, and
 * whether it went to the speech settings or to the top of the settings app.
 *
 * None of them is started here. Starting one would put a system dialog or
 * somebody's messaging app on the screen of the device running the tests,
 * which is a side effect no assertion is worth.
 */
@RunWith(AndroidJUnit4::class)
class SystemIntentsTest {

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun whatsAppIsTheNumberTheTeamGave() {
		val ways = ExternalLinks.whatsAppWays()
		assertEquals(Intent.ACTION_VIEW, ways[0].action)
		// wa.me rather than w.me. The short form is a session on a signed-in
		// desktop and a web page that says it cannot be loaded on a phone
		// otherwise, which is what the button used to arrive at.
		assertEquals("https://wa.me/${ExternalLinks.WHATSAPP_NUMBER}", ways[0].data.toString())
		// The address wa.me turns into, given on its own because a browser
		// that will not follow the redirect still follows this one.
		assertEquals(
			"https://api.whatsapp.com/send?phone=${ExternalLinks.WHATSAPP_NUMBER}",
			ways[1].data.toString()
		)
		assertEquals(
			"whatsapp://send?phone=${ExternalLinks.WHATSAPP_NUMBER}",
			ways[2].data.toString()
		)
		// Every one of them carries the number in the same international form,
		// with no plus: a plus in a query is read as a space by both of the web
		// addresses, and the digits are the one part of a contact that nobody
		// can correct afterwards.
		for (way in ways) {
			assertTrue(way.data.toString().contains(ExternalLinks.WHATSAPP_NUMBER))
			assertFalse(way.data.toString().contains("+${ExternalLinks.WHATSAPP_NUMBER}"))
		}
	}

	@Test
	fun thePhoneMakersBatteryScreenEndsWithAndroidsOwn() {
		// The names a manufacturer's screen has gone by are tried in order, and
		// the last is Android's own list -- the answer for every device, and the
		// reason this cannot end in a button that did nothing.
		val candidates = ManufacturerScreens.candidates()
		assertTrue(candidates.isNotEmpty())
		assertEquals(
			Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
			candidates.last().intent.action
		)
		// Every one of them is asked for by name rather than by data, so none of
		// them can end up naming somebody else's package: a screen of this app's
		// own is asked for by [ManufacturerScreens.appDetails] instead, and that
		// one does carry the package, which is what the next check is about.
		for (candidate in candidates) {
			assertNull(candidate.intent.data)
			assertNotNull(candidate.intent.component)
		}
		assertEquals(
			context.packageName,
			ManufacturerScreens.appDetailsIntent(context).data?.lastPathSegment
		)
	}

	@Test
	fun makersAreMatchedByNameWhateverCaseTheyComeIn() {
		// A maker of "HTC" is an HTC however it capitalizes itself, and a
		// brand nobody has heard of still gets Android's own list at the
		// end, which is the answer for every device.
		val htc = ManufacturerScreens.candidatesFor("HTC", "htc")
		assertTrue(htc.any { it.intent.component?.packageName == "com.htc.pitroad" })
		val lenovo = ManufacturerScreens.candidatesFor("LENOVO", "ZUK")
		assertTrue(lenovo.any { it.intent.component?.packageName == "com.lenovo.security" })
		val leeco = ManufacturerScreens.candidatesFor("LeEco", "LeEco")
		assertTrue(leeco.any { it.intent.component?.packageName == "com.letv.android.letvsafe" })
		val unknown = ManufacturerScreens.candidatesFor("SomePhone", "SomePhone")
		assertEquals(1, unknown.size)
		assertEquals(
			Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
			unknown.last().intent.action
		)
	}

	@Test
	fun thisAppIsWhereTheInstallerIsToldToLook() {
		// The provider the update hands its file to, read out of the installed
		// manifest rather than out of this file. The authority, the path and the
		// name [Installer] builds have to agree, and a mismatch is an update
		// that cannot be offered for installation at all.
		val info = context.packageManager.getProviderInfo(
			android.content.ComponentName(context.packageName, "androidx.core.content.FileProvider"),
			0
		)
		assertEquals(Installer.authority(context), info.authority)
		assertTrue(info.grantUriPermissions)
		assertFalse(info.exported)
		// And the one directory the provider publishes. Anything else is the
		// IllegalArgumentException that [Installer.install] answers by refusing
		// to start anything, so this is the difference between a download that
		// can be installed and one that sits in the cache for nothing.
		val file = java.io.File(context.cacheDir, "update/probe.apk")
		try {
			file.parentFile?.mkdirs()
			file.writeBytes(ByteArray(4))
			val uri = androidx.core.content.FileProvider.getUriForFile(
				context, Installer.authority(context), file
			)
			assertEquals("content", uri.scheme)
			assertEquals(Installer.authority(context), uri.authority)
			assertTrue(uri.path!!.startsWith("/updates/"))
		} finally {
			file.delete()
		}
	}

	@Test
	fun telegramIsTheChannelTheTeamGave() {
		val ways = ExternalLinks.telegramWays()
		assertEquals(Intent.ACTION_VIEW, ways[0].action)
		assertEquals("https://t.me/${ExternalLinks.TELEGRAM_CHANNEL}", ways[0].data.toString())
		assertEquals(
			"tg://resolve?domain=${ExternalLinks.TELEGRAM_CHANNEL}",
			ways[1].data.toString()
		)
	}

	@Test
	fun emailIsAddressedAndAlreadyHasASubject() {
		val ways = ExternalLinks.emailWays(SUBJECT)
		// SENDTO rather than SEND: SEND offers a chooser of everything that
		// can take text, which is not an email and is a dead end for somebody
		// who cannot see what the choices are.
		assertEquals(Intent.ACTION_SENDTO, ways[0].action)
		assertEquals("mailto:${ExternalLinks.EMAIL_ADDRESS}", ways[0].data.toString())
		assertEquals(SUBJECT, ways[0].getStringExtra(Intent.EXTRA_SUBJECT))
		assertEquals(
			listOf(ExternalLinks.EMAIL_ADDRESS),
			ways[1].getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList()
		)
	}

	@Test
	fun theExemptionIsAskedForByTheScreenThatNamesThisApp() {
		val ways = BatteryExemption.ways(context)
		assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, ways[0].action)
		assertEquals("package:${context.packageName}", ways[0].data.toString())
		// The fallback, for a build whose manufacturer took the first one out.
		assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, ways[1].action)
	}

	@Test
	fun theSpeechSettingsAreAskedForBeforeAnythingElse() {
		val ways = SystemSpeechSettings.ways()
		assertEquals("com.android.settings.TTS_SETTINGS", ways[0].action)
		assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, ways[1].action)
		assertEquals(Settings.ACTION_SETTINGS, ways[2].action)
	}

	@Test
	fun askingTheDeviceWhetherItWillLeaveTheEngineAloneDoesNotThrow() {
		// The answer belongs to the device: an emulator that has never dozed is
		// exempt, and a phone whose manufacturer reimplemented this badly is
		// not. What is being checked is that asking does not take the app down,
		// because the answer is read on the way into the main screen and a
		// crash there would be a crash before anything was ever said.
		BatteryExemption.isGranted(context)
	}

	private companion object {
		const val SUBJECT = "Free eloquence TTS"
	}
}
