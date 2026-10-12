package com.blindtechnexus.freeeloquencetts

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * The phone maker's own screen for letting an app keep running.
 *
 * Android's battery optimization exemption is not the whole of the question on
 * a fifth of the phones sold. Xiaomi, Huawei, Honor, Oppo, Realme, OnePlus,
 * Vivo, Samsung, Meizu, Asus, Sony, Lenovo, Zuk, Motorola, LeEco, HTC, Tecno,
 * Itel and Infinix each add a screen of their own in front of it:
 * one that closes an app's background activity, or one that refuses to let it
 * start at all until it is named as an exception. The exemption asked for on
 * Android's own screen does nothing against those, which is why a voice that
 * works in the morning stops working by the evening on a phone where nobody has
 * been told otherwise -- and why the settings for it are on a screen this app
 * cannot know the name of in general.
 *
 * So the names are kept here, one maker at a time, as candidates rather than as
 * one answer per brand: a Xiaomi of Android 9 and a Xiaomi of Android 14 do not
 * have the same settings app, and a name that has moved is a name that fails.
 * Each candidate is *tried*, and the trying is what answers -- the same
 * arrangement [startFirst] uses for a link that may have nothing to take it.
 * Nothing is asked of the package manager first, because a query is exactly the
 * thing Android 11 and later refuse to answer about an app this app has not
 * declared an interest in, while starting an activity by name is answered by the
 * system whatever the visibility rules say. A name that is wrong costs one
 * exception and is passed over, which is the only honest way to hold a list of
 * what may not be there.
 *
 * The last candidate is Android's own list of apps excused from battery
 * optimization, so that a phone none of the names fits still arrives somewhere
 * useful. The difference between that and a button that did nothing is the whole
 * of what this file is for.
 */
internal object ManufacturerScreens {

	/** One screen to try: what it is called, and the intent that opens it. */
	class Candidate(val label: String, val intent: Intent)

	private fun named(label: String, vararg pairs: Pair<String, String>) =
		pairs.map { (pkg, cls) ->
			Candidate(label, Intent(Intent.ACTION_VIEW).setComponent(ComponentName(pkg, cls)))
		}

	/** What to try for this device, best first, the maker's own screens before
	 *  Android's. */
	fun candidates(): List<Candidate> =
		candidatesFor(Build.MANUFACTURER.orEmpty(), Build.BRAND.orEmpty())

