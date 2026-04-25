package com.carradio.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.carradio.data.model.Station

/**
 * Data Access Object for Station entity
 */
@Dao
interface StationDao {

    @Query("SELECT * FROM stations ORDER BY title ASC")
    fun getAllStations(): LiveData<List<Station>>

    @Query("SELECT * FROM stations WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteStations(): LiveData<List<Station>>

    @Query("SELECT * FROM stations WHERE contentId = :id")
    suspend fun getStationById(id: String): Station?

    @Query("SELECT * FROM stations ORDER BY lastPlayed DESC LIMIT 1")
    suspend fun getLastPlayedStation(): Station?

    @Query("SELECT * FROM stations WHERE code = :provinceCode ORDER BY title ASC")
    fun getStationsByProvince(provinceCode: Int): LiveData<List<Station>>

    @Query("SELECT * FROM stations WHERE contentId = :id")
    fun getStationByIdSync(id: String): Station?

    @Query("SELECT * FROM stations WHERE code = :provinceCode ORDER BY title ASC")
    fun getStationsByProvinceSync(provinceCode: Int): List<Station>

    @Query("SELECT * FROM stations ORDER BY title ASC")
    fun getAllStationsSync(): List<Station>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<Station>)

    @Update
    suspend fun updateStation(station: Station)

    @Query("UPDATE stations SET isFavorite = :isFavorite WHERE contentId = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE stations SET lastPlayed = :timestamp, playCount = playCount + 1 WHERE contentId = :id")
    suspend fun updatePlayTimestamp(id: String, timestamp: Long)

    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()

    @Delete
    suspend fun deleteStation(station: Station)

    @Query("SELECT COUNT(*) FROM stations WHERE isFavorite = 1")
    fun getFavoriteCount(): LiveData<Int>
}