package com.aroundme.app.util

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(a))
    }

    fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format("%.1f km", meters / 1000)
        else String.format("%.0f m", meters)

    fun isLocallyAdministered(mac: String): Boolean {
        val first = mac.substringBefore(':').toIntOrNull(16) ?: return false
        return first and 0x02 != 0
    }
}
