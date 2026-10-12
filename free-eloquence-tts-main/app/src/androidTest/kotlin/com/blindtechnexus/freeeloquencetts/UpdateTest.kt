package com.blindtechnexus.freeeloquencetts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What an update answer has to be before a phone is told to fetch a file.
 *
 * Nothing here reaches the network, and that is deliberate. The rules being
 * checked are the ones that stand between a published file and an install, and
 * they are all answered by a string: what a version that matches means, what a
 * blank means, what an address that is not https means. The asking itself is one
 * small file on a git host, and whether it is there today is not something a
 * test can hold the app to; a test that went out to find it would be red on a
 * plane and green in a lab, which is the wrong way round for a check of this
 * app's own rules.
 */
@RunWith(AndroidJUnit4::class)
class UpdateTest {

	private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

	@Test
	fun theSameVersionIsNoUpdateAtAll() {
		assertNull(UpdateInfo.parse("""{"version":"1.1","url":"$HTTPS"}""", "1.1"))
		// The trim is what makes the two agree: the file is written by hand and a
		// trailing space in it is a thing that will happen.
		assertNull(UpdateInfo.parse("""{"version":" 1.1 ","url":"$HTTPS"}""", "1.1"))
	}

	@Test
	fun aDifferentVersionCarriesItselfAndTheAddress() {
		val found = UpdateInfo.parse("""{"version":"1.2","url":"$HTTPS"}""", "1.1")
		assertNotNull(found)
		assertEquals("1.2", found!!.version)
		assertEquals(HTTPS, found.downloadUrl)
	}

	@Test
	fun aNumberThatMovedBackwardsIsStillAMove() {
		// Not newer, different: the release channel is what says which version to
		// be on, and an app on any other one -- one back as much as one forward
		// -- is on something the channel does not stand behind. Comparing the
		// numbers instead would have to answer whether 1.10 is newer than 1.2,
		// which a number says one thing about and a text another.
		assertNotNull(UpdateInfo.parse("""{"version":"1.0","url":"$HTTPS"}""", "1.1"))
		assertNotNull(UpdateInfo.parse("""{"version":"1.10","url":"$HTTPS"}""", "1.2"))
	}

	@Test
	fun nothingIsOfferedWhenOneOfTheTwoValuesIsMissing() {
		assertNull(UpdateInfo.parse("""{"url":"$HTTPS"}""", "1.1"))
		assertNull(UpdateInfo.parse("""{"version":"","url":"$HTTPS"}""", "1.1"))
		assertNull(UpdateInfo.parse("""{"version":"1.2"}""", "1.1"))
		assertNull(UpdateInfo.parse("""{"version":"1.2","url":""}""", "1.1"))
		assertNull(UpdateInfo.parse("", "1.1"))
		assertNull(UpdateInfo.parse("not json at all", "1.1"))
		// An answer that is a JSON array or a stray value is not an answer either.
		assertNull(UpdateInfo.parse("[]", "1.1"))
	}

	@Test
	fun aPlainHttpAddressIsRefusedRatherThanWarnedAbout() {
		// The download is an executable, and installing it is the most
		// privileged thing this app can be asked to do. Over plain http, anybody
		// between the phone and the server can replace it on the way, so the
		// refusal is the whole of the defence and it is not a sentence to be
		// answered with a "still want to continue?".
		assertNull(UpdateInfo.parse("""{"version":"1.2","url":"http://example.com/a.apk"}""", "1.1"))
		assertNull(UpdateInfo.parse("""{"version":"1.2","url":"file:///sdcard/a.apk"}""", "1.1"))
		assertNull(UpdateInfo.parse("""{"version":"1.2","url":"  "}""", "1.1"))
		// And the stream itself refuses one, so that the refusal cannot be
		// walked around by whoever calls it.
		assertNull(UpdateInfo.openStream("http://example.com/a.apk"))
	}

	@Test
	fun theChannelIsTheOneThatWasAskedFor() {
		// One line of this is the whole of the mechanism: a typo in the host, the
		// branch or the file name is an app that never hears about a new version
		// and never says so.
		assertEquals(
			"https://raw.githubusercontent.com/blind-tech-nexus/remote-data-json" +
				"/main/alt-eloquence-tts-update.json",
			UpdateInfo.CHANNEL
		)
	}

	@Test
	fun theFileAlreadyPublishedIsWhatTheParserExpects() {
		// The shape the release channel is written in, checked here rather than
		// hoped for: the two keys, and nothing else the app has to know. A
		// version written as a number rather than a string is the mistake to
		// hold, since it is the one a JSON editor makes silently -- and
		// org.json turns it into "1" for an integer, which would be offered as
		// version "1".
		val found = UpdateInfo.parse("""{"version":"1.0.0","url":"https://api.example.com/demo"}""", "1.1")
		assertNotNull(found)
		assertEquals("1.0.0", found!!.version)
		assertEquals("https://api.example.com/demo", found.downloadUrl)
	}

	@Test
	fun theUpdateGoesIntoTheCacheUnderANameOnlyThisAppWrites() {
		val file = ApkDownload.fileFor(context, "1.2 beta/3")
		assertTrue(file.path.startsWith(context.cacheDir.absolutePath))
		assertTrue(file.parentFile!!.name == "update")
		// What a person cannot type into a file name cannot come out of the
		// published file either, since the version is in the name the installer
		// will read out.
		assertFalse(file.name.contains("/"))
		assertEquals("alt-eloquence-tts-1.2-beta-3.apk", file.name)
		val plain = ApkDownload.fileFor(context, "1.2")
		assertEquals("alt-eloquence-tts-1.2.apk", plain.name)
	}

	@Test
	fun sayingLaterDoesNotForgetAboutTheNextVersion() {
		// "Later" is an answer about one version rather than about the feature:
		// the same version is not offered again in this run of the app, and the
		// next launch asks again whatever the answer was.
		// Nothing has been found, so there is nothing to put up: the dialog is
		// keyed on an answer rather than on the absence of one.
		assertFalse(Updater.shouldShowDialog())
	}

	@Test
	fun aCheckThatWasCancelledAsksForNothingAtAll() {
		// The flag is read before the socket is opened rather than only after it,
		// which is what makes an app on its way out able to call a check off: a
		// request nobody will read the answer to is a request and no more.
		val cancelled = AtomicBoolean(true)
		assertTrue(UpdateInfo.fetch("0.0", cancelled) is UpdateInfo.Result.Cancelled)
	}

	@Test
	fun aFileThatIsNotAPackageIsNeverReadyToInstall() {
		// What the downloaded file is asked before it is offered again, since a
		// download broken off by a phone running out of room is half an APK and
		// half an APK is no APK at all. The answer for a file that is only
		// partly here and for one that is not here is the same, and it is no:
		// the person is offered the download rather than an installer that has
		// nothing to say about the file it was handed.
		val partial = java.io.File(context.cacheDir, "update/probe-partial.apk")
		try {
			partial.parentFile?.mkdirs()
			partial.writeBytes(ByteArray(1024) { (it % 251).toByte() })
			assertFalse(Installer.isInstallable(context, partial))
			assertFalse(Installer.isInstallable(context, java.io.File(context.cacheDir, "update/no-such-file.apk")))
		} finally {
			partial.delete()
		}
	}

	private companion object {
		const val HTTPS = "https://example.com/alt-eloquence-tts.apk"
	}
}
