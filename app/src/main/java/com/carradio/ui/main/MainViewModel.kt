package com.carradio.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Province
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the main activity
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RadioRepository

    private val _stations = MutableLiveData<List<Station>>()
    val stations: LiveData<List<Station>> = _stations

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    val provinces: LiveData<List<Province>>

    private val _currentProvince = MutableLiveData<Province?>()
    val currentProvince: LiveData<Province?> = _currentProvince

    init {
        val database = RadioDatabase.getDatabase(application)
        repository = RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )
        provinces = repository.getAllProvinces()
    }

    fun loadStations(provinceCode: String = "0") {
        _loading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val result = repository.fetchStations(provinceCode)
                if (result.isSuccess) {
                    _stations.postValue(result.getOrDefault(emptyList()))
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to load stations"
                    _stations.postValue(emptyList())
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
                _stations.postValue(emptyList())
            } finally {
                _loading.value = false
            }
        }
    }

    fun setProvince(provinceCode: Int) {
        val provinceList = provinces.value ?: emptyList()
        val province = provinceList.find { it.code == provinceCode } ?: Province(provinceCode, "未知省份")
        _currentProvince.value = province
        loadStations(provinceCode.toString())
    }

    fun toggleFavorite(station: Station) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(station, !station.isFavorite)
                loadStations((_currentProvince.value?.code ?: 0).toString())
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update favorite"
            }
        }
    }
}