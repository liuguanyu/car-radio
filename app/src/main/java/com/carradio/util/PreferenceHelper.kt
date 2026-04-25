package com.carradio.util

import android.content.Context
import com.carradio.data.model.Province

/**
 * 偏好设置帮助类
 * 用于存储和检索应用全局状态，如当前省份
 */
class PreferenceHelper(context: Context) {

    companion object {
        private const val PREFS_NAME = "car_radio_prefs"
        private const val KEY_CURRENT_PROVINCE_CODE = "current_province_code"
        private const val KEY_CURRENT_PROVINCE_NAME = "current_province_name"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCurrentProvince(province: Province?) {
        prefs.edit().apply {
            if (province != null) {
                putInt(KEY_CURRENT_PROVINCE_CODE, province.code)
                putString(KEY_CURRENT_PROVINCE_NAME, province.name)
            } else {
                remove(KEY_CURRENT_PROVINCE_CODE)
                remove(KEY_CURRENT_PROVINCE_NAME)
            }
            apply()
        }
    }

    fun getCurrentProvinceCode(): Int {
        return prefs.getInt(KEY_CURRENT_PROVINCE_CODE, 0)
    }

    fun getCurrentProvinceName(): String? {
        return prefs.getString(KEY_CURRENT_PROVINCE_NAME, null)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
