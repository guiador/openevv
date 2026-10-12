package com.blindtechnexus.freeeloquencetts

/** The numbers the published ECI interface takes, and the few tables that turn
 *  them into something Android understands. */
object Eci {

	const val PARAM_SYNTH_MODE = 0
	const val PARAM_INPUT_TYPE = 1
	const val PARAM_TEXT_MODE = 2
	const val PARAM_DICTIONARY = 3
	const val PARAM_SAMPLE_RATE = 5
	const val PARAM_REAL_WORLD_UNITS = 8
	const val PARAM_LANGUAGE_DIALECT = 9
	const val PARAM_NUMBER_MODE = 10

	const val VOICE_GENDER = 0
	const val VOICE_HEAD_SIZE = 1
	const val VOICE_PITCH_BASELINE = 2
	const val VOICE_PITCH_FLUCTUATION = 3
	const val VOICE_ROUGHNESS = 4
	const val VOICE_BREATHINESS = 5
	const val VOICE_SPEED = 6
	const val VOICE_VOLUME = 7

	/** Voice 0 is the one being spoken in; 1 to 8 are the language's presets
	 *  and are read only; 9 to 16 are the caller's to edit. */
	const val VOICE_CURRENT = 0
	const val FIRST_PRESET = 1
	const val LAST_PRESET = 8
	const val SCRATCH_VOICE = 9

	const val NUM_VOICE_PARAMS = 8

	/** The volumes a dictionary set holds. The fourth, the extended main
	 *  dictionary, is for a language written in another script and every call
	 *  refuses it here. */
	const val DICT_MAIN = 0
	const val DICT_ROOT = 1
	const val DICT_ABBREVIATION = 2

	val DICT_VOLUMES = listOf(DICT_MAIN, DICT_ROOT, DICT_ABBREVIATION)

	/**
	 * The speed every voice is spoken at unless someone has chosen otherwise.
	 *
	 * Six of the eight presets ship at 50 and Glen and Sandy ship at 70, which
	 * is half again as fast. That is IBM's data rather than a fault, but it
	 * makes speed a property of the voice, so picking Glen sped everything up
	 * without anyone asking for it. Speed belongs to whoever is listening.
	 */
	const val DEFAULT_SPEED = 50

	const val SPEED_MAX = 250
	const val PERCENT_MAX = 100

	/** What each of the eight will take. Gender is a choice of two, speed runs
	 *  to 250, and the rest are hundredths. */
	fun voiceRange(param: Int): IntRange = when (param) {
		VOICE_GENDER -> 0..1
		VOICE_SPEED -> 0..SPEED_MAX
		else -> 0..PERCENT_MAX
	}

	fun clampVoice(param: Int, value: Int): Int {
		val range = voiceRange(param)
		return value.coerceIn(range.first, range.last)
	}

	/** The eight are on three different scales, and a screen reader saying a
	 *  bare number leaves it to the listener to remember which. So the settings
	 *  screen works in hundredths of whatever the setting's own range is, which
	 *  for six of the seven sliders is the number the engine already uses and
	 *  for speed is a fifth of it. */
	fun toPercent(param: Int, value: Int): Int {
		val top = voiceRange(param).last
		if (top <= 0) return 0
		return Math.round(value * 100.0 / top).toInt().coerceIn(0, PERCENT_MAX)
	}

	fun fromPercent(param: Int, percent: Int): Int {
		val top = voiceRange(param).last
		return Math.round(percent.coerceIn(0, PERCENT_MAX) * top / 100.0).toInt()
	}

	/** What eciSampleRate's index means in hertz, in the order the engine
	 *  numbers them. The first four are IBM's numbering and the rest openevv's. */
	val SAMPLE_RATES = intArrayOf(8000, 11025, 22050, 16000, 32000, 44100, 48000, 24000)

	fun sampleRateHz(index: Int): Int =
		if (index in SAMPLE_RATES) index else SAMPLE_RATES.getOrElse(index) { SAMPLE_RATES[1] }

