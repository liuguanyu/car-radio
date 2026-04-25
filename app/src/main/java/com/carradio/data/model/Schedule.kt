package com.carradio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

/**
 * Data class representing a scheduled station switch
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val stationId: String,
    val stationName: String,
    val provinceCode: Int = 0,
    val imageUrl: String? = null,

    // Time in HH:mm format (24-hour)
    val time: String,

    // Whether this schedule is enabled
    val isEnabled: Boolean = true,

    // Timestamp when created
    val createdAt: Long = System.currentTimeMillis(),

    // Timestamp when last updated
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable