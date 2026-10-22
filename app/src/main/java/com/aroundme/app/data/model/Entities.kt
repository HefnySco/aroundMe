package com.aroundme.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object Transport {
    const val WIFI_AP = 1
    const val BLE = 2
    const val BT_CLASSIC = 3

    fun label(t: Int) = when (t) {
        WIFI_AP -> "Wi-Fi AP"
        BLE -> "BLE"
        BT_CLASSIC -> "BT"
        else -> "?"
    }
}

object ObjectType {
    const val PERSON = 1
    const val PLACE = 2
    const val CAR = 3
    const val DEVICE = 4
    const val OTHER = 5

    val labels = arrayOf("Person", "Place", "Car", "Device", "Other")

    fun label(t: Int) = labels.getOrElse(t - 1) { "Other" }
}

object AlertType {
    const val MOVING_WITH_YOU = 1
    const val OBJECT_APPEARED = 2
    const val CO_OCCURRENCE = 3
}

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val mac: String,
    val transport: Int,
    val displayName: String?,
    val firstSeen: Long,
    val lastSeen: Long,
    val seenCount: Int,
    val randomizedMac: Boolean,
    val movingWithYou: Boolean
)

@Entity(
    tableName = "sightings",
    indices = [Index("deviceId", "timestamp"), Index("cycleId"), Index("timestamp")]
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val cycleId: Long,
    val timestamp: Long,
    val rssi: Int?,
    val lat: Double?,
    val lng: Double?,
    val accuracy: Float?,
    val speed: Float?,
    val label: String?
)

@Entity(tableName = "objects")
data class VirtualObjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: Int,
    val note: String?,
    val createdAt: Long
)

@Entity(
    tableName = "object_devices",
    primaryKeys = ["objectId", "deviceId"],
    indices = [Index("deviceId")]
)
data class ObjectDeviceLink(
    val objectId: Long,
    val deviceId: String
)

@Entity(tableName = "alerts", indices = [Index("timestamp")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String?,
    val type: Int,
    val timestamp: Long,
    val lat: Double?,
    val lng: Double?,
    val message: String,
    val acknowledged: Boolean
)

data class DeviceWithObjects(
    @Embedded val device: DeviceEntity,
    val objectNames: String?
)

data class CoOccurRow(
    val deviceA: String,
    val deviceB: String,
    val cycles: Int
)
