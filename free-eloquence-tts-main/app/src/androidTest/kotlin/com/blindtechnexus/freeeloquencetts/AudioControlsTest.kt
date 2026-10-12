package com.blindtechnexus.freeeloquencetts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class AudioControlsTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()

    @Test fun notificationAndSettingsUseNativeMethodIdsRatherThanMenuPositions() {
        val s=Settings(context)
        val oldMethod=s.resampling; val oldProfile=s.audioOptimization
        try {
            val item=NotificationControls.items[9]
            for((index,method) in Resampling.METHODS.withIndex()) {
                item.put(s,index)
                assertEquals(method,s.resampling)
                assertEquals(index,item.get(s))
                assertEquals(Resampling.label(context,method,s.audioOptimization),NotificationControls.shown(context,s,item))
            }
            s.resampling=-1
            for(profile in listOf(AudioOptimization.BALANCED,AudioOptimization.VOLUME,AudioOptimization.QUALITY)) {
                s.audioOptimization=profile
                val actual=Resampling.method(-1,profile)
                assertTrue(NotificationControls.shown(context,s,item).contains(context.getString(Resampling.LABELS[Resampling.METHODS.indexOf(actual)])))
            }
        } finally {s.resampling=oldMethod; s.audioOptimization=oldProfile; s.close()}
    }

    @Test fun balanceAndIdleDurationHaveConsistentLabelsAndSteps() {
        val s=Settings(context)
        val oldBalance=s.ttsBalance; val oldTime=s.noiseSeconds
        try {
            for(balance in listOf(-100,0,100)) {
                s.ttsBalance=balance
                assertEquals(NotificationControls.balanceLabel(context,balance),NotificationControls.shown(context,s,NotificationControls.items[15]))
            }
            assertEquals(0,NotificationControls.steppedValue(listOf(-100,0,100),-10,1))
            assertEquals(20,NotificationControls.steppedValue(WirelessMaintenance.DURATIONS,15,1))
            assertEquals(300,NotificationControls.steppedValue(WirelessMaintenance.DURATIONS,300,1))
            s.noiseSeconds=500
            assertEquals(300,s.noiseSeconds)
            assertEquals(context.getString(R.string.seconds_value,300),NotificationControls.shown(context,s,NotificationControls.items[17]))
        } finally {s.ttsBalance=oldBalance; s.noiseSeconds=oldTime; s.close()}
    }

    @Test fun defaultEngineWarningChoicePersistsAcrossSettingsInstances() {
        val s=Settings(context); val old=s.hideDefaultEngineWarning
        try {
            s.hideDefaultEngineWarning=true
            val next=Settings(context)
            try {assertTrue(next.hideDefaultEngineWarning)} finally {next.close()}
        } finally {s.hideDefaultEngineWarning=old; s.close()}
    }
}
