package com.carradio.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.carradio.data.model.Schedule

/**
 * Data Access Object for Schedule entity
 */
@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY time ASC")
    fun getAllSchedules(): LiveData<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1 ORDER BY time ASC")
    fun getEnabledSchedules(): LiveData<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1 ORDER BY time ASC")
    suspend fun getEnabledSchedulesSync(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): Schedule?

    @Insert
    suspend fun insertSchedule(schedule: Schedule): Long

    @Update
    suspend fun updateSchedule(schedule: Schedule)

    @Delete
    suspend fun deleteSchedule(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: Long)

    @Query("UPDATE schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun updateScheduleStatus(id: Long, isEnabled: Boolean)
}