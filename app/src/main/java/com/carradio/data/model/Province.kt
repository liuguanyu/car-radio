package com.carradio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Data class representing a province for regional stations
 */
@Entity(tableName = "provinces")
data class Province(
    @PrimaryKey
    @SerializedName("provinceCode")
    val code: Int,

    @SerializedName("provinceName")
    val name: String
) : Serializable {

    companion object {
        const val ALL_PROVINCES_CODE = 0
        const val ALL_PROVINCES_NAME = "All Provinces"
    }
}