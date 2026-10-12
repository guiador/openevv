package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That each of the screens builds itself, and that the settings screen has a
 * row for every setting there is.
 *
 * A screen here is composed rather than inflated, so nothing about it is
 * checked until it is drawn. A string resource that was renamed and not
 * followed, a parameter of the wrong type handed to a row, a `when` that misses
 * a branch: all of them throw at composition time, in front of whoever opened
 * the app, and none of them is caught by a build.
 *
 * ActivityScenario.launch throws if anything in onCreate does, and these
 * screens are composed during it, so getting past being created is the whole
 * of the check. What state the activity settles in afterwards is the device's
 * business -- it does not resume behind a locked screen -- which is why nothing
 * here asks for more.
 *
 * The sliders are checked separately because they are the setting that is easy
 * to lose rather than to break: the screen shows the list [Settings.BEHAVIOURS]
 * and the engine has the list [Settings.SLIDERS], and a parameter added to one
 * of them is a slider that nobody can reach.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

	private val firstLaunch = context.getSharedPreferences(
		SettingsActivity.FIRST_LAUNCH_PREFERENCES, Context.MODE_PRIVATE
	)

	private var wasRead = false

	@Before
	fun rememberWhetherTheWelcomeScreenHasBeenRead() {
		wasRead = firstLaunch.getBoolean(SettingsActivity.FIRST_LAUNCH_COMPLETE, false)
	}

	@After
	fun putItBack() {
		firstLaunch.edit().putBoolean(SettingsActivity.FIRST_LAUNCH_COMPLETE, wasRead).apply()
	}

	@Test
	fun theWelcomeScreenBuildsItself() {
		sayItHasNotBeenRead()
		launch(action = null)
	}

	@Test
	fun theMainScreenBuildsItself() {
		sayItHasBeenRead()
		launch(action = null)
	}

	@Test
	fun theSettingsScreenBuildsItself() {
		sayItHasBeenRead()
		// The gear beside the engine in the system's own text to speech
		// settings, which is the one way in that goes straight there.
		launch(action = SettingsActivity.ACTION_CONFIGURE_ENGINE)
	}

	@Test
	fun everySliderTheEngineHasIsOnTheScreen() {
		assertEquals(
			"the screen shows sliders the engine does not have, or is missing one",
			Settings.SLIDERS.sorted(),
			Settings.BEHAVIOURS.sorted()
		)
	}

	private fun launch(action: String?) {
		val intent = Intent(context, SettingsActivity::class.java).apply {
			if (action != null) setAction(action)
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		}
		ActivityScenario.launch<SettingsActivity>(intent).use { scenario ->
			assertTrue(
				"the screen never got past being created, it is ${scenario.state}",
				scenario.state.isAtLeast(Lifecycle.State.CREATED)
			)
		}
	}

	private fun sayItHasBeenRead() = write(true)

	private fun sayItHasNotBeenRead() = write(false)

	private fun write(read: Boolean) {
		firstLaunch.edit().putBoolean(SettingsActivity.FIRST_LAUNCH_COMPLETE, read).apply()
	}
}
