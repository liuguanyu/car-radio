package com.carradio.data.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.carradio.data.model.StationState

/**
 * 电台本地状态访问层。
 */
@Dao
interface StationStateDao {

    @Query("SELECT * FROM station_states")
    suspend fun getAllStates(): List<StationState>

    @Query("SELECT * FROM station_states WHERE contentId = :id LIMIT 1")
    suspend fun getStateById(id: String): StationState?

    @Query("SELECT * FROM station_states WHERE isFavorite = 1 ORDER BY lastPlayed DESC, contentId ASC")
    fun getFavoriteStates(): LiveData<List<StationState>>

    @Query("SELECT * FROM station_states WHERE isFavorite = 1 ORDER BY lastPlayed DESC, contentId ASC")
    suspend fun getFavoriteStatesSync(): List<StationState>

    @Query("SELECT * FROM station_states ORDER BY lastPlayed DESC LIMIT 1")
    suspend fun getLastPlayedState(): StationState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: StationState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<StationState>)

    @Query("UPDATE station_states SET isFavorite = :isFavorite WHERE contentId = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Query("UPDATE station_states SET lastPlayed = :timestamp, playCount = playCount + 1 WHERE contentId = :id")
    suspend fun updatePlayTimestamp(id: String, timestamp: Long)

    @Query("DELETE FROM station_states WHERE isFavorite = 0 AND lastPlayed = 0")
    suspend fun deleteUnusedStates()
}