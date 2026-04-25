package com.carradio.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carradio.data.model.Province
import com.carradio.data.model.Schedule
import com.carradio.data.model.StationState

/**
 * Room database for the radio app
 */
@Database(
    entities = [StationState::class, Province::class, Schedule::class],
    version = 5,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {

    abstract fun stationStateDao(): StationStateDao
    abstract fun provinceDao(): ProvinceDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun getDatabase(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "radio_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}