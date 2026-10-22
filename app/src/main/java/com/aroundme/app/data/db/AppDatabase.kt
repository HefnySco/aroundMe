package com.aroundme.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aroundme.app.data.model.AlertEntity
import com.aroundme.app.data.model.DeviceEntity
import com.aroundme.app.data.model.ObjectDeviceLink
import com.aroundme.app.data.model.SightingEntity
import com.aroundme.app.data.model.VirtualObjectEntity

@Database(
    entities = [
        DeviceEntity::class,
        SightingEntity::class,
        VirtualObjectEntity::class,
        ObjectDeviceLink::class,
        AlertEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun sightingDao(): SightingDao
    abstract fun objectDao(): ObjectDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aroundme.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
