package com.carradio.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Schedule
import com.carradio.service.RadioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Worker to handle scheduled station switching
 */
class ScheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = RadioDatabase.getDatabase(applicationContext)
            val scheduleDao = db.scheduleDao()

            val enabledSchedules = scheduleDao.getEnabledSchedulesSync()
            val deviceNow = Calendar.getInstance()
            val currentHour = deviceNow.get(Calendar.HOUR_OF_DAY)
            val currentMinute = deviceNow.get(Calendar.MINUTE)
            val currentTimeMinutes = currentHour * 60 + currentMinute
            
            val timeZoneId = deviceNow.timeZone.id

            // We allow a grace period of 2 minutes to account for WorkManager execution delays
            val dueSchedules = enabledSchedules.filter { schedule ->
                val parts = schedule.time.split(":")
                if (parts.size == 2) {
                    val scheduleHour = parts[0].toIntOrNull() ?: 0
                    val scheduleMinute = parts[1].toIntOrNull() ?: 0
                    val scheduleTimeMinutes = scheduleHour * 60 + scheduleMinute
                    
                    // Check if current time is exactly the schedule time OR within 2 minutes after it
                    // Also handle midnight wrap-around (e.g. schedule at 23:59, current time 00:00)
                    val timeDiff = if (currentTimeMinutes >= scheduleTimeMinutes) {
                        currentTimeMinutes - scheduleTimeMinutes
                    } else {
                        (currentTimeMinutes + 24 * 60) - scheduleTimeMinutes
                    }
                    
                    val isDue = timeDiff in 0..2
                    
                    // We need a specific trigger key that uniquely identifies this trigger attempt
                    // Use the schedule's day and minute so we only trigger once per scheduled occurrence
                    val targetCalendar = Calendar.getInstance().apply {
                        if (timeDiff > 0 && currentTimeMinutes < scheduleTimeMinutes) {
                            add(Calendar.DAY_OF_YEAR, -1) // It was scheduled yesterday
                        }
                    }
                    val triggerKey = "${targetCalendar.get(Calendar.YEAR)}-${targetCalendar.get(Calendar.DAY_OF_YEAR)}-$scheduleHour-$scheduleMinute"
                    
                    isDue && !wasTriggeredThisMinute(schedule.id, triggerKey)
                } else {
                    false
                }
            }

            if (dueSchedules.isNotEmpty()) {
                dueSchedules.forEach { schedule ->
                    val parts = schedule.time.split(":")
                    val scheduleHour = parts[0].toIntOrNull() ?: 0
                    val scheduleMinute = parts[1].toIntOrNull() ?: 0
                    val scheduleTimeMinutes = scheduleHour * 60 + scheduleMinute
                    val timeDiff = if (currentTimeMinutes >= scheduleTimeMinutes) {
                        currentTimeMinutes - scheduleTimeMinutes
                    } else {
                        (currentTimeMinutes + 24 * 60) - scheduleTimeMinutes
                    }
                    val targetCalendar = Calendar.getInstance().apply {
                        if (timeDiff > 0 && currentTimeMinutes < scheduleTimeMinutes) {
                            add(Calendar.DAY_OF_YEAR, -1)
                        }
                    }
                    val triggerKey = "${targetCalendar.get(Calendar.YEAR)}-${targetCalendar.get(Calendar.DAY_OF_YEAR)}-$scheduleHour-$scheduleMinute"
                    
                    android.util.Log.d(
                        TAG,
                        "Matched schedule at ${schedule.time} (current: ${String.format("%02d:%02d", currentHour, currentMinute)}), timezone=$timeZoneId, station=${schedule.stationName}"
                    )
                    triggerStationSwitch(schedule)
                    markTriggeredThisMinute(schedule.id, triggerKey)
                }
            } else {
                android.util.Log.d(
                    TAG,
                    "No enabled schedule matched current time: ${String.format("%02d:%02d", currentHour, currentMinute)}, timezone=$timeZoneId, enabledCount=${enabledSchedules.size}"
                )
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "doWork failed", e)
            Result.failure()
        }
    }

    private suspend fun triggerStationSwitch(schedule: Schedule) {
        try {
            val db = RadioDatabase.getDatabase(applicationContext)

            val repository = com.carradio.data.repository.RadioRepository(
                db.stationStateDao(),
                db.provinceDao(),
                db.scheduleDao()
            )

            val station = if (schedule.provinceCode != 0) {
                repository.refreshStations(schedule.provinceCode.toString())
                    .getOrNull()
                    ?.firstOrNull { it.contentId == schedule.stationId }
            } else {
                repository.getStationByIdDeepSearch(schedule.stationId)
            }

            if (station != null) {
                repository.updatePlayLastedSync(station, System.currentTimeMillis())

                val intent = Intent(applicationContext, RadioService::class.java).apply {
                    action = "com.carradio.ACTION_PLAY_STATION"
                    putExtra("station_id", station.contentId)
                    putExtra("station_title", station.title)
                    putExtra("station_subtitle", station.subtitle)
                    putExtra("station_image_url", station.imageUrl)
                    putExtra("station_play_url", station.getBestPlayUrl())
                }

                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                } catch (e: Exception) {
                    // Fallback to startService which might work if we are already in the foreground or have media permission
                    applicationContext.startService(intent)
                }

                android.util.Log.d(TAG, "Switching to station: ${schedule.stationName} at ${schedule.time}")
            } else {
                android.util.Log.e(TAG, "Could not find station details for ID: ${schedule.stationId}")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error triggering station switch", e)
        }
    }

    private fun wasTriggeredThisMinute(scheduleId: Long, minuteKey: String): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(getScheduleTriggerKey(scheduleId), null) == minuteKey
    }

    private fun markTriggeredThisMinute(scheduleId: Long, minuteKey: String) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(getScheduleTriggerKey(scheduleId), minuteKey).apply()
    }

    private fun getScheduleTriggerKey(scheduleId: Long): String = "schedule_trigger_$scheduleId"

    companion object {
        private const val TAG = "ScheduleWorker"
        private const val PREFS_NAME = "schedule_worker_prefs"
        private const val BEIJING_TIME_ZONE = "Asia/Shanghai"
    }
}
