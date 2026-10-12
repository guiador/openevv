package com.blindtechnexus.freeeloquencetts

import androidx.compose.ui.res.stringResource

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*

@Composable
internal fun NotificationCustomization() {
    val context=LocalContext.current
    val s=remember {Settings(context)}
    var revision by remember {mutableIntStateOf(0)}
    DisposableEffect(s) {onDispose {s.close()}}
    DisposableEffect(context) {
        val prefs = Settings.store(context)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun changed() {revision++; NotificationControls.refresh(context)}
    @Composable fun toggle(label:String, value:Boolean, change:(Boolean)->Unit) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(label,Modifier.weight(1f))
            Switch(value,{change(it); changed()},Modifier.semantics {contentDescription=label})
        }
    }
    Text(stringResource(R.string.notification_text_1),Modifier.semantics {heading()})
    SectionText(stringResource(R.string.notification_text_2))
    // Read the revision without replacing controls: accessibility focus stays in place.
    @Suppress("UNUSED_VARIABLE") val observed=revision
    toggle(stringResource(R.string.notification_text_3),s.persistentNotification) {
        s.persistentNotification=it; if(!it) WirelessMaintenance.stop(); KeepAlive.sync(context,it)
    }
    if(!KeepAliveNotification.notificationsAllowed(context)) Text(stringResource(R.string.notification_text_4))
    toggle(stringResource(R.string.notification_text_5),s.notificationControls) {s.notificationControls=it}
    val item=NotificationControls.items[s.notificationParameter]
    ChoiceButton(stringResource(R.string.notification_text_6), stringResource(R.string.notification_parameter_value, item.name(context)),NotificationControls.items.map {it.name(context)},s.notificationParameter,
        {s.notificationParameter=it;changed()})
    Text("${item.name(context)}: ${NotificationControls.shown(context,s,item)}")
    Row {
        Button({NotificationControls.adjust(context,-1);changed()},Modifier.weight(1f)) {Text(if(item.values.isEmpty()) stringResource(R.string.notification_decrease, item.name(context)) else stringResource(R.string.notification_previous, item.name(context)))}
        Button({NotificationControls.adjust(context,1);changed()},Modifier.weight(1f)) {Text(if(item.values.isEmpty()) stringResource(R.string.notification_increase, item.name(context)) else stringResource(R.string.notification_next, item.name(context)))}
    }
    ChoiceButton(stringResource(R.string.notification_text_7), stringResource(R.string.notification_balance_direction, NotificationControls.balanceLabel(context,s.ttsBalance)),
        listOf(stringResource(R.string.notification_text_9),stringResource(R.string.notification_text_10),stringResource(R.string.notification_text_11)),if(s.ttsBalance<0) 0 else if(s.ttsBalance>0) 2 else 1,
        {s.ttsBalance=listOf(-100,0,100)[it];changed()})
    SectionText(stringResource(R.string.notification_text_12))
    toggle(stringResource(R.string.notification_text_13),s.wirelessNoise) {
        s.wirelessNoise=it; WirelessMaintenance.stop()
        if(it) WirelessMaintenance.startIfIdle(context,resume=true)
    }
    SectionText(stringResource(R.string.notification_text_14))
    val levels=listOf(-90,-80,-70,-60,-50,-40,-35)
    ChoiceButton(stringResource(R.string.notification_text_15), stringResource(R.string.notification_noise_value, s.noiseDb),levels.map {"$it dBFS"},levels.indexOf(s.noiseDb).coerceAtLeast(0),
        {s.noiseDb=levels[it];WirelessMaintenance.stop();changed()})
    val times=WirelessMaintenance.DURATIONS
    ChoiceButton(stringResource(R.string.notification_text_16), stringResource(R.string.notification_time_value, s.noiseSeconds),times.map {stringResource(R.string.seconds_value, it)},times.indexOf(s.noiseSeconds).coerceAtLeast(0),
        {s.noiseSeconds=times[it];WirelessMaintenance.stop();changed()})
    Button({s.wirelessNoise=false;WirelessMaintenance.stop();changed()}) {Text(stringResource(R.string.notification_text_17))}
    Button({WirelessMaintenance.silence();EvvTtsService.instance?.stopSpeakingNow(maintainIdle=false)}) {Text(stringResource(R.string.notification_text_18))}
}
