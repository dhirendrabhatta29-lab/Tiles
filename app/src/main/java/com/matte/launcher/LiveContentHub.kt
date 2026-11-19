package com.matte.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Build

/**
 * Reactive hub for live device metrics (battery level, charging state) and cached weather data
 * so dynamic tiles display up-to-date information immediately on startup and in real time.
 */
internal class LiveContentHub(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onBatteryChanged: () -> Unit
) {
    data class BatteryStatus(val percent: Int, val charging: Boolean) {
        fun shortLabel(): String = if (charging) "⚡ $percent%" else "$percent%"
    }

    data class CachedWeather(val temp: Int, val desc: String, val hiLo: String)

    var battery: BatteryStatus = BatteryStatus(100, false)
        private set

    private var registered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            updateBatteryFromIntent(intent)
        }
    }

    fun start() {
        if (registered) return
        registered = true
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(batteryReceiver, filter)
            }
        } catch (_: Exception) {
            null
        }
        if (sticky != null) updateBatteryFromIntent(sticky)
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) { }
    }

    private fun updateBatteryFromIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else battery.percent
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val next = BatteryStatus(pct, charging)
        if (next != battery) {
            battery = next
            onBatteryChanged()
        }
    }

    fun loadCachedWeather(): CachedWeather? {
        if (!prefs.contains("wx_cached_temp")) return null
        val temp = prefs.getInt("wx_cached_temp", 0)
        val desc = prefs.getString("wx_cached_desc", null) ?: return null
        val hl = prefs.getString("wx_cached_hl", "") ?: ""
        return CachedWeather(temp, desc, hl)
    }

    fun saveCachedWeather(temp: Int, desc: String, hiLo: String) {
        prefs.edit()
            .putInt("wx_cached_temp", temp)
            .putString("wx_cached_desc", desc)
            .putString("wx_cached_hl", hiLo)
            .apply()
    }
}
