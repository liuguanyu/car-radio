package com.carradio.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.carradio.data.model.Province

/**
 * Data Access Object for Province entity
 */
@Dao
interface ProvinceDao {

    @Query("SELECT * FROM provinces ORDER BY name ASC")
    fun getAllProvinces(): LiveData<List<Province>>

    @Query("SELECT * FROM provinces ORDER BY name ASC")
    fun getAllProvincesSync(): List<Province>

    @Query("SELECT * FROM provinces WHERE code = :code")
    suspend fun getProvinceByCode(code: Int): Province?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvinces(provinces: List<Province>)

    @Query("DELETE FROM provinces")
    suspend fun deleteAllProvinces()

    @Delete
    suspend fun deleteProvince(province: Province)
}