	/** The engine's index for [hz], snapped to the nearest rate the engine
	 *  has, so a rate from a preference or a caller is never the wrong one
	 *  by default. */
	fun sampleRateIndex(hz: Int): Int {
		val at = SAMPLE_RATES.indexOf(nearestSampleRate(hz))
		return if (at in 0..6) at else if (at >= 0) SAMPLE_RATES[at] else 1
	}

	/** The names the eight presets have gone by since Eloquence shipped, and
	 *  Luna after them. The engine numbers its own eight 1 to 8 in this
	 *  order; Luna is this app's own ninth voice, built on one of them (see
	 *  [LUNA_BASE_PRESET]) with a sweeter shape laid over it (see
	 *  [LUNA_SHAPE]). */
	val PRESET_NAMES = arrayOf(
		"Reed", "Shelley", "Bobby", "Rocko", "Glen", "Sandy", "Grandma", "Grandpa", "Luna"
	)

	/** Where Luna sits in [PRESET_NAMES]. */
	const val LUNA_INDEX = 8

	/**
	 * The engine preset Luna is built on, in the engine's own numbering 1
	 * to 8: Sandy, Adult Female 2. Shelley -- Adult Female 1 -- was the base
	 * before her, and the trouble with Shelley was exactly the trouble this
	 * is meant to fix: a voice built on the same preset as another voice
	 * sounds like that voice, however much is laid over the top of it. Two
	 * female voices that differ by a few numbers are two takes of one
	 * recording; two that start from different presets are two voices.
	 *
	 * Both are female, so this is not a choice about gender -- it is a choice
	 * about which of the two the shape below is then pushed away from. Sandy
	 * is the brighter of the two, and a bright voice pushed brighter is
	 * unmistakably not the other one.
	 */
	const val LUNA_BASE_PRESET = 6

	/**
	 * What makes Luna Luna: a sweet, clearly female voice, higher and
	 * brighter than the preset underneath her, smooth rather than rough,
	 * with enough breath in it to sound soft and enough lilt to sound
	 * alive. By the numbers eciSetVoiceParam takes, and applied under
	 * anything the listener has chosen for her, so her own settings still
	 * win where they exist.
	 *
	 * The numbers are chosen against the two complaints a flat female
	 * voice earns: sounding male, and sounding robotic. What reads as
	 * male is a low pitch on a large head, so the baseline sits high and
	 * the head small; what reads as robotic is a monotone with an edge,
	 * so the fluctuation is generous, the roughness is nearly nothing,
	 * and the breathiness rounds what is left. The speed is deliberately
	 * not here -- see [EvvEngine.applyVoice], which keeps every voice at
	 * the listener's own pace.
	 */
	val LUNA_SHAPE: Map<Int, Int> = mapOf(
		VOICE_GENDER to 1,
		VOICE_PITCH_BASELINE to 92,
		VOICE_PITCH_FLUCTUATION to 70,
		VOICE_HEAD_SIZE to 12,
		VOICE_ROUGHNESS to 3,
		VOICE_BREATHINESS to 38,
		VOICE_VOLUME to 92
	)

	/**
	 * What the two numbers that carry a voice's gender are doing, since
	 * neither of them is called gender and both were tuned by ear rather
	 * than by name.
	 *
	 * The engine's gender parameter is a label: it tells a caller what the
	 * voice is and changes nothing about how it sounds. What a listener
	 * hears as female is a small head -- shorter vocal folds and a shorter
	 * tract, so higher formants, which is the timbre and not the pitch --
	 * and a pitch baseline sitting high in the voice's own range. So Luna
	 * takes the smallest head and the highest baseline the presets allow,
	 * and the numbers around them are what keep that from sounding like a
	 * caricature: the fluctuation gives her a lilt rather than a monotone,
	 * the roughness is nearly nothing so nothing gravels up the top of the
	 * range, and the breathiness rounds off the edge that a hard pitch
	 * alone would have.
	 *
	 * [VOICE_SPEED] is deliberately absent: speed belongs to the listener,
	 * not to any voice. See [EvvEngine.applyVoice].
	 */

	/** The gender a voice nobody has touched is: female for Luna, male for
	 *  the rest, which is what their presets carry. */
	fun defaultGender(index: Int): Int = if (index == LUNA_INDEX) 1 else 0

