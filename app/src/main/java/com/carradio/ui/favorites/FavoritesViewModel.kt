package com.carradio.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import kotlinx.coroutines.launch

/**
 * ViewModel for the favorites activity
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RadioRepository

    private val _favorites = MutableLiveData<List<Station>>()
    val favorites: LiveData<List<Station>> = _favorites

    init {
        val database = RadioDatabase.getDatabase(application)
        repository = RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            try {
                val favoriteStations = repository.getFavoriteStations()
                _favorites.postValue(favoriteStations)
            } catch (e: Exception) {
                // 可以添加错误处理
                _favorites.postValue(emptyList())
            }
        }
    }

    fun toggleFavorite(station: Station) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(station, !station.isFavorite)
                loadFavorites() // 重新加载收藏列表
            } catch (e: Exception) {
                // 可以添加错误处理
            }
        }
    }
}