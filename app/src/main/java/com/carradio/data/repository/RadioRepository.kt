package com.carradio.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import com.carradio.data.api.RadioApiService
import com.carradio.data.db.ProvinceDao
import com.carradio.data.db.ScheduleDao
import com.carradio.data.db.StationStateDao
import com.carradio.data.model.Province
import com.carradio.data.model.Schedule
import com.carradio.data.model.Station
import com.carradio.data.model.StationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Repository for managing radio station data
 * Handles both local database and remote API calls
 */
class RadioRepository(
    private val stationStateDao: StationStateDao,
    private val provinceDao: ProvinceDao,
    private val scheduleDao: ScheduleDao
) {

    private val apiService: RadioApiService by lazy {
        Retrofit.Builder()
            .baseUrl(RadioApiService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RadioApiService::class.java)
    }

    suspend fun fetchStations(provinceCode: String = "0"): Result<List<Station>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getStations(
                categoryId = "0",
                provinceCode = provinceCode
            ).execute()

            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse != null && apiResponse.code == 0) {
                    Result.success(mergeStates(apiResponse.data ?: emptyList()))
                } else {
                    Result.failure(Exception("API returned error: ${apiResponse?.message}"))
                }
            } else {
                Result.failure(Exception("HTTP error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFirstStationByProvince(provinceCode: Int): Station? {
        return fetchStations(provinceCode.toString()).getOrNull()?.firstOrNull()
    }

    suspend fun getFavoriteStations(): LiveData<List<Station>> = withContext(Dispatchers.IO) {
        liveData(context = Dispatchers.IO) {
            val favoriteStates = stationStateDao.getFavoriteStatesSync()
            val stations = favoriteStates.mapNotNull { state ->
                fetchStationByIdFromApi(state.contentId)?.applyState(state)
            }
            emit(stations)
        }
    }

    suspend fun getAllStationsForSchedule(): List<Station> = withContext(Dispatchers.IO) {
        fetchStations("0").getOrDefault(emptyList())
    }

    suspend fun getLastPlayedStation(): Station? = withContext(Dispatchers.IO) {
        val state = stationStateDao.getLastPlayedState() ?: return@withContext null
        fetchStationByIdFromApi(state.contentId)?.applyState(state)
    }

    suspend fun getStationById(id: String): Station? = withContext(Dispatchers.IO) {
        val state = stationStateDao.getStateById(id)
        fetchStationByIdFromApi(id)?.applyState(state)
    }

    suspend fun getStationByIdDeepSearch(id: String): Station? = withContext(Dispatchers.IO) {
        val state = stationStateDao.getStateById(id)

        fetchStationByIdFromApi(id)?.let { station ->
            return@withContext station.applyState(state)
        }

        val provinces = provinceDao.getAllProvincesSync()
        for (province in provinces) {
            val result = fetchStations(province.code.toString()).getOrNull()
            val matched = result?.firstOrNull { it.contentId == id }
            if (matched != null) {
                return@withContext matched.applyState(state)
            }
        }

        null
    }

    suspend fun toggleFavorite(station: Station, isFavorite: Boolean) {
        val currentState = stationStateDao.getStateById(station.contentId) ?: StationState(contentId = station.contentId)
        stationStateDao.upsert(
            currentState.copy(
                isFavorite = isFavorite,
                lastPlayed = currentState.lastPlayed,
                playCount = currentState.playCount
            )
        )
        stationStateDao.deleteUnusedStates()
    }

    suspend fun updatePlayLasted(station: Station, timestamp: Long) {
        val currentState = stationStateDao.getStateById(station.contentId) ?: StationState(contentId = station.contentId)
        stationStateDao.upsert(
            currentState.copy(
                isFavorite = currentState.isFavorite,
                lastPlayed = timestamp,
                playCount = currentState.playCount + 1
            )
        )
    }

    suspend fun updatePlayLastedSync(station: Station, timestamp: Long) = withContext(Dispatchers.IO) {
        updatePlayLasted(station, timestamp)
    }

    suspend fun refreshProvinces(): Result<List<Province>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getProvinces().execute()
            if (response.isSuccessful) {
                val apiResponse = response.body()
                if (apiResponse != null && apiResponse.code == 0) {
                    val provinces = apiResponse.data ?: emptyList()
                    provinceDao.insertProvinces(provinces)
                    Result.success(provinces)
                } else {
                    Result.failure(Exception("API returned error: ${apiResponse?.message}"))
                }
            } else {
                Result.failure(Exception("HTTP error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshStations(provinceCode: String = "0"): Result<List<Station>> {
        return fetchStations(provinceCode)
    }

    fun getAllProvinces(): LiveData<List<Province>> = provinceDao.getAllProvinces()

    suspend fun getAllProvincesSync(): List<Province> = withContext(Dispatchers.IO) {
        provinceDao.getAllProvincesSync()
    }

    suspend fun getProvinceByCode(code: Int): Province? = provinceDao.getProvinceByCode(code)

    fun getAllSchedules(): LiveData<List<Schedule>> = scheduleDao.getAllSchedules()

    fun getEnabledSchedules(): LiveData<List<Schedule>> = scheduleDao.getEnabledSchedules()

    suspend fun getScheduleById(id: Long): Schedule? = scheduleDao.getScheduleById(id)

    suspend fun insertSchedule(schedule: Schedule): Long {
        return withContext(Dispatchers.IO) {
            scheduleDao.insertSchedule(schedule)
        }
    }

    suspend fun updateSchedule(schedule: Schedule) {
        withContext(Dispatchers.IO) {
            scheduleDao.updateSchedule(schedule)
        }
    }

    suspend fun deleteSchedule(schedule: Schedule) {
        withContext(Dispatchers.IO) {
            scheduleDao.deleteSchedule(schedule)
        }
    }

    suspend fun deleteScheduleById(id: Long) {
        withContext(Dispatchers.IO) {
            scheduleDao.deleteScheduleById(id)
        }
    }

    suspend fun toggleScheduleStatus(id: Long, isEnabled: Boolean) {
        withContext(Dispatchers.IO) {
            scheduleDao.updateScheduleStatus(id, isEnabled)
        }
    }

    private suspend fun mergeStates(stations: List<Station>): List<Station> {
        val stateMap = stationStateDao.getAllStates().associateBy { it.contentId }
        return stations.map { station ->
            station.applyState(stateMap[station.contentId])
        }
    }

    private suspend fun fetchStationByIdFromApi(contentId: String): Station? {
        val response = apiService.getStations(categoryId = "0", provinceCode = "0").execute()
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        if (body.code != 0) return null
        return body.data?.firstOrNull { it.contentId == contentId }
    }

    private fun Station.applyState(state: StationState?): Station {
        if (state == null) return this
        return copy(
            isFavorite = state.isFavorite,
            lastPlayed = state.lastPlayed,
            playCount = state.playCount
        )
    }
}
