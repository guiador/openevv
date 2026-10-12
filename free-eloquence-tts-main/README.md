# Free eloquence TTS

Free eloquence TTS is a free alternative Eloquence text-to-speech engine for Android and Wear OS.

This edition supports eight TTS dialects and interfaces in English, Spanish,
German, French and Italian. The interface follows the selected voice language.
See [Android 17 and localization](ANDROID17-IDIOMAS.md) for build requirements,
the background audio fallback and validation instructions.

The speech comes from [openevv](https://github.com/Mudb0y/openevv), a C rebuild of IBM's Embedded ViaVoice. This repo cross-compiles that engine for Android, wraps it in JNI, and puts a `TextToSpeechService` around it. openevv is a submodule and is not edited in place.

## Unofficial

A hobby project. Nobody's product. Not from IBM, Nuance, Cerence, Google or the openevv project, and none of them have endorsed or reviewed it.

It is not a supported screen reader engine. If you set it as your TalkBack voice and it stops talking, you have to undo that yourself, so read "Turning it off" before you turn it on.

No warranty. See LICENSE, and read the part about language data before redistributing anything.

## What works

* Eight dialects: en-US, en-GB, es-ES, es-MX, fr-FR, fr-CA, de-DE and it-IT. Requests without a supported locale use the language selected in settings. Caller requests do not change the interface language.
* Nine preset voices: Reed, Shelley, Bobby, Rocko, Glen, Sandy, Grandma, Grandpa and Luna -- nine per dialect, offered through the Android voice API. Luna is a female voice built on Sandy rather than Shelley. See [Voices](#voice-phrase-prediction-abbreviations).
* All eight voice parameters: speed, pitch, inflection, head size, roughness, breathiness, volume, gender.
* Audio optimization above the sample rate: balanced, louder, or cleaner finishing of the samples. See [Audio optimization](#audio-optimization).
* Speech rate and pitch from the system settings, with a switch to force this app's own over them.
* Symbol pronunciation: all, most, some, a custom list, or none.
* Pronunciation dictionaries, and a user dictionary whose matching is a bounded index rather than a trie of objects: a file of lakhs or millions of words is an import and not a crash, and the words that do not fit are counted and said out loud rather than silently dropped. See [The user dictionary](#the-user-dictionary).
* Abbreviations and expansions of your own, with a choice of how each one is matched: see [Expansions](#abbreviations-and-expansions).
* Code reading: camelCase and snake_case names taken apart, operators and brackets named, indentation counted, file paths and addresses said as themselves. Seven switches under one: see [Reading source code](#reading-source-code).
* Spelling a word out, letter by letter or in the NATO phonetic alphabet, either always or only for a word written in capitals.
* A document is spoken from its first sentence rather than from its last, and the sentence after the one being spoken is prepared while it is being spoken, on a thread of its own: see [A long text is streamed](#a-long-text-is-streamed).
* One notification, and one switch that decides it: "Run in foreground even while not in use" posts it and keeps the engine in the foreground whether or not anything is bound, and with the switch off nothing in this app posts a notification at all -- not while speaking, not in the background, and not on a timer. A stop button on it, and the same notification coming back after a restart for whoever wants that. See [Keeping the voice alive](#keeping-the-voice-alive-when-nothing-is-speaking).
* Text styles read out in words: bold, italic, links, images, headings, bullets, quotes and the rest, when the text carries them -- which is what a web page read by a browser hands over. See [Web pages and styled text](#web-pages-and-styled-text).
* An audio generator: type text, speak it in your voice, and share or save the file.
* An update channel, checked in the background on every launch, that ends at Android's own installer: see [Updates](#updates).
* Android 6 and later. arm64-v8a, armeabi-v7a, x86_64.

`app/src/androidTest` runs on a device: the tests that matter most are the ones that never start a sound.

## Turning it on

TalkBack settings, Text-to-speech settings, Preferred engine, Free eloquence TTS. The gear next to it opens the voice settings.

## Turning it off

TalkBack settings, Text-to-speech settings, Preferred engine, Google.

## Building

Needs Android SDK 37.0, NDK r28c (28.2.13676358), JDK 17 or 21, Python 3, GNU Make and bash. The wrapper fetches Gradle 9.3.1. Gradle selects the pinned NDK from the Android SDK.

```
git submodule update --init --recursive
./gradlew installPrincipalDebug
./gradlew connectedPrincipalDebugAndroidTest
./gradlew assemblePrincipalDebugAndroidTest
```

The middle line needs a phone. The last one does not: it compiles the instrumented
tests against the app and stops there, which is the half of the check that can be
done where no device is plugged in -- and it is worth doing, because a test that
has never been compiled is a test that has never been read.

First build is about four minutes per ABI. The rules are the part that grows: `make rules` writes the English rules as C -- about 13 MB -- and that cost is paid once rather than once per ABI, so the ABIs after the first are the compile and link only. Expect a few minutes for a clean build, and use `-Pevvdroid.rules=bytecode` while working on the Kotlin.

| property | default | effect |
| --- | --- | --- |
| `evvdroid.abis` | `arm64-v8a,armeabi-v7a,x86_64` | which ABIs to build |
| `evvdroid.rules` | `c` | `bytecode` builds in 30s and runs at under half the speed |
| `evvdroid.abiSplits` | `false` | one APK per ABI |

`-Pevvdroid.abis=arm64-v8a -Pevvdroid.rules=bytecode` is the fast loop for working on the Kotlin. Compiled rules are the default because interrupting and restarting costs about a third of what it costs interpreted, and a screen reader interrupts constantly.

A fix to openevv that upstream has not taken yet goes in `native/patches` and is applied at build time. There are two: `0001-upsample-method-override.patch` gives the front end's Audio Optimization setting the runtime choice of upsampling method; `0002-resampler-phase-overflow.patch` validates rates and prevents phase overflow in large audio blocks while preserving upstream wideband downsampling. The engine revision is recorded in `native/openevv-revision.txt` and participates in Gradle's native build inputs.

## Screens

Four, in one activity. A screen reader announces the departure and the arrival of every activity, an engine preview opened for the settings screen would be closed and reopened by a trip to the about screen, and a back stack of four activities is four things to be somewhere inside when the phone answers. So the screens are a state, the back key moves it, and "Go back" in the title bar does what the back key does.

*Welcome*, once, on a first launch: what the app is, and what it is about to ask for.

*Main*, which is what the launcher opens: what the app is, then the three things worth doing -- configure the voice, open the system's text-to-speech settings and choose this engine there, read about the app -- each with the reason said before the button that acts on it.

*Eloquence settings*, which is also what the gear beside the engine in the system settings opens, straight to it rather than by way of the main screen. Eight sections, each a heading TalkBack can jump to: voice customization, voice behaviors, voice quality -- audio optimization above the sample rate -- voice dictionary settings, text reading, code reading, other settings, and a voice to test the lot of them on.

*Expansions*, reached from the dictionary section: the abbreviations and expansions of your own, as a list with an add button, an options button on each row, and an import and an export beside them.

*Dictionary*, reached the same way: the words the engine is to say differently from how they are spelled.

*About us*: what the app is, who made it -- Sujan Rai, with the Blind Tech Nexus team -- and three ways to reach them, as buttons rather than as addresses to copy down. WhatsApp on [wa.me/9779708340992](https://wa.me/9779708340992) for bugs, feedback and feature requests, the Telegram channel on [t.me/blindtechnexus](https://t.me/blindtechnexus) for update broadcasts and the rest, and blindtechvisionary@gmail.com for an answer that can be relied on, with the address and a subject already filled in. Each asks by link first and then by the app's own scheme, and a link that nothing on the device can follow says so out loud rather than doing nothing at all. WhatsApp is asked three ways rather than two: `wa.me` is the address WhatsApp hands out, `api.whatsapp.com/send?phone=` is what `wa.me` redirects to and is the one that works on a browser that will not follow the redirect, and `whatsapp://send?phone=` is for a device with the app and no browser. The address this used to name, `w.me`, is the short form of a signed-in desktop session and answers a phone with a page saying it cannot be loaded there, which is what the button labelled "Contact on WhatsApp" arrived at. The `<queries>` block in the manifest names the messaging apps for the same reason: Android 11 and later answer "nothing is installed" to an app that has not said what it is looking for, and a scheme that cannot be resolved is a button that does nothing.

### Battery optimization

The exemption from battery optimization is required rather than wanted. A text-to-speech engine is idle almost all the time and has to answer at once when it is not, which is the shape every battery policy is written to kill: Doze takes the alarms away from anything not exempt, and the manufacturer policies stacked on top of it are stricter than Doze and do not say what they did. Refused, the voice stops in the middle of a sentence, or does not start at all once the phone has been still for a while, and nothing inside the app can see that happening in order to explain it.

So the app asks on every launch: whatever has not been given is asked for in `onCreate`, the notification permission first and then the exemption, and the welcome screen does the same asking on the way past it. The reason is said in words of the app's own before handing over to a system screen that is a yes or no with nothing said about it. The asking happens once per launch rather than on every resume, because a dialog that comes back every time the app is resumed is a dialog that gets refused every time the app is resumed. The answer is read again on every resume, since the screens that grant it are not ours. Two of them are tried: the one that asks about this app by name, and the list of every app for a build whose manufacturer took the first one out.

### Keeping the voice alive when nothing is speaking

The notification is what holds the engine up, and for most of the life of a phone it is holding it up while nothing is bound to the engine at all. That is the reason it is not posted by the engine: a text-to-speech service belongs to the system, which starts it when a reader binds and destroys it when the last reader lets go, so a notification posted by the engine is a notification that goes away precisely when it is wanted. `KeepAliveService` exists for that one job. It is a foreground service of the special-use type, started when the switch is turned on, started again by `EngineReceiver` on `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` when "Start again after a restart" is on, and stopped when the switch is turned off.

One switch decides all of it. On, the notification appears and stays, and the engine is held in the foreground whether anything is speaking or not. Off, nothing is posted -- and that is the whole of the promise, because nothing else in this app posts a notification. It used to be otherwise: the engine claimed the foreground for the length of every utterance the switch was off for, so a person who had turned the notification off met it again several times a minute. That claim is gone. It was defended as protection and it was not, since an utterance is a second long and the framework has already bound the service for it; what it bought was a notification somebody had refused.

Nothing polls either. The notification was looked for every few seconds and put back when anything had taken it away, which cost a check every three seconds on a phone that is mostly idle -- exactly the cost this screen exists to explain -- to defend against a notification that a foreground service does not let be swiped away. `KeepAlive.CHECK_MS` is nought now and says so. The notification is posted once, when the switch moves and when the service starts; a redelivery after the system took the process away is answered by reading the setting again and claiming again, which is what a sticky service is for.

The notification carries the app's only control that works while the engine is speaking and the screen is off. Its button sends a broadcast naming this app's own receiver, which asks the running service to be quiet: nothing outside a speech service can reach the audio a service has already handed to Android. It is off by default and it is a switch on this screen, because a button on a notification that does something the person using the phone did not ask for is a worse default than no button. Tapping the notification itself opens the battery screen rather than the launcher, which is where the answer to "why did my voice stop" lives.

### The switch on the battery screen

One switch, and the paragraph under it: whether the app is running in the foreground even while not in use. It starts off. On, `KeepAliveService` holds the notification and the engine is protected from the battery policies that would otherwise stop it while the phone is idle -- which is the failure this screen is about, and the reason the exemption from battery optimization above it matters too. Off, no notification is posted by anything in this app, and the voice is there whenever something asks for it but not held up between times. The screen says which of the two is in force, in the terms of what the phone is doing rather than in the name of the switch, because the question it is opened to answer is not what was set but whether it is working.

Everything the screen used to offer about speaking itself -- speaking in the foreground, the wake lock and how long it is held, how far ahead of the speech the samples may run, running further ahead on long text, giving the engine back when idle, giving up after the engine goes silent, and how often the notification was looked for -- is fixed in `Stability` and `Pacing` instead. None of those has a second good answer: speech that stops the moment the screen goes dark is the failure the foreground claim and the wake lock are for, a lead above a few hundred milliseconds is a queue of unheard speech the next swipe throws away (measured: 2229 ms of audio handed over in 700 ms of speaking without the limit, about 1000 ms with it), an engine given back has to be made again -- a second or so -- before the next thing is said, and a stuck engine should be noticed in three seconds rather than fifteen. The old properties are still in `Settings` so that preferences written by an earlier build keep parsing, but nothing reads them.

## Settings

Launcher icon, or the gear beside the engine in the text-to-speech settings. The nine sections above, top to bottom: the voice and its gender, the seven numeric parameters each behind a button that opens it in a dialog of its own, the sample rate, the dictionary manager and the abbreviation switch that belongs with it, pauses, symbol pronunciation, phrase prediction and putting a voice back as it shipped, and a sample to hear the lot of them on. Nothing speaks on its own: a setting takes effect when you set it, and "Speak a sample" is how you hear it. "Stop the sample" is there as well, because the preview plays into an AudioTrack of its own and nothing else stops it.

A setting that is a choice is a button that carries its own value -- "Voice (Reed) selected" -- and the state description says "Click to change", so a screen reader hears the setting, its value and the action in one stop. The seven numeric parameters are buttons too ("Set voice rate" and its like) that open a dialog with the setting's name for a title, the bar, and Save and Cancel. Nothing is changed until Save, so a look inside a setting costs nothing.

The bar in the dialog is one control. A label, a bar and a number would be three stops for a screen reader, so the dialog merges them into a single node with `clearAndSetSemantics`. It reads as "Voice rate, 20 percent" and adjusts in place.

What a setting does is written above it and not inside it. Inflection, head size and roughness are not words that carry their own meaning, so each has a sentence of its own; folded into the control's announcement it would be heard again on every visit, with no way past it.

Values are percentages. The engine uses three scales (gender is 0 or 1, speed runs to 250, the rest to 100), so a raw number means nothing without knowing which. The setting called rate is the engine's own speed parameter: rate is the word Android and every screen reader use for it, and the number shown beside it is a percentage of 250 as the rest are of 100.

The dialog takes the keyboard and the bar inside it does not. A Compose slider keeps a focus of its own, which no screen reader can see once the semantics belong to the dialog's control, and it answers the End key by going to the maximum. So the bar is out of the tab order and the dialog answers the keys itself.

An arrow moves one percent. TalkBack adjusts any slider by a twentieth of the range it is given, and the number of steps a Compose slider declares never reaches the platform, so TalkBack's own arrow asks for five. Five percent of the speed scale is the difference between too slow to bear and faster than was wanted, so the control reads a request to move as a request to move one.

### Forcing rate, pitch and volume over other apps

Next to the rate, pitch and volume buttons is a switch: "Force over other apps". Checked, the rate, pitch or volume set here is what is spoken, and what the app asking for speech asked for is ignored -- TalkBack's rate no longer scales the engine's speed, so the voice is always at the pace set here. Unchecked, a client's asking is honoured as it always was: TalkBack at 150 percent speaks half again as fast.

Volume is the odd one, and honestly so: Android hands no per-utterance volume to an engine, applying a client's volume to the audio stream after the speech has left the service. Forcing volume re-asserts this app's own engine volume on every utterance, which keeps the number here in force whatever else might have moved it, but no switch inside a TTS engine can reach a stream volume.

Speech rate from the system is a multiplier, so it is treated as one. The engine's speed scale is far from linear: speed 100 speaks 2.7 times the default rate and speed 200 speaks 16.6 times. A measured curve converts between the two rather than multiplying, so 200 percent is about twice as fast and very high rates saturate at the engine's ceiling.

Voice settings belong to the voice. Set Reed's pitch to 55, go to Glen and come back, and Reed is still at 55. Only "Reset this voice to its defaults" forgets it, and only for the voice you are on.

Speed is the listener's, not the voice's. Six presets ship at engine speed 50 and Glen and Sandy ship at 70, half again as fast, so a preset brings its other seven settings and leaves the speed alone.

Dialogs start at the voice's own values. Each preset carries its own head size, inflection and volume: Reed is inflection 30 and volume 92, Bobby is 35 and 90. A parameter you have not set is never sent to the engine, so a fresh install sounds like Eloquence always has.

## Pauses

Eloquence pauses generously, which reads well in prose and badly in a screen reader where most utterances are a few words. The engine has no setting for it but it takes an annotation: `` `p `` and a number is a pause of that many milliseconds, and one placed in front of a punctuation mark replaces the pause that mark would have had.

Three choices. Do not shorten leaves the engine alone. Shorten at end of text trims only the gap after the last thing said. Shorten all pauses trims punctuation as well, which is the default and takes a sentence with commas to 65 percent of its original time.

The rule is davidacm's, from the [NVDA IBMTTS driver](https://github.com/davidacm/NVDA-IBMTTS-Driver), which calls these JAWS-like pauses. A mark counts when it follows a letter, digit or space and is followed by whitespace, a slash or the end, which leaves the point in 3.14, the colon in 2:30 and the comma in 1,024 alone.

## Symbol pronunciation

The engine has no parameter for this, and neither does anything else in the ECI interface: the published eighteen settings are voices, rates and code sets, and that is all. What the engine does have is a character-name table built into its language data, and that was measured rather than assumed, by building the engine and speaking one string at a time:

* A symbol standing on its own is always named. "?" alone is "question mark", byte for byte the same audio as the words; "*" is "asterisk", "," is "comma", "~" is "tilda" -- the engine's own spellings, kept here so a symbol sounds the same whether the engine names it or we do.
* A symbol attached to a word is silent when it is sentence punctuation or a bracket -- "(hello)" costs exactly what "hello" costs -- and read in place when it carries meaning: "$5" is "five dollars", "50%" is "fifty percent", "a=b" is "a equals b". The backslash is one of these too: "a\\b" is "a backslash b" and a Windows path is spelled out, which is why none has to take it away.
* The backtick is the annotation escape and is never touched, and the hyphen standing alone is the one symbol the engine has no name for at all.

So the five settings are made out of text changes on the way in, which is also how Speech Dispatcher's own module for this engine does it. *Some* is the engine left exactly as it stands, and is the default, so nobody's speech changes when this arrives. *Most* adds the names of attached brackets, quotes, question marks and the like, which the engine leaves silent. *All* adds attached periods and commas. *None* names nothing: every symbol is replaced with silence except the marks the engine needs for its pauses and the apostrophe inside a word. *Custom* is none plus a list of the user's own, entered as the symbols themselves.

Three things are deliberately never replaced at any level that speaks them: the symbols the engine already reads in place -- the dollar, the percent, the equals and the rest -- because writing " dollar" in place of "$" trades the engine's "five dollars" for "five dollar"; a mark between two digits, because 3.14 and 2:30 and 1,024 are number readings rather than symbols; and the apostrophe inside a word, because "don't" is a contraction and not a symbol.

## Voice, phrase prediction, abbreviations

The voice is whichever the settings screen says. A caller that names a voice does not override it, because a client holds on to the Voice object it was given: a screen reader that connected while Reed was selected goes on asking for Reed however many times the setting changes underneath it. So the name is not even asked -- and neither is the locale, since there is one language and one instance that is never closed for another. Whatever the caller asked for, in whatever language or under whatever name a TTS router forwarded, is spoken in the voice, which is what keeps a router's request from ever being answered with silence.

Phrase prediction is the engine guessing where the phrase boundaries in a sentence are and shaping the intonation to match. That reads well in prose and gets in the way in a screen reader, where a line is usually a fragment and the guess is wrong, so it is off. It is an annotation rather than a parameter, so it goes out with every utterance.

Expand abbreviations is `eciDictionary`, which is inverted in the engine: zero turns it on. Off by default, and re-sent with every utterance, since it is the one setting with nothing to say whether something else has moved it.

## Dictionaries

The dictionary manager is a list of the user's own words and what to say instead. Add, edit, remove, import and export; a word added is heard the next time anything is spoken, including on "Speak a sample". Matching does not care about capital letters: `doj` and `DOJ` are the same word, because IBM's own dictionary is case-sensitive and a screen reader hands over whatever case the control had, which is why a word added in the manager used to vanish in speech. A file brought in from elsewhere is kept beside the list under its own name instead of being poured into it, and tapping it opens every word it holds with the same options; exports are `.dict` files, and imports are streaming, which is what makes files of lakhs of words -- and the worst of them millions -- an import rather than a crash.

The files people share are text: a word, a tab, then either a respelling (`dee oe jeigh`) or a pronunciation in the engine's own alphabet (`` `[Ekspoz1e] ``). [mohamed00/altibmttsdictionaries](https://github.com/mohamed00/altibmttsdictionaries) has the English pair. An import is the same reading.

The replacements run over the text on the way in, as whole words and as phrases, rather than being taught to the engine a word at a time. Teaching three thousand entries cost about 450 ms and still missed `DOJ` when the file said `doj`. A rewrite is one walk of the utterance, answers the original string when nothing in it is a dictionary word, and the phonetic alphabet still works because the engine's annotations are on.

No dictionary is shipped here. See LICENSE.

## Interrupting

A screen reader wants each string to cut off the one before it. Two things make that work.

The engine cannot abandon an utterance, so long text is handed over in sentence-sized pieces and asking for silence waits out a piece rather than a whole paragraph. The rule for where a piece may end is openevv's, ported from its NVDA driver: a sentence end is free because the engine already pauses there, anywhere else costs about 0.4s, so a full stop has to prove itself. A word with a dot inside it is an abbreviation, and a short capitalised word is an initial.

Synthesis is paced to real time. The engine produces 1.3s of audio in 12 ms and Android queues every byte of it, so a reader swiping quickly would leave five or six items queued while the first is still playing and lose all of them when it asks for silence. Output runs no more than 300 ms ahead. Measured on the same text: 2229 ms of audio handed over in 700 ms of speaking without that limit, about 1000 ms with it.

A request for silence also drops the text being prepared. That is the half of an interruption the engine cannot be told about: a swipe that arrives while a thousand characters are being rewritten would otherwise leave that work running to the end of the text behind the next utterance, which is the wait it was supposed to remove.

A request for silence wakes the wait rather than sitting out the rest of a sleep. Every utterance runs on the framework's one synthesis thread, so whatever the current utterance waits on delays the next element's first syllable; a swipe that landed mid-slice used to wait out the rest of it, and now it does not.

A stop that lands as a piece is being handed over either refuses the piece or aborts it cleanly, never half of each: `speak` asks whether the utterance was stopped under the same lock `stop` takes, so the stop cannot slip between the caller's check and the engine's queueing. Without that fence the slip queues an utterance nobody reads while the next one waits behind it, and that wait is exactly what a fast swipe used to feel like. And the wait for the previous utterance to leave the engine is bounded -- three seconds, where reaching it means the engine is wedged rather than busy -- so the synthesis thread is never held for the rest of the day; the instance is given back instead, and the next utterance opens a fresh one.

## Updates

The app asks one URL when it starts: [the release channel](https://raw.githubusercontent.com/blind-tech-nexus/remote-data-json/main/alt-eloquence-tts-update.json) -- which keeps the file name it has always had, because a channel that moves is a channel every installed build loses -- a file with two values in it, `version` and `url`. The version is compared with this build's `versionName` and, if they are not the same, a dialog says so in the words "A new version (%s) is available to download" and asks whether to fetch it now. "Later" is not a setting: the same version is not offered again for the rest of that run of the app and is asked about again the next time it opens. `UpdateTest` holds the rules of the answer: that the same version is no update, that a missing or blank value is no update, and that an address that is not https is no update -- the download is an executable, so a plain http address is refused rather than warned about, since a person halfway through a download is not in a position to judge a certificate.

Nothing is installed by the app. When the bytes are down the file is validated as a package by the package manager, and the installer is asked with a one-read grant on a content URI into the cache. On Android 8 and later that needs this app to have been allowed to install apps at all, which is not the app's to grant: the allowance is asked for on the system's screen, and the file waits in the cache until it has been given. The dialog while bytes are arriving says how many have arrived, in megabytes, and has a button that breaks the read off and deletes the partial file; the bar under the number is for whoever can see it, and nothing that has to be heard depends on it.

The asking is one `HttpURLConnection` and one `JSONObject`, on a thread of its own, with no library. The About screen says what it found whenever it is asked from there, which is the only case in which an answer of "you are up to date" or "nothing could be reached" is worth a sentence; on the way into a launch, silence is the right answer to both.

## Language

Two: English (United States), engine word `0x00010000`, module `lang/enus`; and English (United Kingdom), engine word `0x00010001`, module `lang/engb`. Both are named in `app/build.gradle.kts` and in `native/build-native.sh`, both are compiled into the app, and both are offered -- on the settings screen, in the voices the service answers with (`eng-USA-Reed`, `eng-GBR-Reed` and the rest), and in what a caller can ask for by locale. The pair differ in one bit because that is how IBM wrote the family: the top half is the language, the third byte the code set, the bottom one the dialect.

Which of the two speaks is decided per utterance. A caller that names one -- a screen reader told to read a British page, an app that asked for `en-GB` -- is answered in it, and a caller that names neither, or names something neither instance speaks, is answered in whichever the settings screen chose, which is American English until somebody says otherwise. Neither language is closed to make room for the other: each has an instance of its own, kept for ever, because the alternative is a second of silence -- the cost of opening one -- every time a reader alternates between an American page and a British one.

What the engine is asked about a language is answered honestly for the two and generously for the rest. Both Englishes in any of their spellings are country-available, and their voices carry real `en-US` and `en-GB` locales, because a voice built on the ISO-3 pair never equals the `Locale.ENGLISH` a router holds. Anything else -- another language, nothing at all -- is available too, because the engine speaks every utterance it is given in one of the two voices rather than refusing it. That last answer is what keeps a TTS router such as Auto TTS routing: it probes with the device's locale, and an engine that answers "not supported" to the probe is an engine it never asks to speak. Any voice name is accepted for the same reason, and a name that carries its language in it -- `eng-GBR-Glen` -- is answered with that language's instance.

## Audio optimization

Above the sample rate on the quality section: how the samples are finished before they are heard. One setting, three modes, each a fixed chain rather than knobs. Balanced is the default -- the voice as recorded, quietly levelled. Optimized for volume is the loudest the voice gets, for a noisy street. Optimized for quality is the cleanest sound it has.

Two halves read the same mode. The engine's half is the upsampling method: held samples for volume, which keeps the band loud; true sinc resampling for quality; cubic between the two for balanced. The front end's half runs on the PCM on its way to the framework: a one-pole direct-current blocker, an adaptive gain that rides the level towards the mode's target -- attacking at once, releasing slowly, so a shout stays inside the ceiling and the floor does not pump -- and a soft limiter built on the hyperbolic tangent, which passes small signals and rounds the top into the ceiling instead of clipping it.

Higher rates are honest in quality mode and loud everywhere else, if not in the way the numbers suggest. The engine synthesises at eleven thousand and twenty five hertz whatever the setting says, so the rate only decides what the upsampling carries into the new band: silence in quality mode, a curve through the speech in balanced, a mirror of it in volume. The framework is told the rate the engine actually took -- a refused rate is never adopted -- so what is heard is always at the speed the label says. And the louder modes are audibly louder for real reasons: volume's held upsampling brightens the top band, and any limiter rounding a peak adds harmonics of it. That is the price of loud, and it is priced on purpose.

## Abbreviations and expansions

The engine has an abbreviation dictionary of its own, and the switch for it is on the dictionary section where it has always been. This is the other one, and it is yours.

Every entry has four parts, and each is a choice rather than a convention:

* **The key** -- what is written. A single word or a phrase; `st`, `Dr`, `new york`.
* **What to say instead of it.**
* **How it is matched.** *Whole word* is the default and is what `st` wants, so that a street is expanded and the first three letters of "street" are not. *From the start of a word* is what `Dr` wants. *Anywhere* is what a placeholder in a template wants, and it is the only one of the three that will match inside a longer word, which is why it is not the default.
* **Whether case is kept.** Off by default, so `DOJ`, `Doj` and `doj` are all the same entry. Turned on, only the spelling that was written matches -- which is the setting that makes a case-sensitive identifier readable without making ordinary prose case-sensitive too.

The list is kept in `abbreviations.dic` beside the user dictionary, as four tab-separated columns, and it imports and exports in that same shape, so a list can be shared between two phones or kept in version control. An entry with the same key and the same matching replaces the one that was there rather than standing next to it.

Matching is one pass over the text against a trie of the keys, and the longest key that matches at a position wins -- `str` as "string" rather than `st` as "street" followed by a stray "r". That is one pass however many entries there are, which is what makes a list of a few hundred cost nothing.

## Reading source code

A synthesiser that reads source code as prose says `getElementById` as one word, `!=` as "bang equals" if it is lucky, and a brace as a silence. Seven switches, under one that turns the whole of it on and is off by default:

* **camelCase** -- `getElementById` as "get element by id". A run of capitals is one word, so `parseHTTPResponse` is "parse http response" and not "parse h t t p response"; and `HTTP` on its own is a word and is not taken apart at all, because taking it apart would spell it.
* **snake_case** -- `my_total_count` as "my total count".
* **Operators** -- named, and met as the whole of themselves: `!==` is "bang double equals" and not "bang" followed by "double equals". Longest first, which is the whole of the rule.
* **Brackets and marks** -- braces, brackets, parentheses, the semicolon, the colon, the comma, the dot, `@`, `#`, `$` and the backtick, each with a name.
* **Indentation** -- counted rather than spoken: a line that opens with whitespace says "indent 4" and then its own text. A tab is one and a space is one, because that is a count of the characters and not a guess at the width of somebody's editor.
* **File paths** -- `/home/user/notes.txt` as "path home slash user slash notes dot txt". Two shapes are taken and both are narrow: one that starts where a path has to -- a slash, a tilde, a dot slash, a drive letter -- and one that is relative and carries a file name, so `src/main/App.kt` is a path and `and/or` is not. Saying "slash" in the middle of a sentence because two words happened to be joined would be worse than the silence.
* **Addresses** -- `https://www.example.com/a/b` as "url example dot com slash a slash b". The scheme is a name and the dots in a host are not sentence ends, and those are the two things the engine gets wrong about an address.

They run in that order, paths and addresses before the operators, because an address is one thing that the operator pass would otherwise take apart mark by mark. What a pass that puts spaces around the things it names owes the text back is put back rather than trimmed: the indentation pass runs last and reads the whitespace at the start of a line, so a leading space that was in the text has to still be there when it does.

## Web pages and styled text

A page read by a browser arrives at a speech engine already flattened to text, and the flattening keeps the meaning in spans: a link is a `URLSpan`, a heading is a `StyleSpan` with a `RelativeSizeSpan` beside it, a list item is a `BulletSpan`, a picture is an `ImageSpan`. Read without them, all of that is a wall of words in which a link is indistinguishable from the sentence around it and a picture is nothing at all. With **Read text styles** on -- it is on by default -- every styled stretch is announced as the thing it is, twice, around the words it covers: "link Shop end link", "bold hello end bold", "larger text Contents end larger text".

What is announced, and the whole of it: bold, italic and bold italic; underline, strikethrough, superscript and subscript; links, from `URLSpan` or from any other `ClickableSpan`, because the two are the same thing to a listener and a page handling its own links carries the second kind; images, with whatever the page said they show, and without the placeholder character they stand on; monospace and other named fonts; larger and smaller text; the colour of coloured text by name; highlighted text, with the colour of the highlight; bullets; quotes; and centred and right-aligned text. What is deliberately not announced: black and white foreground colours, which are the default ink on one screen or another and would be noise on every stretch of every app that sets its colours, transparent colours, which colour nothing, and layout spans that say nothing about a word.

A page also carries characters that are not words. A zero-width space wherever the markup allowed a line break, a soft hyphen wherever a word could be split, a byte-order mark at the front, an object replacement character where an image was, and the several spaces that are not the space bar: a voice given those says a pause, or nothing, or the word "space", any of which is a wrong word in the middle of a sentence. They are taken out in one scan before anything else reads the text, and the scan only runs when one of them is there at all -- which is never for text a person typed and often for text a page was.

Both halves are bounded and both fail open. A span that misbehaves, a collection that moves under the reader, a colour that will not answer: the annotation stops at five hundred spans, the walk catches everything, and a style that cannot be read is worth the words without it and never worth losing the utterance over. `TextStylesTest` and `TextFixesTest` hold the promises, one per style and one per character.

## A long text is streamed

Long text is handed to the engine in pieces, because the engine cannot abandon an utterance and a cancel has to wait for something. What it used to do first was cut the text into every piece it had and put every one of them through the pipeline, and only then ask for the first syllable. A chapter is a few thousand pieces, so the wait before the first sound grew with the length of what was being read -- and a person who swiped away two seconds later had paid for the whole chapter and heard one sentence.

The pieces are a sequence now, and a piece is rewritten when the utterance reaches it. The first syllable is one piece of work behind the request rather than one document behind it, and a piece is a couple of hundred characters whatever the document it came out of is. `StreamingPipelineTest` holds both halves of the claim: that the pieces are the same pieces in the same order saying the same thing, and that asking for the first two of them costs two of them rather than all of them.

Below ten thousand characters -- a couple of pages -- the single pass is still taken. It is cheaper there than the per-piece calls, and it cuts the rewritten text rather than the caller's, which is the better of the two: the pipeline can move a character, and a cut made before it runs is a cut made against text that is about to change.

The pieces are prepared one ahead of the speech, on a thread of its own (`evv-text`), rather than on the framework's synthesis thread between two pieces. There are three properties and each is a delay that is not paid. The next piece is ready while the piece before it is still being spoken, so the engine is never idle waiting for text that is ready a millisecond later than it is needed. One piece at a time and no further, so a chapter is not cut and rewritten in full behind somebody who is about to swipe away its first sentence. And a stop cancels the work where it stands -- a flag, an interrupt, and the prepared text dropped -- so a stopped utterance leaves no thread grinding through the rest of the document that the next utterance would have been waiting behind. Interrupting and synthesising are two threads doing their own work at the same time, and the only thing that orders them is the flag they both watch.

## The user dictionary

Importing a dictionary used to be the way to kill this app. The matcher was a trie with a hash map at every node -- one object per character of every word, all of them alive at once -- and the index was built on the way to the first syllable of the next utterance. A file of a few hundred thousand entries is a few million such objects, and a phone asked to hold them while it also held the previous copy ran out of memory on the framework's synthesis thread: the voice stopped, and for the rest of the session the engine answered nothing at all. The caps that were meant to prevent it (two hundred thousand entries, fifty megabytes) turned the crash into a refusal instead, which is a different way of being told no.

What replaces it works the other way round. The text decides what is looked at: the utterance is walked word by word, and for each word one hash probe answers whether any entry begins with it. A dictionary of ten entries and a dictionary of a million cost an utterance the same, because neither is walked. The dictionary itself is three flat arrays and one `CharArray` -- every key and every replacement back to back, with offsets beside them -- rather than a hundred thousand strings and their arrays, and its size is decided before it is built rather than discovered by running out of memory.

The bound is a budget in characters, not a count of entries, because characters are what runs a phone out of memory: a sixty-fourth of the process's heap, between a quarter of a megabyte and eight, which is around two hundred thousand ordinary entries. An import reads the file once, in order, a line at a time, and stops reading when the budget is full; the entries that did not fit are counted, and the dictionary manager says how many are in force and how many were left out rather than leaving somebody to wonder why a word they imported is not being said. A file of a billion words is the same import as a file of a thousand: bounded time, bounded memory, and an honest number at the end.

The file rides a version. `words.dic.ver` is written beside the main list, every write moves the number on, and a write hands the rebuild to a thread of its own -- one of them, daemon, named `evv-dictionary` -- so the utterance that follows compares one integer against the one its index was built from. A match is the index it already has, which is the ordinary case by a wide margin, because a write happens once and is spoken from a thousand times. A mismatch waits on a rebuild that was started before the utterance was, and is usually finished already.

The version is what makes that safe rather than merely fast. Without it, a reader on the synthesis thread and a writer on another are racing over one file and the reader has no way to know it lost. With it, the index and the number it was built from are published together under one lock, so a reader sees a pair that goes together or an older pair, and never an index from one list against the version of another. The imported files ride the same snapshot -- their names, sizes and ages beside the version, so a file added, removed or rewritten rebuilds on the next utterance -- and they are read into the same budget, in name order, so the main list is what is kept when the two cannot both fit.

Writes are streaming too. An import into a file that already exists is copied line by line into a file of its own, appended to, and put in place in one move, so a cancelled import or one that fills the storage leaves the dictionary it was adding to exactly as it was rather than with half a line at the end of it. The bytes written are counted as they are written -- both halves of the copy, which is what makes the free-space check mean something -- and the whole of it is bounded by the free space on the phone as well as by the budget, because filling the phone would take the settings and the dictionaries with it.

## Speed

The engine is not slow. A host build of the same C the app ships, on the same single thread the app uses, said 4003 characters of English to 8.5 megabytes of audio -- 386 seconds of speech -- in 0.74 seconds, 520 times real time. So every part of "the engine is slow" that was real was somewhere else, and the four that were found are worth writing down because none of them is where a person reading the code would look first.

**The engine's output buffer was too small.** Samples leave this engine one buffer at a time, and the buffer's size is asked of `eciSetOutputBuffer` in samples: the engine fills it, hands it over on the callback, and only then makes the next. At 1024 samples -- two kilobytes -- a long paragraph is four thousand callbacks, each one a lock, a copy into a malloc, a `SetByteArrayRegion` and a wake-up on the other side, and the same again in reverse for the read. The experiment that set the shape went from 1024 to 4096 samples: measured on the same text, the whole utterance went from 0.961 s to 0.742 s and the reads from 4156 to 1039, and 16384 was faster again but the first byte of audio arrived 2 milliseconds later. The frame is now 2048 -- half the wait for the first buffer to fill and, since the abort is only noticed when the engine offers the next buffer, half the wait for a stop to be answered -- at twice the callbacks of 4096, which is still dozens per utterance rather than thousands. The latency cost is one buffer's worth of synthesis: under a fifth of a second of speech at eleven thousand and twenty five hertz, against over a third at 4096.

**A piece boundary was a full stop.** Long text is handed over in pieces, because the engine cannot abandon an utterance and a cancel has to wait for something. Where the text has a sentence end, the piece goes there and it is free -- measured exactly: 482,449 samples whole, the same 482,449 in six pieces. Where it has none within reach, the cut lands on whitespace and the engine puts the pause of a full stop there anyway: 0.39 seconds, measured, every boundary. Text with no full stops in it is the ordinary case for a chapter of a book or a page of a table, and thirty seconds of silence in ten minutes of speech was what "slow" meant. `TextPieces` now marks a cut it had to make as forced and the service ends such a piece with the engine's own `` `p1 `` annotation, which measured 0.15 s for two boundaries instead of 0.78: the pause is not prosody, it is an artefact of where a splitter happened to cut, and it can be taken back out.

**The engine's own log was on.** `g_iTTSLogLevel` in openevv's port layer starts at 9, and at 4 and above the engine's internals write a line to stderr for a mutex that did not answer the way they expected. That path is not taken on the shipping arrangement, where one thread drives the engine and one reads it, so nothing was changed here -- but it is why a harness that runs the engine differently looks twenty per cent slower than the app is, and it is worth knowing before the next measurement of this kind is believed.

**Everything before the first syllable was done on every utterance.** The text used to be copied through a chain of five passes that each built new strings, the pronunciation dictionary was re-taught per utterance, and the settings were compared field by field. Now the emoji, symbol, fix-up, dictionary and code-set passes each make one scan of the characters and each answer with the original string when they have nothing to change; `WesternText` keeps a table that says whether a character needs work at all, so ordinary English is a copy rather than a lookup per character; a dictionary is reread when its revision has moved and not otherwise; and the settings carry a revision that goes up on any write, so an unchanged configuration is one integer comparison. What the reader waits for is the first frame of audio, which is 12 ms of this engine's time on a desktop build and is the only number in the whole utterance that cannot be shared out over the rest of it.

The engine is also kept between utterances rather than made and thrown away each time -- it is never given back, because making a fresh one costs a second or so before the next first syllable; and one read takes everything the engine has written rather than everything a caller happened to ask for, so a 2048-sample frame crosses the boundary between the two threads in one go whatever the size of the buffer the framework offered.

Two more that were found while the "while speaking" section was taken out, because slowness between elements is interruption latency rather than synthesis speed and every utterance runs on the framework's one synthesis thread. **The waits a stop has to land in were slept out.** A swipe that arrived mid-slice waited out the rest of it before the next element could start. The wait is woken the moment silence is asked for, so the next first syllable is delayed only by however long the engine takes to drop what it was making. **Long text ran four seconds ahead of the speech.** That queued seconds of audio nobody had heard yet and the next swipe threw all of it away, which is the failure the lead exists to prevent -- so every message is paced to the same three hundred milliseconds now, long or short.

## Wear OS

Runs on a watch. Tested on a Pixel Watch 4, which is armeabi-v7a.

A Pixel Watch has no USB, so adb is wireless. On the watch: Settings, System, About, Versions, tap Build number seven times. Then Developer options, ADB debugging on, Wireless debugging on, Pair new device. Watch and computer on the same Wi-Fi, with the watch's Wi-Fi actually on rather than dropped for Bluetooth.

```
adb mdns services                 # the watch announces both ports
adb pair 192.168.x.x:PAIRPORT     # then the six digits on screen
adb connect 192.168.x.x:PORT
adb -s SERIAL shell getprop ro.product.cpu.abilist
./gradlew installDebug -Pevvdroid.abis=armeabi-v7a
```

## Layout

```
native/openevv          the engine, a submodule, untouched
native/patches          the one runtime hookup the front end needs
native/android.mk       two make targets openevv has no reason to carry
native/build-native.sh  cross-compiles the engine, links libevvjni.so
native/tools            drives openevv's own test gate over adb
app/src/main/cpp        JNI bridge
app/src/main/kotlin     the service, engine wrapper, settings
```

The engine is built by its own Makefile rather than CMake, because that Makefile generates the language rules with Python first. Gradle runs the script and picks the result up as prebuilt `jniLibs`.

Everything links into one shared object, `libevvjni.so`. Android only packages files named `lib*.so`, and openevv's own library records a soname of `libeci.so.1`, which nothing would then find.

`native/tools/probe-on-device.sh` stands in for openevv's own probe, so its gate, `test/matrix.sh check enus`, drives an Android build over adb unchanged. That is 98 cases comparing sample hashes and reported answers against baselines recorded on x86.

## Licence

MIT for the Kotlin, the C bridge, the build and the tests. See LICENSE.

Not the engine. openevv is MIT under its own copyright and is fetched from upstream, not copied here.

Not the language data openevv carries. That is transcribed from IBM's Embedded ViaVoice objects and is IBM's. `native/openevv/NOTICE` says what it is and whose it is.

No pronunciation dictionary is included. Those belong to whoever wrote them, and the best known collection carries no licence at all, so it is linked rather than copied. The test fixture here is written for the purpose.
