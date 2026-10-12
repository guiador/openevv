package com.blindtechnexus.freeeloquencetts

import android.content.Context
import android.media.*
import android.os.Build
import android.os.SystemClock
import android.os.PowerManager
import kotlin.math.pow
import kotlin.random.Random

/** Bounded, opt-in idle audio. Never changes Bluetooth codecs or other apps' audio. */
object WirelessMaintenance {
    val DURATIONS = listOf(1,5,10,15,20,30,45,60,90,120,180,240,300)
    private class Run { @Volatile var cancelled=false; @Volatile var track: AudioTrack?=null; @Volatile var lock: PowerManager.WakeLock?=null }
    private var speechCount=0
    private var silenced=false
    @Volatile private var current: Run?=null
    val active: Boolean get()=current!=null
    @Synchronized fun stop() {
        val old=current; current=null
        old?.cancelled=true
        runCatching { old?.track?.pause(); old?.track?.flush() }
        runCatching { old?.lock?.let { if(it.isHeld) it.release() } }
        if(old!=null) KeepAliveService.refreshForeground()
    }
    @Synchronized fun silence() { silenced=true; stop() }
    @Synchronized fun speechStarted() { speechCount++; silenced=false; stop() }
    @Synchronized fun speechEnded(context: Context, s: Settings) {
        speechCount=(speechCount-1).coerceAtLeast(0)
        if(speechCount==0 && !silenced) afterSpeech(context,s)
    }
    fun startIfIdle(context: Context, resume: Boolean = false) {
        val settings=Settings(context)
        try {
            synchronized(this) {
                if(resume) silenced=false
                afterSpeech(context,settings)
            }
        } finally { settings.close() }
    }
    private fun wireless(t: Int)=t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (Build.VERSION.SDK_INT>=31 && (t==AudioDeviceInfo.TYPE_BLE_HEADSET || t==AudioDeviceInfo.TYPE_BLE_SPEAKER))
    @Synchronized fun afterSpeech(context: Context, s: Settings) {
        if(speechCount>0 || silenced) return
        stop()
        // Require an existing visible foreground notification, and own playback so
        // the maintenance signal follows the same usage as the speech.
        if(!s.wirelessNoise || !s.persistentNotification || !KeepAliveService.isRunning ||
            !KeepAliveService.hasForeground || speechCount>0 || silenced ||
            !KeepAliveNotification.notificationsAllowed(context) ||
            !SynthesisRouting.playsItself(s.synthesisRouting) || s.synthesisRouting==SynthesisRouting.ALL) return
        val manager=context.getSystemService(AudioManager::class.java) ?: return
        if(manager.mode!=AudioManager.MODE_NORMAL) return
        if(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).none {wireless(it.type)}) return
        val seconds=s.noiseSeconds; val balance=s.ttsBalance
        val amplitude=(32767.0 * 10.0.pow(s.noiseDb/20.0)).toFloat()
        val usage=SynthesisRouting.usageOf(s.synthesisRouting)
        val run=Run(); current=run
        KeepAliveService.refreshForeground()
        Thread({
            var track: AudioTrack?=null
            try {
                if(run.cancelled) return@Thread
                val lock=context.getSystemService(PowerManager::class.java)?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,"OpenEVV:wireless-idle") ?: return@Thread
                synchronized(this) {
                    if(run.cancelled) return@Thread
                    run.lock=lock
                    lock.setReferenceCounted(false)
                    lock.acquire(seconds*1000L+2000L)
                }
                val rate=24000
                val minimum=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT)
                if(minimum<=0) return@Thread
                track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(maxOf(minimum,rate/5)).setTransferMode(AudioTrack.MODE_STREAM).build()
                run.track=track
                if(run.cancelled) return@Thread
                track.play()
                track.write(ByteArray(1920),0,1920)
                var attempts=0
                while(!run.cancelled && track.routedDevice==null && attempts++<20) Thread.sleep(5)
                val deadline=SystemClock.elapsedRealtime()+seconds*1000L
                val mono=ByteArray(960) // 20 ms; bounded cancellation latency.
                while(!run.cancelled && SystemClock.elapsedRealtime()<deadline) {
                    if(manager.mode!=AudioManager.MODE_NORMAL || !KeepAliveService.hasForeground) break
                    // Never emit noise through speakers after disconnect/rerouting.
                    val device=track.routedDevice
                    if(device==null || !wireless(device.type)) break
                    for(i in 0 until mono.size/2) {
                        val sample=((Random.nextFloat()*2-1)*amplitude).toInt()
                        mono[i*2]=sample.toByte(); mono[i*2+1]=(sample shr 8).toByte()
                    }
                    val stereo=TtsStereo.convert(mono,mono.size,balance)
                    var at=0
                    while(at<stereo.size && !run.cancelled && SystemClock.elapsedRealtime()<deadline) {
                        val n=track.write(stereo,at,stereo.size-at,AudioTrack.WRITE_NON_BLOCKING)
                        if(n<0) {run.cancelled=true; break}
                        at+=n
                        if(n==0) Thread.sleep(5)
                    }
                }
            } catch (_: Exception) {
                // A missing route or refused playback leaves speech unaffected.
            } finally {
                runCatching {track?.pause(); track?.flush(); track?.release()}
                runCatching {run.lock?.let {if(it.isHeld) it.release()}}
                synchronized(this) {if(current===run) current=null}
                KeepAliveService.refreshForeground()
            }
        },"OpenEVV-wireless-maintenance").start()
    }
}
