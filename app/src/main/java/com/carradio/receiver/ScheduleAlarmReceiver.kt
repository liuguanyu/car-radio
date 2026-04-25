package com.carradio.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.carradio.worker.ScheduleWorker

/**
 * Receiver to handle precise minute-by-minute alarms for schedule checking
 */
class ScheduleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Trigger the ScheduleWorker immediately when the alarm fires
        val workRequest = OneTimeWorkRequestBuilder<ScheduleWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
