package com.carradio.data.api

import com.carradio.data.model.Station
import com.carradio.data.model.Province
import com.carradio.data.model.ApiResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API service for radio.cn API
 */
interface RadioApiService {

    companion object {
        const val BASE_URL = "https://ytmsout.radio.cn"
        const val STATION_LIST_PATH = "/web/appBroadcast/list"
        const val PROVINCE_LIST_PATH = "/web/appProvince/list/all"
    }

    /**
     * Fetch all stations
     * categoryId: "0" for all categories
     * provinceCode: "0" for all provinces
     */
    @GET(STATION_LIST_PATH)
    fun getStations(
        @Query("categoryId") categoryId: String = "0",
        @Query("provinceCode") provinceCode: String = "0"
    ): Call<ApiResponse<List<Station>>>

    /**
     * Fetch stations filtered by province
     */
    @GET(STATION_LIST_PATH)
    fun getStationsByProvince(
        @Query("categoryId") categoryId: String = "0",
        @Query("provinceCode") provinceCode: String
    ): Call<ApiResponse<List<Station>>>

    /**
     * Fetch all provinces
     */
    @GET(PROVINCE_LIST_PATH)
    fun getProvinces(): Call<ApiResponse<List<Province>>>
}