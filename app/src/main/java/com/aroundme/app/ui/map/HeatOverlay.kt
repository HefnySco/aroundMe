package com.aroundme.app.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import kotlin.math.cos
import kotlin.math.min
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class HeatOverlay(cells: List<Cell>) : Overlay() {

    data class Cell(val lat: Double, val lng: Double, val count: Int)

    private val cells = cells
    private val maxCount = (cells.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val tmp = Point()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pj = mapView.projection
        val baseRadius = (24 * mapView.zoomLevelDouble / 14.0).toFloat().coerceIn(10f, 60f)
        for (c in cells) {
            pj.toPixels(GeoPoint(c.lat, c.lng), tmp)
            val t = min(1f, c.count.toFloat() / maxCount)
            paint.color = heatColor(t)
            paint.alpha = 70 + (130 * t).toInt()
            canvas.drawCircle(tmp.x.toFloat(), tmp.y.toFloat(), baseRadius * (0.6f + 0.8f * t), paint)
        }
    }

    private fun heatColor(t: Float): Int {
        val (r1, g1, b1) = if (t < 0.5f) Triple(41, 121, 255) else Triple(255, 193, 7)
        val (r2, g2, b2) = if (t < 0.5f) Triple(255, 193, 7) else Triple(255, 23, 68)
        val u = if (t < 0.5f) t / 0.5f else (t - 0.5f) / 0.5f
        val r = (r1 + (r2 - r1) * u).toInt()
        val g = (g1 + (g2 - g1) * u).toInt()
        val b = (b1 + (b2 - b1) * u).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    companion object {
        private const val CELL = 0.00025

        fun bucketize(points: List<Pair<Double, Double>>): List<Cell> {
            val buckets = HashMap<Long, Triple<Double, Double, Int>>()
            for ((lat, lng) in points) {
                val key = (lat / CELL).toInt().toLong() * 1_000_000L +
                    (lng / CELL).toInt().toLong()
                val b = buckets[key]
                buckets[key] = if (b == null) Triple(lat, lng, 1)
                else Triple(b.first + lat, b.second + lng, b.third + 1)
            }
            return buckets.values.map { Cell(it.first / it.third, it.second / it.third, it.third) }
        }
    }
}
