package com.codepocket.ide.animation

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import com.codepocket.ide.MotionPolicy

/** Observe actual system switches, including API24, without polling from animation frames. */
class MotionEnvironment(private val context: Context, private val changed: () -> Unit) {
    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { changed() }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { changed() }
    }
    fun start() {
        if (observing) return
        observing = true
        scales.forEach { context.contentResolver.registerContentObserver(Settings.Global.getUriFor(it), false, observer) }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        changed()
    }
    fun stop() {
        if (!observing) return
        observing = false
        context.contentResolver.unregisterContentObserver(observer)
        context.unregisterReceiver(receiver)
    }
    companion object {
        private val scales = listOf(Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE)
        fun enabled(context: Context, appEnabled: Boolean): Boolean {
            val scale = runCatching { scales.minOf { Settings.Global.getFloat(context.contentResolver, it, 1f) } }.getOrDefault(0f)
            val save = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: true
            return MotionPolicy.enabled(appEnabled, scale, save) &&
                (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
        }
    }
}

interface MotionParticipant { fun finishMotion() }
