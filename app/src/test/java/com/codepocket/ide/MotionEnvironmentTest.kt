package com.codepocket.ide

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.codepocket.ide.animation.MotionEnvironment
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class MotionEnvironmentTest {
    private lateinit var activity:Activity
    private var observer:MotionEnvironment?=null
    private val scales=listOf(Settings.Global.ANIMATOR_DURATION_SCALE,Settings.Global.TRANSITION_ANIMATION_SCALE,Settings.Global.WINDOW_ANIMATION_SCALE)
    @Before fun prepare(){activity=Robolectric.buildActivity(Activity::class.java).setup().get();scales.forEach {Settings.Global.putFloat(activity.contentResolver,it,1f)}}
    @After fun cleanup(){observer?.stop();scales.forEach {Settings.Global.putFloat(activity.contentResolver,it,1f)}}
    @Test fun eachSystemAnimationSwitchCanDisableAllAppMotion(){
        assertTrue(MotionEnvironment.enabled(activity,true))
        scales.forEach {key->Settings.Global.putFloat(activity.contentResolver,key,0f);assertFalse(MotionEnvironment.enabled(activity,true));Settings.Global.putFloat(activity.contentResolver,key,1f)}
        assertFalse(MotionEnvironment.enabled(activity,false))
    }
    @Test fun observerStartsOnceAndReceivesActualSettingsChanges(){
        var updates=0;observer=MotionEnvironment(activity){updates++};observer!!.start();observer!!.start();assertEquals(1,updates)
        Settings.Global.putFloat(activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,0f);shadowOf(Looper.getMainLooper()).idle()
        assertTrue(updates>=2);assertFalse(MotionEnvironment.enabled(activity,true))
    }
    @Test fun stoppingUnregistersAndCanBeRepeated(){
        var updates=0;observer=MotionEnvironment(activity){updates++};observer!!.start();observer!!.stop();observer!!.stop()
        val previous=updates;Settings.Global.putFloat(activity.contentResolver,Settings.Global.WINDOW_ANIMATION_SCALE,0f)
        shadowOf(Looper.getMainLooper()).idle();assertEquals(previous,updates)
    }
    @Test fun powerSavingSuppressesMotionAndBroadcastNotifiesTheOwner(){
        var updates=0;observer=MotionEnvironment(activity){updates++};observer!!.start()
        val manager=activity.getSystemService(PowerManager::class.java)
        shadowOf(manager).setIsPowerSaveMode(true)
        activity.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED));shadowOf(Looper.getMainLooper()).idle()
        assertFalse(MotionEnvironment.enabled(activity,true));assertTrue(updates>=2)
    }
    @Test fun cursorSettingsAreIndependentAndPersistWithoutChangingOtherPreferences(){
        val settings=AppSettings(cursorMovement=false,cursorBlink=false,animations=true,wordWrap=true)
        assertEquals(settings,AppSettings.fromJson(settings.toJson()))
        val old=AppSettings.fromJson("""{"version":6,"animations":false}""")
        assertFalse(old.animations);assertTrue(old.cursorMovement);assertTrue(old.cursorBlink)
    }
}
