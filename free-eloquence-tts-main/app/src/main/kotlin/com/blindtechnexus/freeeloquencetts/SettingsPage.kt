package com.blindtechnexus.freeeloquencetts

/** The settings tree is also used by Back and the accessibility pane title. */
internal enum class SettingsPage(val titleRes: Int, val descriptionRes: Int, val parent: String?) {
    HOME(R.string.page_home_title, R.string.page_home_description, null),
    VOICE(R.string.page_voice_title, R.string.page_voice_description, "HOME"),
    READING(R.string.page_reading_title, R.string.page_reading_description, "HOME"),
    DICTIONARIES(R.string.page_dictionaries_title, R.string.page_dictionaries_description, "HOME"),
    SYSTEM(R.string.page_system_title, R.string.page_system_description, "HOME"),
    EXPERIMENTAL(R.string.page_experimental_title, R.string.page_experimental_description, "SYSTEM"),
    SELECTION(R.string.page_selection_title, R.string.page_selection_description, "VOICE"),
    TIMBRE(R.string.page_timbre_title, R.string.page_timbre_description, "VOICE"),
    QUALITY(R.string.page_quality_title, R.string.page_quality_description, "VOICE"),
    EQ(R.string.page_eq_title, R.string.page_eq_description, "VOICE"),
    SPEED(R.string.page_speed_title, R.string.page_speed_description, "TIMBRE"),
    PITCH(R.string.page_pitch_title, R.string.page_pitch_description, "TIMBRE"),
    COLOR(R.string.page_color_title, R.string.page_color_description, "TIMBRE"),
    EQ_BANDS(R.string.page_eq_bands_title, R.string.page_eq_bands_description, "EQ"),
    EQ_TIMBRE(R.string.page_eq_timbre_title, R.string.page_eq_timbre_description, "EQ"),
    RESAMPLING(R.string.page_resampling_title, R.string.page_resampling_description, "EQ"),
    TEXT(R.string.page_text_title, R.string.page_text_description, "READING"),
    NUMBERS(R.string.page_numbers_title, R.string.page_numbers_description, "READING"),
    PROSODY(R.string.page_prosody_title, R.string.page_prosody_description, "READING"),
    CODE(R.string.page_code_title, R.string.page_code_description, "READING"),
    NOTIFICATION(R.string.page_notification_title, R.string.page_notification_description, "SYSTEM"),
    ENGINES(R.string.page_engines_title, R.string.page_engines_description, "SYSTEM");

    val title: String
        @androidx.compose.runtime.Composable get() = androidx.compose.ui.res.stringResource(titleRes)
    val description: String
        @androidx.compose.runtime.Composable get() = androidx.compose.ui.res.stringResource(descriptionRes)

    fun parentPage(): SettingsPage? = parent?.let { valueOf(it) }
    fun children(): List<SettingsPage> = entries.filter { it.parent == name }
}
