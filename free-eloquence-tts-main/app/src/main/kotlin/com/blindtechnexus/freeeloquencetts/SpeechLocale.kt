package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** Localized contexts still belong to the Activity that owns their UI. */
internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The interface follows the chosen engine dialect, not individual caller requests. */
internal object SpeechLocale {
    fun context(base: Context, language: Int = Settings.store(base).getInt(Settings.KEY_LANGUAGE, Eci.SPANISH_SPAIN)): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Eci.languageOf(language).locale())
        // Preserve the Activity in the wrapper chain for permission launchers,
        // sharing and Compose's LocalActivity lookup.
        return ContextThemeWrapper(base, 0).apply { applyOverrideConfiguration(configuration) }
    }
}

@Composable
internal fun SpeechLocaleContent(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    val prefs = remember(base) { Settings.store(base) }
    var language by remember(prefs) { mutableIntStateOf(prefs.getInt(Settings.KEY_LANGUAGE, Eci.SPANISH_SPAIN)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Settings.KEY_LANGUAGE) {
                language = prefs.getInt(key, Eci.SPANISH_SPAIN)
                NotificationControls.refresh(base)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val localized = remember(base, language, systemConfiguration) { SpeechLocale.context(base, language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content
    )
}
