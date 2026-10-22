package com.aroundme.app.scan

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aroundme.app.App
import com.aroundme.app.R
import com.aroundme.app.data.model.DeviceEntity
import com.aroundme.app.data.model.SightingEntity
import com.aroundme.app.data.model.Transport
import com.aroundme.app.logic.Analyzer
import com.aroundme.app.util.Geo
import com.aroundme.app.util.Prefs
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ScanService : LifecycleService() {

    private lateinit var prefs: Prefs
    private lateinit var fused: com.google.android.gms.location.FusedLocationProviderClient
    private lateinit var wifi: WifiManager
    private var btAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var scanJob: Job? = null
    private var lastLocation: Location? = null
    private lateinit var analyzer: Analyzer

    private val bleHits = ConcurrentHashMap<String, BleHit>()
    private val classicHits = ConcurrentHashMap<String, ClassicHit>()
    private var bleScanning = false

    private data class BleHit(val name: String?, val rssi: Int)
    private data class ClassicHit(val name: String?, val rssi: Int?, val ts: Long)

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            bleHits[dev.address] = BleHit(result.scanRecord?.deviceName, result.rssi)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (r in results) {
                val dev = r.device ?: continue
                bleHits[dev.address] = BleHit(r.scanRecord?.deviceName, r.rssi)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed: $errorCode")
        }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_FOUND) return
            val dev = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
            val name = try { dev.name } catch (_: SecurityException) { null }
            classicHits[dev.address] = ClassicHit(
                name, if (rssi == Short.MIN_VALUE) null else rssi.toInt(),
                System.currentTimeMillis()
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastLocation = it
                prefs.lastLat = it.latitude
                prefs.lastLng = it.longitude
                prefs.hasLocation = true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        analyzer = Analyzer(applicationContext, (application as App).db)
        fused = LocationServices.getFusedLocationProviderClient(this)
        wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        btAdapter = getSystemService(BluetoothManager::class.java)?.adapter
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(classicReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(classicReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when {
            intent?.action == ACTION_STOP -> stopTracking()
            intent?.action == ACTION_START -> startTracking()
            intent == null && prefs.serviceRunning -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (isRunning) return
        isRunning = true
        prefs.serviceRunning = true
        startForegroundCompat()
        startLocationUpdates()
        scanJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    runCycle(System.currentTimeMillis())
                } catch (t: Throwable) {
                    Log.w(TAG, "cycle failed", t)
                }
                delay(prefs.scanIntervalMs)
            }
        }
    }

    private fun stopTracking() {
        isRunning = false
        prefs.serviceRunning = false
        scanJob?.cancel()
        scanJob = null
        stopBleScan()
        try { fused.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        try { btAdapter?.cancelDiscovery() } catch (_: Exception) {}
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runCycle(cycleId: Long) {
        val wifiScanOn = prefs.wifiScan && (wifi.isWifiEnabled || wifi.isScanAlwaysAvailable)
        val btScanOn = prefs.btScan && btAdapter?.isEnabled == true

        if (wifiScanOn) {
            try { wifi.startScan() } catch (_: Exception) {}
        }

        if (btScanOn) {
            bleHits.clear()
            startBleScan()
            if (prefs.classicDiscovery) {
                try { btAdapter?.startDiscovery() } catch (_: Exception) {}
            }
            delay(BLE_WINDOW_MS)
            stopBleScan()
            try { btAdapter?.cancelDiscovery() } catch (_: Exception) {}
        } else if (wifiScanOn) {
            delay(WIFI_SETTLE_MS)
        } else {
            delay(IDLE_TICK_MS)
            return
        }

        val now = System.currentTimeMillis()
        val loc = lastLocation
        val lat = loc?.latitude
        val lng = loc?.longitude
        val acc = loc?.takeIf { it.hasAccuracy() }?.accuracy
        val speed = loc?.takeIf { it.hasSpeed() }?.speed

        val rows = mutableListOf<SightingEntity>()

        if (wifiScanOn) {
            val cutoffUs = SystemClock.elapsedRealtimeNanos() / 1000 - WIFI_MAX_AGE_US
            try {
                for (r in wifi.scanResults) {
                    val ageUs = SystemClock.elapsedRealtimeNanos() / 1000 - r.timestamp
                    if (ageUs in 1..WIFI_MAX_AGE_US) {
                        val ssid = r.SSID?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                        rows += sighting("${Transport.WIFI_AP}:${r.BSSID}", cycleId, now, r.level, lat, lng, acc, speed, ssid)
                    }
                }
            } catch (_: SecurityException) {}
        }

        if (btScanOn) {
            for ((mac, hit) in bleHits) {
                rows += sighting("${Transport.BLE}:$mac", cycleId, now, hit.rssi, lat, lng, acc, speed, hit.name)
            }
            for ((mac, hit) in classicHits) {
                if (now - hit.ts < CLASSIC_MAX_AGE_MS) {
                    rows += sighting("${Transport.BT_CLASSIC}:$mac", cycleId, now, hit.rssi, lat, lng, acc, speed, hit.name)
                }
            }
        }

        if (rows.isEmpty()) {
            updateNotification(0)
            return
        }

        val db = (application as App).db
        db.sightingDao().insertAll(rows)

        for ((deviceId, list) in rows.groupBy { it.deviceId }) {
            val existing = db.deviceDao().get(deviceId)
            val prevSeen = existing?.lastSeen ?: 0L
            val mac = deviceId.substringAfter(':')
            val transport = deviceId.substringBefore(':').toIntOrNull() ?: 0
            if (existing == null) {
                db.deviceDao().insert(
                    DeviceEntity(
                        id = deviceId, mac = mac, transport = transport,
                        displayName = list.first().label,
                        firstSeen = now, lastSeen = now, seenCount = 1,
                        randomizedMac = Geo.isLocallyAdministered(mac),
                        movingWithYou = false
                    )
                )
            } else {
                db.deviceDao().touch(deviceId, now, list.first().label)
            }
            analyzer.onDeviceSeen(deviceId, prevSeen, now, lat, lng)
        }

        updateNotification(rows.groupBy { it.deviceId }.size)
    }

    private fun sighting(
        deviceId: String, cycleId: Long, ts: Long, rssi: Int?,
        lat: Double?, lng: Double?, acc: Float?, speed: Float?, label: String?
    ) = SightingEntity(
        deviceId = deviceId, cycleId = cycleId, timestamp = ts, rssi = rssi,
        lat = lat, lng = lng, accuracy = acc, speed = speed, label = label
    )

    private fun startBleScan() {
        if (bleScanning) return
        try {
            if (bleScanner == null) bleScanner = btAdapter?.bluetoothLeScanner
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build()
            bleScanner?.startScan(null, settings, bleCallback)
            bleScanning = true
        } catch (t: Throwable) {
            Log.w(TAG, "ble startScan failed", t)
        }
    }

    private fun stopBleScan() {
        if (!bleScanning) return
        try { bleScanner?.stopScan(bleCallback) } catch (_: Exception) {}
        bleScanning = false
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            .build()
        try {
            fused.requestLocationUpdates(req, locationCallback, mainLooper)
        } catch (t: Throwable) {
            Log.w(TAG, "location updates failed", t)
        }
    }

    private fun fgNotification(count: Int): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (count > 0) "Tracking — $count devices around" else "Tracking — scanning…"
        return NotificationCompat.Builder(this, App.CH_SCAN)
            .setSmallIcon(R.drawable.ic_radar)
            .setContentTitle("Around Me")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startForegroundCompat() {
        val types = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else 0
        try {
            ServiceCompat.startForeground(this, App.NOTIF_FG, fgNotification(0), types)
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed", t)
        }
    }

    private fun updateNotification(count: Int) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(App.NOTIF_FG, fgNotification(count))
    }

    override fun onDestroy() {
        isRunning = false
        prefs.serviceRunning = false
        scanJob?.cancel()
        stopBleScan()
        try { unregisterReceiver(classicReceiver) } catch (_: Exception) {}
        try { fused.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.aroundme.app.action.START"
        const val ACTION_STOP = "com.aroundme.app.action.STOP"
        private const val TAG = "ScanService"
        private const val BLE_WINDOW_MS = 7_000L
        private const val WIFI_SETTLE_MS = 2_000L
        private const val IDLE_TICK_MS = 500L
        private const val WIFI_MAX_AGE_US = 90_000_000L
        private const val CLASSIC_MAX_AGE_MS = 30_000L
        private const val LOCATION_INTERVAL_MS = 10_000L

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val i = Intent(context, ScanService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, ScanService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
