package com.blindtechnexus.freeeloquencetts

import androidx.compose.ui.res.stringResource

import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal val LocalExpertInterface=staticCompositionLocalOf {false}

@Composable
internal fun InterfaceMode(content:@Composable ()->Unit) {
    val context=LocalContext.current
    val prefs=remember(context) {Settings.store(context)}
    var expert by remember {mutableStateOf(prefs.getBoolean("expert_interface",false))}
    DisposableEffect(prefs) {
        val watcher=SharedPreferences.OnSharedPreferenceChangeListener {_,key ->
            if(key=="expert_interface") expert=prefs.getBoolean(key,false)
        }
        prefs.registerOnSharedPreferenceChangeListener(watcher)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(watcher)}
    }
    CompositionLocalProvider(LocalExpertInterface provides expert,content=content)
}

@Composable
internal fun InterfaceModeSelector() {
    val context=LocalContext.current
    val expert=LocalExpertInterface.current
    ChoiceButton(stringResource(R.string.interface_text_1), stringResource(R.string.interface_mode_value, stringResource(if (expert) R.string.interface_text_4 else R.string.interface_text_3)),
        listOf(stringResource(R.string.interface_text_3), stringResource(R.string.interface_text_4)),if(expert) 1 else 0,
        {Settings.store(context).edit().putBoolean("expert_interface",it==1).apply()},
        description=stringResource(R.string.interface_text_5))
}