	/** What to try for a maker and a brand named outright, rather than this
	 *  phone's own. The matching answers in lowercase, and rooted on purpose:
	 *  in a Turkish locale a plain lowercase turns the I in XIAOMI into a
	 *  dotless one, and the maker is not matched. A test asks through here
	 *  for makers this phone is not. */
	fun candidatesFor(manufacturer: String, brandName: String): List<Candidate> {
		val maker = manufacturer.lowercase(java.util.Locale.ROOT)
		val brand = brandName.lowercase(java.util.Locale.ROOT)
		val out = ArrayList<Candidate>()
		when {
			maker.contains("xiaomi") || maker.contains("redmi") || brand.contains("poco") ->
				out += named(
					"MIUI",
					"com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
					"com.miui.securitycenter" to "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
				)
			maker.contains("huawei") || maker.contains("honor") ->
				out += named(
					"the phone's own startup manager",
					"com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
					"com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
					"com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
				)
			maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus") ->
				out += named(
					"ColorOS",
					"com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
					"com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
					"com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
					"com.oplus.securitypermission" to "com.oplus.securitypermission.startup.StartupAppListActivity"
				)
			maker.contains("vivo") || maker.contains("iqoo") ->
				out += named(
					"Funtouch",
					"com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
					"com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
					"com.iqoo.secure" to "com.iqoo.secure.ui.appoptimize.BackgroundStartUpActivity"
				)
			maker.contains("samsung") ->
				out += named(
					"One UI",
					"com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
					"com.samsung.android.sm_cn" to "com.samsung.android.sm.ui.battery.BatteryActivity",
					"com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity"
				)
			maker.contains("meizu") ->
				out += named(
					"Flyme",
					"com.meizu.safe" to "com.meizu.safe.security.SHOW_APPSEC"
				)
			maker.contains("asus") ->
				out += named(
					"Asus",
					"com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
					"com.asus.mobilemanager" to "com.asus.mobilemanager.MainActivity"
				)
			maker.contains("sony") ->
				out += named(
					"Sony",
					"com.sonyericans.autostartmgr" to "com.sonyericans.autostartmgr.LoadingScreen"
				)
			maker.contains("letv") || maker.contains("leeco") || maker.contains("lenovo") ->
				out += named(
					"Lenovo",
					"com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
					"com.lenovo.security" to "com.lenovo.security.purebackground.PureBackgroundActivity"
				)
			maker.contains("zuk") || maker.contains("motorola") ->
				// Zuk's own phones, and the Motorola ones Lenovo's security
				// center followed to: the background manager is the same
				// pure-background screen under both names.
				out += named(
					"Lenovo",
					"com.lenovo.security" to "com.lenovo.security.purebackground.PureBackgroundActivity"
				)
			maker.contains("htc") ->
				out += named(
					"HTC",
					"com.htc.pitroad" to "com.htc.pitroad.landingpage.activity.LandingPageActivity"
				)
			maker.contains("tecno") || maker.contains("itel") || maker.contains("infinix") ->
				out += named(
					"HiOS",
					"com.transsion.securitycenter" to "com.transsion.securitycenter.startupmanage.StartupManageActivity",
					"com.transsion.phoenix" to "com.transsion.phoenix.ui.activity.AppManagerActivity"
				)
		}
		out += Candidate(
			"Android",
			// Not a question about one app: this is the list Android keeps of the
			// apps it has been told to leave alone, and this app is in it or not
			// in it. The action has existed since the optimization did.
			Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
		)
		return out
	}

	/** Whether this phone's maker is one this file has names for. The row that
	 *  opens the screen says something different when there are none: a person
	 *  is better told that much than sent to a screen of Android's that will not
	 *  have what they are after. */
	// One call rather than two: the list is built from Build fields and costs
	// nothing, but a screen that asks the question and then asks it again to
	// phrase its answer is a screen that can get the two answers out of step.
	fun hasOwnScreen(): Boolean = candidates().size > 1

	/**
	 * Opens the screen, and answers whether anything took the question.
	 *
	 * A refusal is not an error to be logged and forgotten. The row that asks
	 * for this is the one a person is sent to when the voice stops by itself, so
	 * what comes back is a sentence for the live region saying that nothing
	 * opened, and the row that opens this app's own entry in the system's list
	 * of apps, where the maker's switches usually sit when their component names
	 * have moved.
	 */
	fun open(context: Context): Boolean {
		for (candidate in candidates()) {
			try {
				start(context, candidate.intent)
				Tracker.action(context, "opened ${candidate.label}'s screen for what may run in the background")
				return true
			} catch (nothing: ActivityNotFoundException) {
				// The next name on the list, if there is one.
			} catch (refused: SecurityException) {
				// A screen that is there and will not be opened from here.
			}
		}
		Log.i(TAG, "no battery screen on this phone answered")
		return false
	}

	/** This app's entry in the system's list of apps, which is where "Autostart"
	 *  and "Battery" live on several of these phones. */
	fun appDetails(context: Context): Boolean = try {
		start(context, appDetailsIntent(context))
		true
	} catch (nothing: ActivityNotFoundException) {
		false
	} catch (refused: SecurityException) {
		false
	}

	/** The intent [appDetails] starts, separate so that a test can read the
	 *  package out of it rather than launching the settings to find out. */
	fun appDetailsIntent(context: Context) =
		Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
		.setData(Uri.fromParts("package", context.packageName, null))

	private fun start(context: Context, intent: Intent) {
		// Started from an activity, which is where these screens all are, no task
		// of its own is needed; from anywhere else Android refuses to start one.
		if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		context.startActivity(intent)
	}

	private const val TAG = "freeeloquencetts"
}
