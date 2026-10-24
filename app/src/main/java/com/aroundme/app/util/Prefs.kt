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

    var hideAfterMs: Long
        get() = sp.getLong(KEY_HIDE, 120_000L)
        set(v) = sp.edit().putLong(KEY_HIDE, v).apply()

    var mapMode: Int
        get() = sp.getInt(KEY_MAP_MODE, 0)
        set(v) = sp.edit().putInt(KEY_MAP_MODE, v).apply()

    var sortMode: Int
        get() = sp.getInt(KEY_SORT, 0)
        set(v) = sp.edit().putInt(KEY_SORT, v).apply()

    var groupByName: Boolean
        get() = sp.getBoolean(KEY_GROUP_NAME, false)
        set(v) = sp.edit().putBoolean(KEY_GROUP_NAME, v).apply()

    var hideNamelessBt: Boolean
        get() = sp.getBoolean(KEY_HIDE_NAMELESS, true)
        set(v) = sp.edit().putBoolean(KEY_HIDE_NAMELESS, v).apply()

    var clearedAtMs: Long
        get() = sp.getLong(KEY_CLEARED_AT, 0L)
        set(v) = sp.edit().putLong(KEY_CLEARED_AT, v).apply()

    companion object {
        private const val KEY_WIFI = "wifi_scan"
        private const val KEY_BT = "bt_scan"
        private const val KEY_CLASSIC = "classic_discovery"
        private const val KEY_INTERVAL = "scan_interval"
        private const val KEY_RUNNING = "service_running"
        private const val KEY_LAT = "last_lat"
        private const val KEY_LNG = "last_lng"
        private const val KEY_HAS_LOC = "has_loc"
        private const val KEY_HIDE = "hide_after"
        private const val KEY_MAP_MODE = "map_mode"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_GROUP_NAME = "group_by_name"
        private const val KEY_HIDE_NAMELESS = "hide_nameless_bt"
        private const val KEY_CLEARED_AT = "cleared_at_ms"

        val INTERVAL_OPTIONS = longArrayOf(10_000L, 20_000L, 30_000L, 60_000L, 120_000L)
        val INTERVAL_LABELS = arrayOf("10 s", "20 s", "30 s", "1 min", "2 min")

        val HIDE_OPTIONS = longArrayOf(30_000L, 60_000L, 120_000L, 300_000L, 900_000L, 3_600_000L)
        val HIDE_LABELS = arrayOf("30 s", "1 min", "2 min", "5 min", "15 min", "1 h")

        const val SORT_NEWEST = 0
        const val SORT_LATEST = 1
        const val SORT_MOST_SEEN = 2
        val SORT_LABELS = arrayOf("newest appeared", "latest update", "most seen")
    }
}
