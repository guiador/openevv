package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun ExperimentalResponseSection(state: SettingsModel) {
    val c = state.responseExperiment
    val context = LocalContext.current
    SectionHeading(text = stringResource(R.string.page_experimental_title))
    SectionText(text = stringResource(R.string.exp_intro))
    ExperimentalToggle(stringResource(R.string.exp_enable), c.enabled) { state.chooseResponseExperiment(c.copy(enabled = it)) }
    val profiles = listOf(R.string.exp_reference, R.string.exp_balanced, R.string.exp_fast, R.string.exp_extreme).map { stringResource(it) }
    val descriptions = listOf(R.string.exp_reference_note, R.string.exp_balanced_note, R.string.exp_fast_note, R.string.exp_extreme_note).map { stringResource(it) }
    val selected = ResponseExperiment.PROFILES.indexOf(c.copy(enabled = true))
    val custom = stringResource(R.string.exp_custom)
    ChoiceButton(stringResource(R.string.exp_profile), stringResource(R.string.exp_profile) + ": " + profiles.getOrElse(selected) { custom },
        profiles.mapIndexed { i, name -> "$name. ${descriptions[i]}" }, selected,
        { state.chooseResponseExperiment(ResponseExperiment.PROFILES[it].copy(enabled = c.enabled)) }, stringResource(R.string.exp_profile_note))
    OutlinedButton(onClick = { state.chooseResponseExperiment(ResponseExperiment()) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.exp_reset))
    }

    SectionHeading(text = stringResource(R.string.exp_text_heading))
    ExperimentalNumber(R.string.exp_piece, c.pieceChars, 32..1000, R.string.exp_chars, R.string.exp_piece_note) { state.chooseResponseExperiment(c.copy(pieceChars = it)) }
    ChoiceButton(stringResource(R.string.exp_boundary), stringResource(R.string.exp_boundary) + ": " + stringResource(if (c.hardCut) R.string.exp_inside_word else R.string.exp_whole_word),
        listOf(stringResource(R.string.exp_whole_word), stringResource(R.string.exp_inside_word)), if (c.hardCut) 1 else 0,
        { state.chooseResponseExperiment(c.copy(hardCut = it == 1)) }, stringResource(R.string.exp_boundary_note))
    ExperimentalChoice(R.string.exp_stream, listOf(0, 250, 1000, 5000, 10000), c.streamFromChars, R.string.exp_chars, R.string.exp_stream_note) { state.chooseResponseExperiment(c.copy(streamFromChars = it)) }
    ExperimentalChoice(R.string.exp_prefetch, (1..4).toList(), c.preparedPieces, R.string.exp_pieces_unit, R.string.exp_prefetch_note) { state.chooseResponseExperiment(c.copy(preparedPieces = it)) }
    ExperimentalChoice(R.string.exp_workers, (1..3).toList(), c.textWorkers, R.string.exp_threads, R.string.exp_workers_note) { state.chooseResponseExperiment(c.copy(textWorkers = it)) }

    SectionHeading(text = stringResource(R.string.exp_audio_heading))
    ExperimentalNumber(R.string.exp_lead, c.leadMs, 20..500, R.string.exp_ms, R.string.exp_lead_note) { state.chooseResponseExperiment(c.copy(leadMs = it)) }
    val rate = state.sampleRateHz.coerceAtLeast(1)
    val frameOptions = ResponseExperiment.FRAMES.map { n -> context.getString(R.string.exp_frame_estimate, n, n * 1000.0 / rate, rate.toDouble() / n) + ". " + context.getString(if (n <= 512) R.string.exp_block_small else R.string.exp_block_large) }
    ChoiceButton(stringResource(R.string.exp_frame), stringResource(R.string.exp_frame) + ": " + frameOptions[ResponseExperiment.FRAMES.indexOf(c.frameSamples)], frameOptions,
        ResponseExperiment.FRAMES.indexOf(c.frameSamples), { state.chooseResponseExperiment(c.copy(frameSamples = ResponseExperiment.FRAMES[it])) }, stringResource(R.string.exp_frame_note))
    val reads = ResponseExperiment.READ_SAMPLES.map { n -> if (n == 0) context.getString(R.string.exp_read_auto) else context.getString(R.string.exp_frame_estimate, n, n * 1000.0 / rate, rate.toDouble() / n) }
    ChoiceButton(stringResource(R.string.exp_read), stringResource(R.string.exp_read) + ": " + reads[ResponseExperiment.READ_SAMPLES.indexOf(c.readSamples)], reads,
        ResponseExperiment.READ_SAMPLES.indexOf(c.readSamples), { state.chooseResponseExperiment(c.copy(readSamples = ResponseExperiment.READ_SAMPLES[it])) }, stringResource(R.string.exp_read_note))
    ExperimentalChoice(R.string.exp_ring, ResponseExperiment.RINGS, c.ringKiB, R.string.exp_kib, R.string.exp_ring_note) { state.chooseResponseExperiment(c.copy(ringKiB = it)) }
    SectionText(text = stringResource(R.string.exp_ring_estimate, c.ringKiB * 1024 * 1000.0 / (rate * 2), rate))
    ExperimentalToggle(stringResource(R.string.exp_light), c.lightOutput) { state.chooseResponseExperiment(c.copy(lightOutput = it)) }
    SectionText(text = stringResource(R.string.exp_light_note))

    SectionHeading(text = stringResource(R.string.exp_player_heading))
    SectionText(text = stringResource(R.string.exp_player_note))
    ExperimentalNumber(R.string.exp_player_buffer, c.playerBufferMs, 20..500, R.string.exp_ms, R.string.exp_player_buffer_note) { state.chooseResponseExperiment(c.copy(playerBufferMs = it)) }
    ExperimentalChoice(R.string.exp_start, listOf(1, 10, 25, 50, 75, 100), c.startPercent, R.string.exp_percent, R.string.exp_start_note) { state.chooseResponseExperiment(c.copy(startPercent = it)) }

    SectionHeading(text = stringResource(R.string.exp_test_heading))
    SectionText(text = stringResource(R.string.exp_test_note))
    val tester = remember(context) { ExperimentalSpeaker(context) }
    DisposableEffect(tester) { onDispose { tester.close() } }
    val example = stringResource(R.string.exp_test_text)
    val items = (1..6).map { context.getString(R.string.exp_test_item, it) }
    val testReady = tester.ready
    OutlinedButton(onClick = { tester.say(example) }, enabled = testReady, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.exp_test_say)) }
    OutlinedButton(onClick = { tester.interruptions(items) }, enabled = testReady, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.exp_test_swipes)) }
    OutlinedButton(onClick = { tester.stop() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.exp_test_stop)) }
    if (!testReady) SectionText(text = stringResource(R.string.exp_test_initializing))
    var reading by remember { mutableStateOf(ResponseTiming.latest) }
    var player by remember { mutableStateOf(ResponseTiming.player) }
    OutlinedButton(onClick = { reading = ResponseTiming.latest; player = ResponseTiming.player }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(stringResource(R.string.exp_measure_refresh)) }
    reading?.let { SectionText(text = stringResource(R.string.exp_measure, it.preparedMs, it.pcmMs, it.deliveredMs)) }
    if (SynthesisRouting.playsItself(state.synthesisRouting)) player?.let {
        SectionText(text = stringResource(R.string.exp_player_actual, it.frames * 1000.0 / it.rateHz,
            if (it.startFrames < 0) context.getString(R.string.exp_unavailable) else context.getString(R.string.exp_time_value, it.startFrames * 1000.0 / it.rateHz)))
    }
    SectionText(text = stringResource(R.string.exp_measure_note))
}