	/**
	 * The eight languages this app speaks: the eight IBM finished, which are
	 * the eight whose modules are compiled into this build.
	 *
	 * The numbers are the engine's own -- `eciGeneralAmericanEnglish` and
	 * the rest in its include/eci.h -- and the modules behind them are the
	 * engine's own directories, `lang/enus` and the rest, which the build
	 * script names and the Makefile compiles. The words are IBM's and not
	 * ours: the family is the top half, the code set the third byte and the
	 * dialect the bottom one, so the two Englishes differ in one bit and
	 * speak in two accents.
	 *
	 * Every one of them is carried, every one is offered, and none is closed
	 * to make room for another: a screen reader that reads an English page
	 * and then a German one must not pay a second of silence between them,
	 * which is what remaking an instance costs. See [EvvTtsService].
	 */
	const val ENGLISH_US = 0x00010000
	const val ENGLISH_UK = 0x00010001
	const val SPANISH_SPAIN = 0x00020000
	const val SPANISH_MEXICO = 0x00020001
	const val FRENCH_FRANCE = 0x00030000
	const val FRENCH_CANADA = 0x00030001
	const val GERMAN = 0x00040000
	const val ITALIAN = 0x00050000

	/** One language the app speaks: the engine's own number for it, the
	 *  spellings Android asks about in both lengths, the BCP-47 tag a
	 *  [java.util.Locale] is built from, and what to call it on a screen.
	 *  [module] is the engine's directory under lang/, which is the one
	 *  the build script names. */
	class Language(
		val code: Int,
		val iso2Language: String,
		val iso3Language: String,
		val iso2Country: String,
		val iso3Country: String,
		val label: String,
		val module: String
	) {
		/** The tag a locale or a voice name is built from: en-US, fr-CA. */
		val tag: String get() = "$iso2Language-$iso2Country"

		fun locale(): java.util.Locale = java.util.Locale(iso2Language, iso2Country)

		/** Whether Android's words for a language, in either length and in
		 *  either case, name this one's language. */
		fun speaks(lang: String?): Boolean =
			lang != null && (lang.equals(iso2Language, true) || lang.equals(iso3Language, true))

		/** Whether Android's words for a country name this one's. */
		fun inCountry(country: String?): Boolean =
			country != null && (country.equals(iso2Country, true) || country.equals(iso3Country, true))
	}

	/** All eight, the American first, which is what every list that does
	 *  not say otherwise is ordered by, and then the rest in the engine's
	 *  own order. */
	val LANGUAGES = listOf(
		Language(ENGLISH_US, "en", "eng", "US", "USA", "English (United States)", "enus"),
		Language(ENGLISH_UK, "en", "eng", "GB", "GBR", "English (United Kingdom)", "engb"),
		Language(SPANISH_SPAIN, "es", "spa", "ES", "ESP", "Spanish (Spain)", "eses"),
		Language(SPANISH_MEXICO, "es", "spa", "MX", "MEX", "Spanish (Mexico)", "esus"),
		Language(FRENCH_FRANCE, "fr", "fra", "FR", "FRA", "French (France)", "frfr"),
		Language(FRENCH_CANADA, "fr", "fra", "CA", "CAN", "French (Canada)", "frca"),
		Language(GERMAN, "de", "deu", "DE", "DEU", "German", "dede"),
		Language(ITALIAN, "it", "ita", "IT", "ITA", "Italian", "itit")
	)

	/** The ISO-3 language of the default voice, which is what Android
	 *  asked about when there was one language. */
	const val ISO3_LANGUAGE = "eng"

	/** The two-letter language of the default voice. */
	const val ISO2_LANGUAGE = "en"

	/** The ISO-3 country of [language], or the American one for a number
	 *  this build does not know: an answer that is always one of the eight
	 *  supported rather than nothing at all. */
	fun countryOf(language: Int): String = languageOf(language).iso3Country

	/** The same question with the two letters [java.util.Locale] wants. */
	fun iso2CountryOf(language: Int): String = languageOf(language).iso2Country

	/** The one of the eight [code] names, or the American one for anything
	 *  else, so that a preference written by another build or edited by hand
	 *  is a voice rather than a refusal. */
	fun languageOf(code: Int): Language =
		LANGUAGES.firstOrNull { it.code == code } ?: LANGUAGES[0]

