package com.aroundme.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dayFmt = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val dayTimeFmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())

    fun time(ts: Long): String = timeFmt.format(Date(ts))

    fun dayTime(ts: Long): String = dayTimeFmt.format(Date(ts))

    fun range(from: Long, to: Long): String {
        val sameDay = dayFmt.format(Date(from)) == dayFmt.format(Date(to))
        return if (sameDay) "${dayFmt.format(Date(from))} ${time(from)} → ${time(to)}"
        else "${dayTime(from)} → ${dayTime(to)}"
    }

    fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
        val d = (now - ts) / 1000
        return when {
            d < 5 -> "now"
            d < 60 -> "${d}s ago"
            d < 3600 -> "${d / 60}m ago"
            d < 86400 -> "${d / 3600}h ago"
            else -> "${d / 86400}d ago"
        }
    }
}
