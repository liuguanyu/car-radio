package com.carradio

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.carradio.receiver.ScheduleAlarmReceiver

/**
 * Application class for the car radio app
 */
class CarRadioApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initScheduleAlarm()
    }

    private fun initScheduleAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ScheduleAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Fire every 60 seconds using elapsed realtime so it works regardless of clock changes
        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 5_000L, // first fire in 5 seconds
            60_000L, // repeat every 60 seconds
            pendingIntent
        )
    }

    companion object {
        private const val ALARM_REQUEST_CODE = 9001
    }
}
