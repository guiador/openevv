package com.blindtechnexus.freeeloquencetts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun EqualizerSection(state: SettingsModel, section: Int) {
    SectionHeading(text = when (section) { 0 -> stringResource(R.string.eq_text_1); 1 -> stringResource(R.string.eq_text_2); else -> stringResource(R.string.eq_text_3) })
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    LiveNotice(text = notice, horizontal = 16.dp)
    if (section == 0) SectionText(text = stringResource(R.string.eq_description))
    if (section == 0) {
    val names = listOf(stringResource(R.string.eq_text_4), stringResource(R.string.eq_text_5), stringResource(R.string.eq_text_6), stringResource(R.string.eq_text_7), stringResource(R.string.eq_text_8), stringResource(R.string.eq_text_9))
    val index = EqualizerConfig.PRESETS.indexOf(state.equalizer).let { if (it < 0) 5 else it }
    ChoiceButton(title = stringResource(R.string.eq_text_10), shown = stringResource(R.string.eq_profile_value, names[index]),
        options = names.take(EqualizerConfig.PRESETS.size), chosen = index, onChoose = {
            state.chooseEqualizer(EqualizerConfig.PRESETS[it])
        })
    state.equalizer.bands.forEachIndexed { i, db ->
        DecibelControl(stringResource(R.string.eq_band, EqualizerConfig.FREQUENCIES[i]), db) { gain ->
            state.chooseEqualizer(state.equalizer.copy(bands = state.equalizer.bands.toMutableList().also { it[i] = gain }))
        }
    }
    DecibelControl(stringResource(R.string.eq_text_11), state.equalizer.preamp) { state.chooseEqualizer(state.equalizer.copy(preamp = it)) }
    ActionRow(label = stringResource(R.string.reset_defaults), description = stringResource(R.string.reset_eq_description), onClick = {
        state.chooseEqualizer(EqualizerConfig.PRESETS.first())
        notice = context.getString(R.string.defaults_restored)
    })
    }
    if (section == 1) {
    SectionText(text = stringResource(R.string.eq_text_12))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(stringResource(R.string.eq_text_13), stringResource(R.string.eq_text_14), stringResource(R.string.eq_text_15)).forEachIndexed { i, label ->
            OutlinedButton(onClick = {
                state.chooseTimbre(i)
                notice = context.getString(R.string.eq_applied, label, Eci.PRESET_NAMES[state.voice])
            }, modifier = Modifier.fillMaxWidth()) { Text(label) }
        }
    }
    ActionRow(label = stringResource(R.string.reset_defaults), description = stringResource(R.string.reset_description), onClick = {
        state.resetVoice()
        notice = context.getString(R.string.voice_reset, Eci.PRESET_NAMES[state.voice])
    })
    }
    if (section == 2) {
    val modes = listOf(stringResource(R.string.synthesis_classic), stringResource(R.string.synthesis_wideband), stringResource(R.string.synthesis_native16))
    ChoiceButton(title = stringResource(R.string.synthesis_mode), shown = modes[state.synthesisMode],
        options = modes, chosen = state.synthesisMode, onChoose = { state.chooseSynthesisMode(it) },
        description = stringResource(R.string.synthesis_mode_help))
    ActionRow(label = stringResource(R.string.reset_defaults), description = stringResource(R.string.synthesis_reset_help), onClick = {
        state.chooseSynthesisMode(0)
        notice = context.getString(R.string.defaults_restored)
    })
    val methods = Resampling.LABELS.map { stringResource(it) }
    val method = Resampling.METHODS.indexOf(state.resampling).coerceAtLeast(0)
    ChoiceButton(title = stringResource(R.string.eq_text_21), shown = stringResource(R.string.eq_method_value, Resampling.label(context, state.resampling, state.audioOptimization)),
        options = methods, chosen = method, onChoose = { state.chooseResampling(Resampling.METHODS[it]) },
        description = stringResource(R.string.eq_text_22))
    SectionText(text = stringResource(R.string.eq_text_23))
    }
}

@Composable
private fun DecibelControl(label: String, value: Int, onSave: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("$label: $value dB")
    }
    if (open) {
        var selected by remember { mutableIntStateOf(value) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(label) }, text = {
            Column {
                Text("$selected dB")
                Slider(value = selected.toFloat(), onValueChange = { selected = it.roundToInt() },
                    valueRange = -12f..12f, steps = 23,
                    modifier = Modifier.semantics { contentDescription = label })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { selected = (selected - 1).coerceAtLeast(-12) }) { Text("−1 dB") }
                    OutlinedButton(onClick = { selected = (selected + 1).coerceAtMost(12) }) { Text("+1 dB") }
                }
            }
        }, confirmButton = { TextButton(onClick = { onSave(selected); open = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
