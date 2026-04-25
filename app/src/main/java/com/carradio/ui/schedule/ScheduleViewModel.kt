package com.carradio.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Schedule
import com.carradio.data.repository.RadioRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the schedule activity
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RadioRepository

    private val _schedules = MutableLiveData<List<Schedule>>()
    val schedules: LiveData<List<Schedule>> = _schedules

    init {
        val database = RadioDatabase.getDatabase(application)
        repository = RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )
    }

    fun loadSchedules() {
        viewModelScope.launch {
            repository.getAllSchedules().observeForever { scheduleList: List<Schedule> ->
                _schedules.value = scheduleList
            }
        }
    }

    fun insertSchedule(schedule: Schedule) {
        viewModelScope.launch {
            try {
                repository.insertSchedule(schedule)
                loadSchedules()
            } catch (_: Exception) {
            }
        }
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            try {
                repository.updateSchedule(schedule)
                loadSchedules()
            } catch (_: Exception) {
            }
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            try {
                repository.deleteSchedule(schedule)
                loadSchedules()
            } catch (_: Exception) {
            }
        }
    }

    fun toggleScheduleStatus(scheduleId: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.toggleScheduleStatus(scheduleId, isEnabled)
                loadSchedules()
            } catch (_: Exception) {
            }
        }
    }
}