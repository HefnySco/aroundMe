package com.aroundme.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aroundme.app.data.model.AlertEntity
import com.aroundme.app.data.model.CoOccurRow
import com.aroundme.app.data.model.DeviceEntity
import com.aroundme.app.data.model.DeviceWithObjects
import com.aroundme.app.data.model.ObjectDeviceLink
import com.aroundme.app.data.model.SightingEntity
import com.aroundme.app.data.model.VirtualObjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: DeviceEntity): Long

    @Query("UPDATE devices SET lastSeen = :ts, seenCount = seenCount + 1, displayName = COALESCE(:name, displayName) WHERE id = :id")
    suspend fun touch(id: String, ts: Long, name: String?)

    @Query(
        """UPDATE devices SET
           appearance = COALESCE(:appearance, appearance),
           btClass = COALESCE(:btClass, btClass),
           companyId = COALESCE(:companyId, companyId),
           beacon = CASE WHEN :beacon THEN 1 ELSE beacon END
           WHERE id = :id"""
    )
    suspend fun updateAdvInfo(id: String, appearance: Int?, btClass: Int?, companyId: Int?, beacon: Boolean)

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun get(id: String): DeviceEntity?

    @Query("UPDATE devices SET movingWithYou = :moving WHERE id = :id")
    suspend fun setMoving(id: String, moving: Boolean)

    @Query("SELECT * FROM devices WHERE lastSeen > :since ORDER BY lastSeen DESC")
    suspend fun seenSinceList(since: Long): List<DeviceEntity>

    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun allDevicesFlow(): Flow<List<DeviceEntity>>

    @Query(
        """SELECT d.*, GROUP_CONCAT(o.name, ', ') AS objectNames
           FROM devices d
           LEFT JOIN object_devices od ON od.deviceId = d.id
           LEFT JOIN objects o ON o.id = od.objectId
           WHERE d.lastSeen > :since
           GROUP BY d.id
           ORDER BY d.lastSeen DESC"""
    )
    fun liveDevices(since: Long): Flow<List<DeviceWithObjects>>

    @Query("SELECT * FROM devices ORDER BY lastSeen DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DeviceEntity>
}

@Dao
interface SightingDao {
    @Insert
    suspend fun insertAll(rows: List<SightingEntity>)

    @Query("SELECT * FROM sightings WHERE deviceId = :deviceId ORDER BY timestamp")
    suspend fun forDevice(deviceId: String): List<SightingEntity>

    @Query("SELECT * FROM sightings WHERE deviceId = :deviceId AND timestamp > :since ORDER BY timestamp")
    suspend fun forDeviceSince(deviceId: String, since: Long): List<SightingEntity>

    @Query("SELECT * FROM sightings WHERE deviceId IN (:deviceIds) AND timestamp > :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun forDevicesSince(deviceIds: List<String>, since: Long, limit: Int): List<SightingEntity>

    @Query("SELECT * FROM sightings WHERE timestamp > :since")
    suspend fun since(since: Long): List<SightingEntity>

    @Query("SELECT * FROM sightings WHERE deviceId = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun lastForDevice(deviceId: String): SightingEntity?

    @Query("SELECT lat, lng FROM sightings WHERE timestamp > :since AND lat IS NOT NULL AND lng IS NOT NULL LIMIT :limit")
    suspend fun pointsSince(since: Long, limit: Int): List<PointRow>

    @Query("SELECT DISTINCT deviceId FROM sightings WHERE timestamp > :since")
    suspend fun deviceIdsSince(since: Long): List<String>

    @Query(
        """SELECT s.* FROM sightings s
           JOIN (SELECT deviceId, MAX(timestamp) AS mt FROM sightings
                 WHERE timestamp > :since AND lat IS NOT NULL GROUP BY deviceId) t
           ON t.deviceId = s.deviceId AND t.mt = s.timestamp
           LIMIT :limit"""
    )
    suspend fun lastPositionsSince(since: Long, limit: Int): List<SightingEntity>

    @Query(
        """SELECT s1.deviceId AS deviceA, s2.deviceId AS deviceB, COUNT(DISTINCT s1.cycleId) AS cycles
           FROM sightings s1
           JOIN sightings s2 ON s1.cycleId = s2.cycleId AND s1.deviceId < s2.deviceId
           WHERE s1.timestamp > :since
           GROUP BY deviceA, deviceB
           HAVING cycles >= :minCycles
           ORDER BY cycles DESC
           LIMIT :limit"""
    )
    suspend fun coOccurring(since: Long, minCycles: Int, limit: Int): List<CoOccurRow>
}

@Dao
interface ObjectDao {
    @Insert
    suspend fun insert(obj: VirtualObjectEntity): Long

    @Update
    suspend fun update(obj: VirtualObjectEntity)

    @Delete
    suspend fun delete(obj: VirtualObjectEntity)

    @Query("SELECT * FROM objects WHERE id = :id")
    suspend fun get(id: Long): VirtualObjectEntity?

    @Query("SELECT * FROM objects ORDER BY name")
    fun objectsFlow(): Flow<List<VirtualObjectEntity>>

    @Query(
        """SELECT o.*, (SELECT COUNT(*) FROM object_devices od WHERE od.objectId = o.id) AS deviceCount
           FROM objects o ORDER BY o.name"""
    )
    fun objectsWithCountFlow(): Flow<List<ObjectWithCount>>

    @Query("SELECT * FROM objects")
    suspend fun all(): List<VirtualObjectEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(link: ObjectDeviceLink)

    @Query("DELETE FROM object_devices WHERE objectId = :objectId AND deviceId = :deviceId")
    suspend fun unlink(objectId: Long, deviceId: String)

    @Query("SELECT deviceId FROM object_devices WHERE objectId = :objectId")
    suspend fun deviceIdsForObject(objectId: Long): List<String>

    @Query("SELECT o.* FROM objects o JOIN object_devices od ON o.id = od.objectId WHERE od.deviceId = :deviceId")
    suspend fun objectsForDevice(deviceId: String): List<VirtualObjectEntity>

    @Query("SELECT COUNT(*) FROM object_devices WHERE objectId = :objectId")
    suspend fun deviceCount(objectId: Long): Int

    @Query("SELECT od.deviceId, o.name AS objectName FROM object_devices od JOIN objects o ON o.id = od.objectId")
    fun allLinksFlow(): Flow<List<DeviceLinkRow>>
}

data class DeviceLinkRow(
    val deviceId: String,
    val objectName: String
)

data class ObjectWithCount(
    @androidx.room.Embedded val obj: VirtualObjectEntity,
    val deviceCount: Int
)

data class PointRow(
    val lat: Double,
    val lng: Double
)

@Dao
interface AlertDao {
    @Insert
    suspend fun insert(alert: AlertEntity): Long

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 500")
    fun allFlow(): Flow<List<AlertEntity>>

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("SELECT COUNT(*) FROM alerts WHERE message = :message AND timestamp > :since")
    suspend fun countSimilar(message: String, since: Long): Int
}
