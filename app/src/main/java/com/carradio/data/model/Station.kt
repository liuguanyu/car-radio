package com.carradio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Data class representing a radio station
 */
@Entity(tableName = "stations")
data class Station(
    @PrimaryKey
    @SerializedName("contentId")
    val contentId: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("subtitle")
    val subtitle: String? = null,

    @SerializedName("image")
    val imageUrl: String? = null,

    @SerializedName("playUrlLow")
    val playUrlLow: String? = null,

    @SerializedName("mp3PlayUrlLow")
    val mp3PlayUrlLow: String? = null,

    @SerializedName("playUrlMulti")
    val playUrlMulti: String? = null,

    var isFavorite: Boolean = false,
    var lastPlayed: Long = 0,
    var playCount: Int = 0,
    var code: Int? = null
) : Serializable {

    /**
     * Get the best available playback URL
     */
    fun getBestPlayUrl(): String? {
        return when {
            !mp3PlayUrlLow.isNullOrEmpty() -> mp3PlayUrlLow
            !playUrlLow.isNullOrEmpty() -> playUrlLow
            !playUrlMulti.isNullOrEmpty() -> playUrlMulti
            else -> null
        }
    }

    /**
     * Get a displayable subtitle or default text
     */
    fun getDisplaySubtitle(): String {
        return subtitle ?: "Radio Station"
    }

    /**
     * Get a displayable image URL or default
     */
    fun getDisplayImageUrl(): String? {
        return imageUrl
    }

    companion object {
        const val DEFAULT_IMAGE = "android.resource://com.carradio/drawable/ic_radio_station"
    }
}

/**
 * API response wrapper
 */
data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: T
)