	/** [code] when it is one of the eight, [ENGLISH_US] otherwise. */
	fun languageOrDefault(code: Int): Int =
		if (LANGUAGES.any { it.code == code }) code else ENGLISH_US

	/** Whether [code] is one of the eight. */
	fun knowsLanguage(code: Int): Boolean = LANGUAGES.any { it.code == code }

	/**
	 * The language Android's words name, or null when they name none of
	 * the eight.
	 *
	 * A language and a country that match a module exactly -- en and GB,
	 * fra and CAN -- are that module. A language alone, or a language with
	 * a country no module has -- en-AU, es-AR, fr-BE, de-CH -- is the first
	 * module of that language, which is the mother country's: the American
	 * English, the Castilian Spanish, the French of France. A language the
	 * engine has no module for is null, and what the caller does with null
	 * is its business; the service speaks the settings screen's language.
	 */
	fun languageFor(lang: String?, country: String?): Language? {
		if (lang.isNullOrEmpty()) return null
		var family: Language? = null
		for (language in LANGUAGES) {
			if (!language.speaks(lang)) continue
			if (language.inCountry(country)) return language
			if (family == null) family = language
		}
		return family
	}

	/** The ISO-3 country the default voice name is built from, kept as its
	 *  own name because it is what the service answered before there were
	 *  two and what anything holding a voice name still expects. */
	const val ISO3_COUNTRY = "USA"

	/** What a name of one of this app's voices is built from: the engine's
	 *  language, a dash, the country and the preset -- eng-USA-Reed,
	 *  deu-DEU-Reed. The same shape as the American one has always had,
	 *  extended to the other seven. */
	fun voiceName(preset: Int, language: Int = ENGLISH_US): String {
		val spoken = languageOf(language)
		return spoken.iso3Language + "-" + spoken.iso3Country + "-" + PRESET_NAMES[preset]
	}

	/**
	 * The language a voice name belongs to, read out of the name itself, or
	 * null when the name is not one of this app's.
	 *
	 * This app's names are three letters, a dash, three letters, a dash and
	 * a preset; a locale tag such as en-GB or de-DE in front of anything is
	 * read too, because a router that builds its own names builds them out
	 * of the locale. A name that is neither -- a name from another engine
	 * -- is nobody's, and the caller falls back to the settings screen's
	 * language, which is the voice that speaks.
	 */
	fun languageOfVoiceName(name: String): Language? {
		val parts = name.split('-', '_')
		if (parts.size < 2) return null
		return languageFor(parts[0], parts[1])
	}

	/**
	 * The rate the engine has that is nearest to [hz], for a number that
	 * came from a preference or a caller rather than from the list.
	 *
	 * A rate the engine does not have is a rate the audio would be labelled
	 * with and not the rate it was made at, and every word would come out
	 * at the wrong pitch and pace. Nearest by distance in hertz, and the
	 * default of eleven thousand and twenty five for anything that is not a
	 * positive number at all.
	 */
	fun nearestSampleRate(hz: Int): Int {
		if (hz <= 0) return SAMPLE_RATES[1]
		var best = SAMPLE_RATES[1]
		var distance = Int.MAX_VALUE
		for (rate in SAMPLE_RATES) {
			val away = kotlin.math.abs(rate - hz)
			if (away < distance) {
				distance = away
				best = rate
			}
		}
		return best
	}

	/**
	 * How the engine raises its own eleven thousand and twenty five hertz to a
	 * higher rate. These are the CVT_* values from the engine's
	 * src/eci/sound/eci_pcm.h, and [EvvNative.setUpsampleMethod] carries one
	 * of them down. See [AudioOptimization] for which mode uses which, and why
	 * the choice is audible: the engine's true bandwidth never changes, so the
	 * method is what decides what the new band carries -- a mirror of the
	 * speech, a curve through it, or nothing at all.
	 */
	const val UPSAMPLE_HOLD = 0
	const val UPSAMPLE_LINEAR = 2
	const val UPSAMPLE_CUBIC = 3
	const val UPSAMPLE_SINC = 4
}
