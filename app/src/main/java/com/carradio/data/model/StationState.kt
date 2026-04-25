package com.carradio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地电台状态，仅保存用户侧数据。
 */
@Entity(tableName = "station_states")
data class StationState(
    @PrimaryKey
    val contentId: String,
    val isFavorite: Boolean = false,
    val lastPlayed: Long = 0,
    val playCount: Int = 0
)