@Composable
private fun ExperimentalToggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    val status = stringResource(if (value) R.string.exp_on else R.string.exp_off)
    Row(modifier = Modifier.fillMaxWidth().toggleable(value = value, role = Role.Switch, onValueChange = change)
        .semantics(mergeDescendants = true) { stateDescription = status }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = null)
    }
}

@Composable
private fun ExperimentalChoice(label: Int, values: List<Int>, value: Int, unit: Int, note: Int, save: (Int) -> Unit) {
    val context = LocalContext.current
    val title = stringResource(label)
    val suffix = stringResource(unit)
    val options = values.map { n ->
        val hint = when (label) {
            R.string.exp_stream -> if (n == 0) R.string.exp_stream_small else R.string.exp_stream_large
            R.string.exp_prefetch -> if (n == 1) R.string.exp_prefetch_small else R.string.exp_prefetch_large
            R.string.exp_workers -> if (n == 1) R.string.exp_worker_single else R.string.exp_worker_extra
            R.string.exp_ring -> if (n <= 32) R.string.exp_ring_small else R.string.exp_ring_large
            else -> if (n <= 25) R.string.exp_start_small else R.string.exp_start_large
        }
        "$n $suffix. ${context.getString(hint)}"
    }
    ChoiceButton(title, "$title: $value $suffix", options, values.indexOf(value), { save(values[it]) }, stringResource(note))
}

@Composable
private fun ExperimentalNumber(label: Int, value: Int, range: IntRange, unit: Int, note: Int, save: (Int) -> Unit) {
    val title = stringResource(label)
    val suffix = stringResource(unit)
    val help = stringResource(note)
    var open by remember { mutableStateOf(false) }
    Described(help) { ValueButton("$title: $value $suffix") { open = true } }
    if (open) {
        var picked by remember { mutableIntStateOf(value) }
        val spoken = "$picked $suffix"
        AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(help)
                Text(spoken)
                Slider(value = picked.toFloat(), onValueChange = { picked = it.roundToInt().coerceIn(range) },
                    valueRange = range.first.toFloat()..range.last.toFloat(), steps = range.last - range.first - 1,
                    modifier = Modifier.semantics { contentDescription = title; stateDescription = spoken })
                Row {
                    OutlinedButton(onClick = { picked = (picked - 1).coerceIn(range) }, modifier = Modifier.semantics { contentDescription = "$title: −1 $suffix" }) { Text("−1") }
                    OutlinedButton(onClick = { picked = (picked + 1).coerceIn(range) }, modifier = Modifier.semantics { contentDescription = "$title: +1 $suffix" }) { Text("+1") }
                }
            }
        }, confirmButton = { TextButton(onClick = { save(picked); open = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
    }
}

/** Goes through the installed TTS service, unlike the direct-engine voice preview. */
private class ExperimentalSpeaker(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    var ready by mutableStateOf(false)
        private set
    private var closed = false
    private val tts = TextToSpeech(context.applicationContext, { status -> Handler(Looper.getMainLooper()).post { if (!closed) ready = status == TextToSpeech.SUCCESS } }, context.packageName)
    fun stop() { handler.removeCallbacksAndMessages(null); tts.stop() }
    fun say(text: String) { stop(); tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "experiment-${System.nanoTime()}") }
    fun interruptions(items: List<String>) {
        stop()
        items.forEachIndexed { i, text -> handler.postDelayed({ if (!closed) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "experiment-$i") }, i * 350L) }
    }
    fun close() { closed = true; stop(); tts.shutdown() }
}
