package com.aroundme.app.util

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("aroundme", Context.MODE_PRIVATE)

    var wifiScan: Boolean
        get() = sp.getBoolean(KEY_WIFI, true)
        set(v) = sp.edit().putBoolean(KEY_WIFI, v).apply()

    var btScan: Boolean
        get() = sp.getBoolean(KEY_BT, true)
        set(v) = sp.edit().putBoolean(KEY_BT, v).apply()

    var classicDiscovery: Boolean
        get() = sp.getBoolean(KEY_CLASSIC, true)
        set(v) = sp.edit().putBoolean(KEY_CLASSIC, v).apply()

    var scanIntervalMs: Long
        get() = sp.getLong(KEY_INTERVAL, 20_000L)
        set(v) = sp.edit().putLong(KEY_INTERVAL, v).apply()

    var serviceRunning: Boolean
        get() = sp.getBoolean(KEY_RUNNING, false)
        set(v) = sp.edit().putBoolean(KEY_RUNNING, v).apply()

    var lastLat: Double
        get() = Double.fromBits(sp.getLong(KEY_LAT, 0))
        set(v) = sp.edit().putLong(KEY_LAT, v.toBits()).apply()

    var lastLng: Double
        get() = Double.fromBits(sp.getLong(KEY_LNG, 0))
        set(v) = sp.edit().putLong(KEY_LNG, v.toBits()).apply()

    var hasLocation: Boolean
        get() = sp.getBoolean(KEY_HAS_LOC, false)
        set(v) = sp.edit().putBoolean(KEY_HAS_LOC, v).apply()

    companion object {
        private const val KEY_WIFI = "wifi_scan"
        private const val KEY_BT = "bt_scan"
        private const val KEY_CLASSIC = "classic_discovery"
        private const val KEY_INTERVAL = "scan_interval"
        private const val KEY_RUNNING = "service_running"
        private const val KEY_LAT = "last_lat"
        private const val KEY_LNG = "last_lng"
        private const val KEY_HAS_LOC = "has_loc"

        val INTERVAL_OPTIONS = longArrayOf(10_000L, 20_000L, 30_000L, 60_000L, 120_000L)
        val INTERVAL_LABELS = arrayOf("10 s", "20 s", "30 s", "1 min", "2 min")
    }
}
