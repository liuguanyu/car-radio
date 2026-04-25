package com.carradio.ui.player

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.carradio.R
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import com.carradio.service.RadioService
import com.carradio.util.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 播放界面 - 收音机风格
 * 支持自动播放、方向键控制、音量调节、换台换省
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STATION = "station"
    }

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var stationImageView: ImageView
    private lateinit var stationTitleTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var programInfoTextView: TextView
    private lateinit var volumeLevelText: TextView

    private lateinit var btnPreviousStation: ImageButton
    private lateinit var btnNextStation: ImageButton
    private lateinit var btnPrevProvince: ImageButton
    private lateinit var btnNextProvince: ImageButton
    private lateinit var btnVolume: ImageButton
    private lateinit var btnFavorite: ImageButton

    private var radioService: RadioService? = null
    private var isBound = false
    private var currentStation: Station? = null
    private var stationList: List<Station> = emptyList()
    private var currentProvinceCode: Int = 0
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var repository: RadioRepository

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder?) {
            val binder = service as RadioService.LocalBinder
            radioService = binder.getService()
            isBound = true

            currentStation?.let { station ->
                radioService?.playStation(station)
                updatePlaybackStatus()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            radioService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        volumeControlStream = AudioManager.STREAM_MUSIC

        val station = intent.extras?.getSerializable(EXTRA_STATION) as? Station
        currentStation = station

        if (currentStation == null) {
            finish()
            return
        }

        val database = RadioDatabase.getDatabase(this)
        repository = RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )
        preferenceHelper = PreferenceHelper(this)

        initViews()
        setupClickListeners()
        displayStation(currentStation!!)

        bindToService()
        loadCurrentProvinceStations()
        startProgramScrolling()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        stationImageView = findViewById(R.id.stationImageView)
        stationTitleTextView = findViewById(R.id.stationTitleTextView)
        statusTextView = findViewById(R.id.statusTextView)
        programInfoTextView = findViewById(R.id.programInfoTextView)
        volumeLevelText = findViewById(R.id.volumeLevelText)

        btnPreviousStation = findViewById(R.id.btnPreviousStation)
        btnNextStation = findViewById(R.id.btnNextStation)
        btnPrevProvince = findViewById(R.id.btnPrevProvince)
        btnNextProvince = findViewById(R.id.btnNextProvince)
        btnVolume = findViewById(R.id.btnVolume)
        btnFavorite = findViewById(R.id.btnFavorite)

        updateVolumeDisplay()
    }

    private fun bindToService() {
        val intent = Intent(this, RadioService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, 0)
    }

    private fun loadCurrentProvinceStations() {
        CoroutineScope(Dispatchers.IO).launch {
            currentProvinceCode = preferenceHelper.getCurrentProvinceCode()
            val provinceCode = if (currentProvinceCode == 0) "0" else currentProvinceCode.toString()
            val result = repository.fetchStations(provinceCode)
            val list = result.getOrDefault(emptyList())
            stationList = list
            if (currentStation != null && list.none { it.contentId == currentStation?.contentId }) {
                currentStation = list.firstOrNull() ?: currentStation
            }
            runOnUiThread {
                currentStation?.let { displayStation(it) }
            }
        }
    }

    private fun displayStation(station: Station) {
        stationTitleTextView.text = station.title

        station.getDisplayImageUrl()?.let { imageUrl ->
            stationImageView.load(imageUrl) {
                placeholder(R.drawable.ic_radio_station)
                error(R.drawable.ic_radio_station)
            }
        } ?: stationImageView.setImageResource(R.drawable.ic_radio_station)

        updateFavoriteButton(station.isFavorite)
        programInfoTextView.text = station.getDisplaySubtitle()
        supportActionBar?.title = getString(R.string.radio_mode)
    }

    private fun updateFavoriteButton(isFavorite: Boolean) {
        if (isFavorite) {
            btnFavorite.setImageResource(R.drawable.ic_favorite)
            btnFavorite.contentDescription = getString(R.string.favorite_remove)
        } else {
            btnFavorite.setImageResource(R.drawable.ic_favorite_border)
            btnFavorite.contentDescription = getString(R.string.favorite_add)
        }
    }

    private fun updatePlaybackStatus() {
        val isPlaying = radioService?.isPlaying() == true
        statusTextView.visibility = if (isPlaying) View.VISIBLE else View.GONE
    }

    private fun updateVolumeDisplay() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = if (maxVolume > 0) (currentVolume * 100) / maxVolume else 0
        volumeLevelText.text = "$percent%"
    }

    private fun setupClickListeners() {
        btnPreviousStation.setOnClickListener {
            navigateToStation(-1)
        }

        btnNextStation.setOnClickListener {
            navigateToStation(1)
        }

        btnPrevProvince.setOnClickListener {
            navigateToProvince(-1)
        }

        btnNextProvince.setOnClickListener {
            navigateToProvince(1)
        }

        btnVolume.setOnClickListener {
            showVolumeDialog()
        }

        btnFavorite.setOnClickListener {
            toggleFavorite()
        }
    }

    private fun toggleFavorite() {
        currentStation?.let { station ->
            station.isFavorite = !station.isFavorite
            updateFavoriteButton(station.isFavorite)

            CoroutineScope(Dispatchers.IO).launch {
                repository.toggleFavorite(station, station.isFavorite)
            }
        }
    }

    private fun navigateToStation(direction: Int) {
        if (stationList.isEmpty()) return

        val currentId = currentStation?.contentId ?: return
        val currentIndex = stationList.indexOfFirst { it.contentId == currentId }

        val newIndex = when {
            currentIndex == -1 -> 0
            direction < 0 -> if (currentIndex > 0) currentIndex - 1 else stationList.size - 1
            direction > 0 -> if (currentIndex < stationList.size - 1) currentIndex + 1 else 0
            else -> currentIndex
        }

        val newStation = stationList[newIndex]
        currentStation = newStation
        displayStation(newStation)
        radioService?.playStation(newStation)

        CoroutineScope(Dispatchers.IO).launch {
            repository.updatePlayLasted(newStation, System.currentTimeMillis())
        }
    }

    private fun navigateToProvince(direction: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            val allProvinces = repository.getAllProvincesSync()
            if (allProvinces.isEmpty()) return@launch

            val currentIndex = allProvinces.indexOfFirst { it.code == currentProvinceCode }
            val safeCurrentIndex = if (currentIndex >= 0) currentIndex else 0
            val newIndex = if (direction < 0) {
                if (safeCurrentIndex > 0) safeCurrentIndex - 1 else allProvinces.size - 1
            } else {
                if (safeCurrentIndex < allProvinces.size - 1) safeCurrentIndex + 1 else 0
            }

            val newProvince = allProvinces[newIndex]
            currentProvinceCode = newProvince.code
            preferenceHelper.saveCurrentProvince(newProvince)

            val result = repository.fetchStations(newProvince.code.toString())
            val newStationList = result.getOrDefault(emptyList())
            if (newStationList.isNotEmpty()) {
                val firstStation = newStationList[0]
                stationList = newStationList
                currentStation = firstStation
                repository.updatePlayLasted(firstStation, System.currentTimeMillis())

                runOnUiThread {
                    displayStation(firstStation)
                    radioService?.playStation(firstStation)
                    Toast.makeText(
                        this@PlayerActivity,
                        getString(R.string.current_province, newProvince.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this@PlayerActivity,
                        getString(R.string.error_load_stations),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showVolumeDialog() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val dialogView = layoutInflater.inflate(R.layout.dialog_volume, null)
        val seekBar = dialogView.findViewById<android.widget.SeekBar>(R.id.volumeSeekBar)

        seekBar.max = maxVolume
        seekBar.progress = currentVolume

        AlertDialog.Builder(this)
            .setTitle(R.string.volume_control)
            .setView(dialogView)
            .setPositiveButton(R.string.ok, null)
            .show()

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                }
                val percent = if (maxVolume > 0) (progress * 100) / maxVolume else 0
                volumeLevelText.text = "$percent%"
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun startProgramScrolling() {
    }

    override fun onResume() {
        super.onResume()
        updatePlaybackStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                navigateToStation(-1)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                navigateToStation(1)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                navigateToProvince(-1)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                navigateToProvince(1)
                true
            }
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (radioService?.isPlaying() == true) {
                    radioService?.pausePlayback()
                    updatePlaybackStatus()
                } else {
                    radioService?.resumePlayback()
                    updatePlaybackStatus()
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}
