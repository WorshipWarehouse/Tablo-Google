package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SavedMultiviewEntity::class, TabloDeviceEntity::class, FavoriteChannelEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedMultiviewDao(): SavedMultiviewDao
    abstract fun tabloDeviceDao(): TabloDeviceDao
    abstract fun favoriteChannelDao(): FavoriteChannelDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tablo_tv_multiview.db"
                ).addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /** Removes tokens written by pre-release builds from the Room database. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE tablo_device_new (" +
                        "`key` TEXT NOT NULL, `serverId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`model` TEXT NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, " +
                        "`streamingPort` INTEGER NOT NULL, `tunerCount` INTEGER NOT NULL, " +
                        "`isConnected` INTEGER NOT NULL, `firmware` TEXT NOT NULL, " +
                        "`clientId` TEXT NOT NULL, `isGen4` INTEGER NOT NULL, PRIMARY KEY(`key`))"
                )
                database.execSQL(
                    "INSERT INTO tablo_device_new (`key`, serverId, name, model, host, port, " +
                        "streamingPort, tunerCount, isConnected, firmware, clientId, isGen4) " +
                        "SELECT `key`, serverId, name, model, host, port, streamingPort, tunerCount, " +
                        "isConnected, firmware, clientId, isGen4 FROM tablo_device"
                )
                database.execSQL("DROP TABLE tablo_device")
                database.execSQL("ALTER TABLE tablo_device_new RENAME TO tablo_device")
            }
        }
    }
}
