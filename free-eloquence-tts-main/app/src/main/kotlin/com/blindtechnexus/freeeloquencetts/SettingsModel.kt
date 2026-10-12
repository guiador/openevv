package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the settings screen is showing, and the engine it can hear itself on.
 *
 * The screen edits engine numbers and shows percentages, and this is where the
 * two meet. Everything written here goes straight into [Settings], which is
 * what the speech service reads, so a change is in force before the sample
 * that demonstrates it has finished.
 */
class SettingsModel(context: Context) {

	internal var settingsPage = SettingsPage.HOME
	private val app = context.applicationContext
	private val settings = Settings(app)
    var responseExperiment by mutableStateOf(settings.responseExperiment)
        private set
    fun chooseResponseExperiment(value: ResponseExperiment) {
        responseExperiment = value.normalized()
        settings.responseExperiment = responseExperiment
    }

	/** The engine the sample is spoken on. One instance at a time, opened in
	 *  whichever English is chosen, and nothing about it can be changed
	 *  afterwards except the voice. */
	private val preview: EvvPreview = EvvPreview(app)

	/** Which English this app speaks. Two of them, and the choice is what
	 *  every utterance from anywhere without a locale of its own is spoken
	 *  in; see [Settings.language]. */
	var language by mutableStateOf(settings.language)
		private set

	/** Where [language] sits in the offered list, for a button that offers
	 *  them. */
	val languageIndex: Int
		get() = Eci.LANGUAGES.indexOfFirst { it.code == language }.coerceAtLeast(0)

	var voice by mutableStateOf(settings.voice)
		private set

	var expandAbbreviations by mutableStateOf(settings.expandAbbreviations)
		private set

	var codeMode by mutableStateOf(settings.codeMode)
		private set

	var codeCamelCase by mutableStateOf(settings.codeCamelCase)
		private set

	var codeSnakeCase by mutableStateOf(settings.codeSnakeCase)
		private set

	var codeOperators by mutableStateOf(settings.codeOperators)
		private set

	var codeBraces by mutableStateOf(settings.codeBraces)
		private set

	var codeIndentation by mutableStateOf(settings.codeIndentation)
		private set

	var codePaths by mutableStateOf(settings.codePaths)
		private set

	var codeUrls by mutableStateOf(settings.codeUrls)
		private set

	var spellingMode by mutableStateOf(settings.spellingMode)
		private set

	var abbreviations by mutableStateOf(settings.abbreviations)
		private set

	var sampleRateHz by mutableStateOf(settings.sampleRateHz)
		private set

    var equalizer by mutableStateOf(settings.equalizer)
        private set
    var synthesisMode by mutableStateOf(settings.synthesisMode)
        private set
    fun chooseSynthesisMode(value: Int) {
        settings.synthesisMode = value
        synthesisMode = settings.synthesisMode
        if (synthesisMode == 2) chooseSampleRate(16000)
        else if (synthesisMode == 1 && sampleRateHz <= 11025) chooseSampleRate(22050)
    }
    var resampling by mutableStateOf(settings.resampling)
        private set
    fun chooseEqualizer(value: EqualizerConfig) {
        equalizer = value.normalized()
        settings.equalizer = equalizer
    }
    fun chooseResampling(value: Int) {
        settings.resampling = value
        resampling = settings.resampling
    }
    fun chooseTimbre(profile: Int) {
        val params = when (profile) {
            0 -> mapOf(Eci.VOICE_HEAD_SIZE to 75, Eci.VOICE_PITCH_BASELINE to 30, Eci.VOICE_ROUGHNESS to 15, Eci.VOICE_BREATHINESS to 10)
            1 -> mapOf(Eci.VOICE_HEAD_SIZE to 30, Eci.VOICE_PITCH_BASELINE to 75, Eci.VOICE_ROUGHNESS to 5, Eci.VOICE_BREATHINESS to 10)
            else -> mapOf(Eci.VOICE_HEAD_SIZE to 50, Eci.VOICE_PITCH_BASELINE to 50, Eci.VOICE_ROUGHNESS to 0, Eci.VOICE_BREATHINESS to 25)
        }
        params.forEach { (p, v) -> setShape(p, v) }
    }

	var audioOptimization by mutableStateOf(settings.audioOptimization)
		private set

	var pauses by mutableStateOf(settings.pauses)
		private set

	var phrasePrediction by mutableStateOf(settings.phrasePrediction)
		private set

	var symbolMode by mutableStateOf(settings.symbolMode)
		private set

	var customSymbols by mutableStateOf(settings.customSymbols)
		private set

	var readEmoji by mutableStateOf(settings.readEmoji)
		private set

	var numberMode by mutableStateOf(settings.numberMode)
		private set

	var readOtpDigits by mutableStateOf(settings.readOtpDigits)
		private set

