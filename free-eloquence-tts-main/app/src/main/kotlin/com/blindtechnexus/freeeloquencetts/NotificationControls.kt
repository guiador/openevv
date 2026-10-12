package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent

/** One registry supplies both the accessible names and the parameter bounds. */
object NotificationControls {
    const val ACTION = "org.openevv.action.ADJUST"
    data class Item(val nameRes: Int, val values: List<Int> = emptyList(), val min: Int = 0, val max: Int = 100,
                    val get: (Settings) -> Int, val put: (Settings, Int) -> Unit, val default: (Settings) -> Boolean = {false}, val unit: String = "", val frequency: Int? = null,
                    val steps: List<Int> = emptyList(), val format: ((Context, Settings) -> String)? = null) {
        fun name(context: Context): String = if (frequency == null) context.getString(nameRes) else context.getString(nameRes, frequency)
    }
    private fun shape(name: Int, param: Int, default: Int = 50) = Item(name, get = { Eci.toPercent(param, it.shapeValue(it.voice, param) ?: VoiceDefaults.get(it,param) ?: default) },
        put = { s, v -> s.setShapeValue(s.voice, param, Eci.fromPercent(param,v))
            if (param == Eci.VOICE_SPEED) s.forceRate = true
            if (param == Eci.VOICE_PITCH_BASELINE) s.forcePitch = true
            if (param == Eci.VOICE_VOLUME) s.forceVolume = true
        }, default={param!=Eci.VOICE_SPEED && it.shapeValue(it.voice,param)==null && VoiceDefaults.get(it,param)==null}, unit=" %")
    val items = listOf(
        shape(R.string.control_text_1, Eci.VOICE_SPEED), shape(R.string.control_text_2, Eci.VOICE_PITCH_BASELINE),
        shape(R.string.control_text_3, Eci.VOICE_PITCH_FLUCTUATION), shape(R.string.control_text_4, Eci.VOICE_HEAD_SIZE),
        shape(R.string.control_text_5, Eci.VOICE_ROUGHNESS, 0), shape(R.string.control_text_6, Eci.VOICE_BREATHINESS, 0),
        shape(R.string.control_text_7, Eci.VOICE_VOLUME, 100),
        Item(R.string.control_text_8, listOf(R.string.control_text_9, R.string.control_text_10), get={it.shapeValue(it.voice,Eci.VOICE_GENDER) ?: Eci.defaultGender(it.voice)}, put={s,v -> s.setShapeValue(s.voice,Eci.VOICE_GENDER,v)}),
        Item(R.string.control_text_11, listOf(R.string.control_text_12, R.string.control_text_13, R.string.control_text_14), get={it.audioOptimization},put={s,v -> s.audioOptimization=v}),
        Item(R.string.control_text_15, Resampling.LABELS, get={Resampling.METHODS.indexOf(it.resampling)},put={s,v -> s.resampling=Resampling.METHODS[v]}, format={c,s -> Resampling.label(c,s.resampling,s.audioOptimization)}),
        Item(R.string.voice_label, List(Eci.PRESET_NAMES.size) { 0 }, get = { it.voice }, put = { s,v -> s.voice=v }),
        Item(R.string.control_text_21, List(Eci.LANGUAGES.size) { 0 },
            get = { s -> Eci.LANGUAGES.indexOfFirst { it.code == s.language }.coerceAtLeast(0) },
            put = { s,v -> s.language=Eci.LANGUAGES[v].code; s.forceLanguage=true }),
        Item(R.string.control_text_30, listOf(R.string.control_text_31, R.string.control_text_32, R.string.control_text_33, R.string.control_text_34, R.string.control_text_35),
            get = { EqualizerConfig.PRESETS.indexOf(it.equalizer) }, put = { s,v -> s.equalizer=EqualizerConfig.PRESETS[v] }),
        Item(R.string.control_text_36, List(Eci.SAMPLE_RATES.size) { 0 },
            get = { Eci.SAMPLE_RATES.indexOf(it.sampleRateHz) }, put = { s,v -> s.sampleRateHz=Eci.SAMPLE_RATES[v] }),
        Item(R.string.control_text_37, listOf(R.string.control_text_38, R.string.control_text_39, R.string.control_text_40, R.string.control_text_41),
            get = { it.synthesisRouting }, put = { s,v -> s.synthesisRouting=v }),
        Item(R.string.control_text_42, min=-100, max=100, get={it.ttsBalance}, put={s,v -> s.ttsBalance=v}, steps=listOf(-100,0,100), format={c,s -> balanceLabel(c,s.ttsBalance)}),
        Item(R.string.control_text_43, min=-90, max=-35, get={it.noiseDb}, put={s,v -> s.noiseDb=v; WirelessMaintenance.stop()}, unit=" dBFS"),
        Item(R.string.control_text_44, min=1, max=300, get={it.noiseSeconds}, put={s,v -> s.noiseSeconds=v; WirelessMaintenance.stop()}, steps=WirelessMaintenance.DURATIONS, format={c,s -> c.getString(R.string.seconds_value,s.noiseSeconds)}),
        Item(R.string.control_text_45, listOf(R.string.control_text_46, R.string.control_text_47), get={if(it.wirelessNoise) 1 else 0},
            put={s,v -> s.wirelessNoise=v==1; if(v==0) WirelessMaintenance.stop()})
    ) + listOf(Item(R.string.control_text_48, min=-12,max=12,get={it.equalizer.preamp},put={s,v -> s.equalizer=s.equalizer.copy(preamp=v)},unit=" dB")) +
        EqualizerConfig.FREQUENCIES.mapIndexed { index, hz ->
            Item(R.string.control_eq_band,frequency=hz,min=-12,max=12,get={it.equalizer.bands[index]},put={s,v ->
                val bands=s.equalizer.bands.toMutableList(); bands[index]=v; s.equalizer=s.equalizer.copy(bands=bands)},unit=" dB")
        }
    fun balanceLabel(context: Context, value: Int): String = context.getString(
        if(value<0) R.string.notification_text_9 else if(value>0) R.string.notification_text_11 else R.string.notification_text_10)
    internal fun steppedValue(steps: List<Int>, current: Int, delta: Int): Int =
        if(delta<0) steps.lastOrNull { it<current } ?: steps.first()
        else if(delta>0) steps.firstOrNull { it>current } ?: steps.last() else current
    fun shown(context: Context, s: Settings, item: Item): String {
        val n=item.get(s)
        if(item.default(s)) return context.getString(R.string.control_default)
        item.format?.let { return it(context,s) }
        val index = items.indexOf(item)
        if (index == 10) return Eci.PRESET_NAMES.getOrElse(n) { Eci.PRESET_NAMES[0] }
        if (index == 11) return Eci.LANGUAGES.getOrElse(n) { Eci.LANGUAGES[0] }.locale().getDisplayName(androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: java.util.Locale.getDefault())
        if (index == 13) return context.getString(R.string.hertz, Eci.SAMPLE_RATES.getOrElse(n) { 11025 })
        return item.values.getOrNull(n)?.let { context.getString(it) }
            ?: if(item.values.isNotEmpty()) context.getString(R.string.eq_text_9) else n.toString()+item.unit
    }
    fun add(builder: Notification.Builder, context: Context): Boolean {
        val s=Settings(context)
        try {
            if(!s.notificationControls) return false
            val item=items[s.notificationParameter]
            builder.setContentText(context.getString(R.string.control_notification, item.name(context), shown(context,s,item)))
                .setOnlyAlertOnce(true)
            for (delta in listOf(-1,1)) {
                val label=context.getString(if (item.values.isEmpty()) {
                    if(delta<0) R.string.notification_decrease else R.string.notification_increase
                } else { if(delta<0) R.string.notification_previous else R.string.notification_next }, item.name(context))
                val intent=Intent(context, EngineReceiver::class.java).setAction(ACTION).putExtra("delta",delta).putExtra("parameter",s.notificationParameter)
                val pending=PendingIntent.getBroadcast(context,100+2*s.notificationParameter+if(delta>0) 1 else 0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(Notification.Action.Builder(null,label,pending).build())
            }
            return true
        } finally { s.close() }
    }
    fun adjust(context: Context, delta: Int, parameter: Int? = null) {
        val s=Settings(context)
        try {
            if(parameter!=null && parameter!=s.notificationParameter) { refresh(context); return }
            val item=items[s.notificationParameter]
            if(item.default(s)) {
                android.widget.Toast.makeText(context,SpeechLocale.context(context).getString(R.string.control_load_voice),android.widget.Toast.LENGTH_LONG).show()
                return
            }
            val current=item.get(s)
            val v=if(item.values.isNotEmpty()) {
                if(current<0) {if(delta<0) item.values.lastIndex else 0}
                else ((current+delta)%item.values.size+item.values.size)%item.values.size
            }
                else if(item.steps.isNotEmpty()) steppedValue(item.steps,current,delta)
                else (current+delta).coerceIn(item.min,item.max)
            item.put(s,v)
            android.widget.Toast.makeText(context,"${item.name(SpeechLocale.context(context))}: ${shown(SpeechLocale.context(context),s,item)}",android.widget.Toast.LENGTH_SHORT).show()
            refresh(context)
        } finally {s.close()}
    }
    fun refresh(context: Context) {
        if(!KeepAliveService.isRunning) return
        val manager=context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            KeepAliveNotification.ensureChannel(context)
            manager.notify(KeepAliveNotification.ID,KeepAliveNotification.build(context,true))
        }
    }
}
