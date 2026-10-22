package com.aroundme.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aroundme.app.data.model.AlertEntity
import com.aroundme.app.data.model.DeviceEntity
import com.aroundme.app.data.model.ObjectDeviceLink
import com.aroundme.app.data.model.ObjectNameLink
import com.aroundme.app.data.model.SightingEntity
import com.aroundme.app.data.model.VirtualObjectEntity

@Database(
    entities = [
        DeviceEntity::class,
        SightingEntity::class,
        VirtualObjectEntity::class,
        ObjectDeviceLink::class,
        ObjectNameLink::class,
        AlertEntity::class
    ],
    version = 3,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN appearance INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN btClass INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN companyId INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN beacon INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS object_names (" +
                        "objectId INTEGER NOT NULL, name TEXT NOT NULL, " +
                        "PRIMARY KEY(objectId, name))"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aroundme.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
