package com.blindtechnexus.freeeloquencetts

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.core.os.ConfigurationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.assertNotNull
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText

class SpeechLocaleTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun everyEngineDialectSelectsItsInterfaceLanguage() {
        val titles = mapOf("en" to "Voice settings", "es" to "Ajustes de voz",
            "de" to "Stimmeinstellungen", "fr" to "Réglages vocaux", "it" to "Impostazioni vocali")
        for (language in Eci.LANGUAGES) {
            val localized = SpeechLocale.context(context, language.code)
            assertEquals(language.tag, ConfigurationCompat.getLocales(localized.resources.configuration)[0]?.toLanguageTag())
            assertEquals(titles[language.iso2Language], localized.getString(R.string.settings_title))
        }
    }

    @Test fun regionalVariantsKeepTheExactDialectAndShareTranslations() {
        for (codes in listOf(Eci.ENGLISH_US to Eci.ENGLISH_UK,
            Eci.SPANISH_SPAIN to Eci.SPANISH_MEXICO, Eci.FRENCH_FRANCE to Eci.FRENCH_CANADA)) {
            val first = SpeechLocale.context(context, codes.first)
            val second = SpeechLocale.context(context, codes.second)
            assertNotEquals(ConfigurationCompat.getLocales(first.resources.configuration)[0],
                ConfigurationCompat.getLocales(second.resources.configuration)[0])
            assertEquals(first.getString(R.string.settings_title), second.getString(R.string.settings_title))
        }
    }

    @Test fun interfaceChangesWhenNotificationChangesTheEngineLanguage() {
        val prefs = Settings.store(context)
        val hadLanguage = prefs.contains(Settings.KEY_LANGUAGE)
        val original = prefs.getInt(Settings.KEY_LANGUAGE, Eci.SPANISH_SPAIN)
        try {
            compose.setContent {
                SpeechLocaleContent {
                    // Localization must preserve the Activity for permission and share flows.
                    assertNotNull(androidx.activity.compose.LocalActivity.current)
                    assertNotNull(androidx.compose.ui.platform.LocalContext.current.findActivity())
                    Text(stringResource(R.string.settings_title))
                }
            }
            for ((language, title) in listOf(Eci.GERMAN to "Stimmeinstellungen",
                Eci.FRENCH_CANADA to "Réglages vocaux", Eci.ITALIAN to "Impostazioni vocali",
                Eci.ENGLISH_UK to "Voice settings", Eci.SPANISH_MEXICO to "Ajustes de voz")) {
                compose.runOnIdle { prefs.edit().putInt(Settings.KEY_LANGUAGE, language).apply() }
                compose.onNodeWithText(title).assertExists()
            }
        } finally {
            compose.runOnIdle {
                val editor = prefs.edit()
                if (hadLanguage) editor.putInt(Settings.KEY_LANGUAGE, original)
                else editor.remove(Settings.KEY_LANGUAGE)
                editor.apply()
            }
        }
    }
}