	var forceLanguage by mutableStateOf(settings.forceLanguage)
		private set

	var synthesisRouting by mutableStateOf(settings.synthesisRouting)
		private set

	var stripSsml by mutableStateOf(settings.stripSsml)
		private set

	var readTextStyles by mutableStateOf(settings.readTextStyles)
		private set

	var capitalMode by mutableStateOf(settings.capitalMode)
		private set

	var forceRate by mutableStateOf(settings.forceRate)
		private set

	var forcePitch by mutableStateOf(settings.forcePitch)
		private set

	var forceVolume by mutableStateOf(settings.forceVolume)
		private set

	private val shape = mutableStateMapOf<Int, Int>().apply { putAll(settings.shape(settings.voice)) }
    private val preferences = Settings.store(app)
    private val preferenceWatcher = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        responseExperiment = settings.responseExperiment
        resampling = settings.resampling
        synthesisMode = settings.synthesisMode
        audioOptimization = settings.audioOptimization
        sampleRateHz = settings.sampleRateHz
        equalizer = settings.equalizer
        synthesisRouting = settings.synthesisRouting
        forceRate = settings.forceRate
        forcePitch = settings.forcePitch
        forceVolume = settings.forceVolume
        if(voice != settings.voice) voice = settings.voice
        val latestShape = settings.shape(voice)
        if(shape.toMap() != latestShape) { shape.clear(); shape.putAll(latestShape) }
        NotificationControls.refresh(app)
    }
    init { preferences.registerOnSharedPreferenceChangeListener(preferenceWatcher) }

	val gender: Int get() = shape[Eci.VOICE_GENDER] ?: Eci.defaultGender(voice)

	init {
		openPreview()
	}

	/** Opens the preview in the chosen English, and takes the voice's own
	 *  settings from the engine once it is there. The same work happens again
	 *  when the language changes, because the two voices are two engines and
	 *  a slider left at one English's preset would be a lie about the
	 *  other's. */
	private fun openPreview() {
		preview.open(language) {
			// A voice's own settings are what the sliders start at, and only
			// the engine knows them.
			if (!settings.shapeIsCustom(settings.voice)) adoptVoice()
			shape.putAll(settings.shape(settings.voice))
		}
	}

	fun stopSpeaking() {
		preview.stopSpeaking()
	}

	fun close() {
		preferences.unregisterOnSharedPreferenceChangeListener(preferenceWatcher)
		settings.close()
		preview.close()
	}

	fun percentOf(param: Int): Int = Eci.toPercent(param, shape[param] ?: 0)

	// ---- what the screen changes -----------------------------------------

	fun chooseVoice(which: Int) {
		voice = which
		settings.voice = which
		// A voice that has been changed keeps its changes. One that has not
		// takes what the engine says it is.
		if (!settings.shapeIsCustom(which)) adoptVoice()
		shape.clear()
		shape.putAll(settings.shape(which))
	}

	/** Another of the eight. Everything else is left alone: the voice, the
	 *  shape and the speed are the listener's and not the language's, and a
	 *  screen that reset them for changing language would be a screen nobody
	 *  would use twice. */
	fun chooseLanguage(code: Int) {
		val settled = Eci.languageOrDefault(code)
		settings.language = settled
		language = settled
		openPreview()
	}

	/** A notification can change the language while this screen is still open. */
	internal fun syncLanguage() {
		val selected = settings.language
		if (selected == language) return
		language = selected
		openPreview()
	}

	fun setPercent(param: Int, percent: Int) = setShape(param, Eci.fromPercent(param, percent))

	fun setShape(param: Int, value: Int) {
		val settled = Eci.clampVoice(param, value)
		if (shape[param] == settled) return
		shape[param] = settled
		settings.setShapeValue(voice, param, settled)
	}

	/** Forgets this voice's changes. Every other voice is left alone, and so is
	 *  the speed, which belongs to the listener rather than to any of them. */
	fun resetVoice() {
		settings.clearShape(voice)
		adoptVoice()
		shape.clear()
		shape.putAll(settings.shape(voice))
	}

	fun chooseAbbreviations(on: Boolean) {
		abbreviations = on
		settings.abbreviations = on
	}

	fun chooseExpandAbbreviations(on: Boolean) {
		expandAbbreviations = on
		settings.expandAbbreviations = on
	}

	fun chooseCodeMode(on: Boolean) {
		codeMode = on
		settings.codeMode = on
	}

	fun chooseCodeCamelCase(on: Boolean) {
		codeCamelCase = on
		settings.codeCamelCase = on
	}

	fun chooseCodeSnakeCase(on: Boolean) {
		codeSnakeCase = on
		settings.codeSnakeCase = on
	}

	fun chooseCodeOperators(on: Boolean) {
		codeOperators = on
		settings.codeOperators = on
	}

	fun chooseCodeBraces(on: Boolean) {
		codeBraces = on
		settings.codeBraces = on
	}

	fun chooseCodeIndentation(on: Boolean) {
		codeIndentation = on
		settings.codeIndentation = on
	}

	fun chooseCodePaths(on: Boolean) {
		codePaths = on
		settings.codePaths = on
	}

	fun chooseCodeUrls(on: Boolean) {
		codeUrls = on
		settings.codeUrls = on
	}

	fun chooseSpellingMode(mode: Int) {
		spellingMode = mode
		settings.spellingMode = mode
	}

	fun chooseReadEmoji(on: Boolean) {
		readEmoji = on
		settings.readEmoji = on
	}

	fun chooseNumberMode(mode: Int) {
		numberMode = mode
		settings.numberMode = mode
	}

	fun chooseReadOtpDigits(on: Boolean) {
		readOtpDigits = on
		settings.readOtpDigits = on
	}

	fun chooseForceLanguage(on: Boolean) {
		forceLanguage = on
		settings.forceLanguage = on
	}

	fun chooseSynthesisRouting(route: Int) {
		val settled = SynthesisRouting.clamp(route)
		synthesisRouting = settled
		settings.synthesisRouting = settled
	}

	fun chooseStripSsml(on: Boolean) {
		stripSsml = on
		settings.stripSsml = on
	}

	fun chooseReadTextStyles(on: Boolean) {
		readTextStyles = on
		settings.readTextStyles = on
	}

	fun chooseCapitalMode(mode: Int) {
		capitalMode = mode
		settings.capitalMode = mode
	}

	fun choosePhrasePrediction(on: Boolean) {
		phrasePrediction = on
		settings.phrasePrediction = on
	}

	fun chooseSymbolMode(mode: Int) {
		symbolMode = mode
		settings.symbolMode = mode
	}

	fun chooseCustomSymbols(list: String) {
		customSymbols = list
		settings.customSymbols = list
	}

	fun chooseForceRate(on: Boolean) {
		forceRate = on
		settings.forceRate = on
	}

	fun chooseForcePitch(on: Boolean) {
		forcePitch = on
		settings.forcePitch = on
	}

	fun chooseForceVolume(on: Boolean) {
		forceVolume = on
		settings.forceVolume = on
	}

	fun choosePauses(mode: Int) {
		pauses = mode
		settings.pauses = mode
	}

	fun chooseSampleRate(hz: Int) {
		val settled = Eci.nearestSampleRate(hz)
		sampleRateHz = settled
		settings.sampleRateHz = settled
	}

	fun chooseAudioOptimization(mode: Int) {
		audioOptimization = mode
		settings.audioOptimization = mode
	}

	// ---- hearing it ------------------------------------------------------

	/** The sample in the language that will speak it: a German voice
	 *  reading an English sentence is a sample of nothing. */
	private fun previewTextOf(code: Int): Int = when (code) {
		Eci.GERMAN -> R.string.preview_text_de
		Eci.SPANISH_SPAIN, Eci.SPANISH_MEXICO -> R.string.preview_text_es
		Eci.FRENCH_FRANCE, Eci.FRENCH_CANADA -> R.string.preview_text_fr
		Eci.ITALIAN -> R.string.preview_text_it
		else -> R.string.preview_text
	}

	/** Nothing here speaks by itself. Every setting is in force the moment it
	 *  is written down, and this is how it gets heard. The sample goes through
	 *  the same text road as a caller's speech, so what is heard is what the
	 *  setting really does. */
	fun say() {
		// The same snapshot the service takes, so the sample hears the list
		// that is in force rather than a fresh read of the file behind it.
		val dictionary = runCatching { UserDictionary.snapshot(app) }.getOrNull()
		val text = TextPipeline.apply(
			app.getString(previewTextOf(language)),
			settings,
			dictionary?.index ?: WordIndex.EMPTY,
			Abbreviations.entries(app)
		)
		val said = Prosody.prefix(settings.phrasePrediction) +
			Pauses.apply(text, settings.pauses, true)
		preview.say(said, voice, settings.shape(voice), sampleRateHz, settings.abbreviations, settings.audioOptimization, settings.equalizer, settings.resampling, settings.synthesisMode, settings.pauses)
	}

	private fun adoptVoice() {
		val own = preview.presetShape(settings.voice)
		if (own.isEmpty()) return
		// Everything but the speed, which stays where the listener put it.
		// Glen and Sandy ship at 70 where the rest are 50, so taking the
		// voice's own would move the slider and the pace on every change.
		settings.writeShape(settings.voice, own - Eci.VOICE_SPEED)
	}

	companion object {
		val RATES: List<Int> = Eci.SAMPLE_RATES.sorted()

	}
}
