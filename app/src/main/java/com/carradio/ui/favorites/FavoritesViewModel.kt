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
            repository.getFavoriteStations().observeForever { stations ->
                _favorites.postValue(stations)
            }
        }
    }
}