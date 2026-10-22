package com.aroundme.app.logic

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.aroundme.app.App
import com.aroundme.app.R
import com.aroundme.app.data.db.AppDatabase
import com.aroundme.app.data.model.AlertEntity
import com.aroundme.app.data.model.AlertType
import com.aroundme.app.data.model.ObjectType
import com.aroundme.app.util.Format
import com.aroundme.app.util.Geo
import java.util.concurrent.atomic.AtomicInteger

class Analyzer(private val context: Context, private val db: AppDatabase) {

    private val movingChecks = HashMap<String, Long>()
    private var lastCoOccurRun = 0L
    private val notifId = AtomicInteger(App.NOTIF_ALERT_BASE)

    suspend fun onDeviceSeen(
        deviceId: String,
        prevLastSeen: Long,
        now: Long,
        lat: Double?,
        lng: Double?
    ) {
        val device = db.deviceDao().get(deviceId)
        val objects = db.objectDao().objectsForDevice(deviceId, device?.displayName)
        val reappeared = prevLastSeen == 0L || now - prevLastSeen > REAPPEAR_MS
        val hasPlaceOnly = objects.isNotEmpty() && objects.all { it.type == ObjectType.PLACE }

        if (reappeared && objects.isNotEmpty()) {
            val names = objects.joinToString(", ") { it.name }
            val label = device?.displayName?.takeIf { it.isNotBlank() } ?: device?.mac ?: deviceId
            insertAlert(
                AlertType.OBJECT_APPEARED, deviceId, lat, lng,
                "$names spotted nearby ($label)", notify = true
            )
        }

        val lastCheck = movingChecks[deviceId] ?: 0L
        if (!hasPlaceOnly && now - lastCheck > MOVING_CHECK_EVERY_MS) {
            movingChecks[deviceId] = now
            checkMoving(deviceId, objects.joinToString(", ") { it.name }, now, lat, lng)
        }

        if (now - lastCoOccurRun > CO_OCCUR_EVERY_MS) {
            lastCoOccurRun = now
            checkCoOccurrence(now)
        }
    }

    private suspend fun checkMoving(
        deviceId: String,
        objectNames: String,
        now: Long,
        lat: Double?,
        lng: Double?
    ) {
        val device = db.deviceDao().get(deviceId) ?: return
        if (device.movingWithYou) return
        val pts = db.sightingDao().forDeviceSince(deviceId, now - MOVING_WINDOW_MS)
            .filter { it.lat != null && it.lng != null }
        if (pts.size < 2) return
        val first = pts.first()
        val last = pts.last()
        if (last.timestamp - first.timestamp < MOVING_MIN_SPAN_MS) return
        val dist = Geo.distanceMeters(first.lat!!, first.lng!!, last.lat!!, last.lng!!)
        if (dist < MOVING_MIN_DIST_M) return
        db.deviceDao().setMoving(deviceId, true)
        val label = device.displayName?.takeIf { it.isNotBlank() } ?: device.mac
        val who = if (objectNames.isBlank()) label else "$objectNames ($label)"
        insertAlert(
            AlertType.MOVING_WITH_YOU, deviceId, lat, lng,
            "$who is moving with you — seen across ${Geo.formatDistance(dist)}",
            notify = true
        )
    }

    private suspend fun checkCoOccurrence(now: Long) {
        val rows = db.sightingDao().coOccurring(now - CO_OCCUR_WINDOW_MS, CO_OCCUR_MIN_CYCLES, 20)
        for (row in rows) {
            val a = db.deviceDao().get(row.deviceA)
            val b = db.deviceDao().get(row.deviceB)
            val la = a?.displayName?.takeIf { it.isNotBlank() } ?: a?.mac ?: row.deviceA
            val lb = b?.displayName?.takeIf { it.isNotBlank() } ?: b?.mac ?: row.deviceB
            val sharedObjects = db.objectDao()
                .objectsForDevice(row.deviceA, a?.displayName)
                .map { it.id }.toSet()
                .intersect(
                    db.objectDao().objectsForDevice(row.deviceB, b?.displayName)
                        .map { it.id }.toSet()
                )
            if (sharedObjects.isNotEmpty()) continue
            val msg = "$la and $lb appear together (${row.cycles}×) — maybe one object?"
            if (db.alertDao().countSimilar(msg, now - 24 * 3600_000L) > 0) continue
            insertAlert(AlertType.CO_OCCURRENCE, null, null, null, msg, notify = false)
        }
    }

    private suspend fun insertAlert(
        type: Int,
        deviceId: String?,
        lat: Double?,
        lng: Double?,
        message: String,
        notify: Boolean
    ) {
        val now = System.currentTimeMillis()
        db.alertDao().insert(
            AlertEntity(
                deviceId = deviceId, type = type, timestamp = now,
                lat = lat, lng = lng, message = message, acknowledged = false
            )
        )
        if (!notify) return
        val intent = if (deviceId != null) {
            Intent(context, com.aroundme.app.ui.device.DeviceDetailActivity::class.java)
                .putExtra(com.aroundme.app.ui.device.DeviceDetailActivity.EXTRA_DEVICE_ID, deviceId)
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
        }
        val pi = PendingIntent.getActivity(
            context, notifId.get(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, App.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("Around Me")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId.getAndIncrement(), n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val REAPPEAR_MS = 30 * 60_000L
        const val MOVING_WINDOW_MS = 2 * 3600_000L
        const val MOVING_MIN_SPAN_MS = 5 * 60_000L
        const val MOVING_MIN_DIST_M = 250.0
        const val MOVING_CHECK_EVERY_MS = 10 * 60_000L
        const val CO_OCCUR_EVERY_MS = 30 * 60_000L
        const val CO_OCCUR_WINDOW_MS = 24 * 3600_000L
        const val CO_OCCUR_MIN_CYCLES = 8
    }
}
