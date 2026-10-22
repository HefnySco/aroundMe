package com.aroundme.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.aroundme.app.data.db.AppDatabase
import org.osmdroid.config.Configuration

class App : Application() {

    val db: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService<NotificationManager>()!!
        nm.createNotificationChannel(
            NotificationChannel(CH_SCAN, "Tracking status", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        Configuration.getInstance().load(
            this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue = packageName
    }

    companion object {
        const val CH_SCAN = "aroundme_scan"
        const val CH_ALERTS = "aroundme_alerts"
        const val NOTIF_FG = 1001
        const val NOTIF_ALERT_BASE = 2000
    }
}
