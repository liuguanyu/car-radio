package com.carradio.ui.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.carradio.R
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import com.carradio.service.RadioService
import com.carradio.ui.main.MainActivity
import com.carradio.ui.schedule.ScheduleActivity
import com.carradio.util.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 播放界面 - 收音机风格
 * 支持自动播放、方向键控制、音量调节、换台换省
 * 旋钮UI：换台旋钮、换省旋钮、音量旋钮（一行排列，等大）
 * 方向盘按键：左右键换台，上下键调音量
 * 右上角：收藏按钮 + 播放计划按钮
 * 音量旋钮：短按静音/取消静音，长按弹出纵向音量条
 * 静音使用ExoPlayer volume，不影响其他应用
 */
class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STATION = "station"
        private const val VOLUME_STEPS = 20  // Volume granularity 0..20
        private const val LONG_PRESS_THRESHOLD_MS = 500L
    }

    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    private lateinit var stationImageView: ImageView
    private lateinit var stationTitleTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var programInfoTextView: TextView
    private lateinit var volumeLevelText: TextView

    private lateinit var btnChangeStation: ImageButton
    private lateinit var btnChangeProvince: ImageButton
    private lateinit var btnVolume: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnSchedule: ImageButton

    private var radioService: RadioService? = null
    private var isBound = false
    private var currentStation: Station? = null
    private var stationList: List<Station> = emptyList()
    private var currentProvinceCode: Int = 0
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var repository: RadioRepository

    // Track station/province navigation direction for alternating behavior
    private var stationDirection = 1  // 1 = next, -1 = previous; alternates on each click
    private var provinceDirection = 1 // 1 = next, -1 = previous; alternates on each click

    // Mute state: uses ExoPlayer volume, not system stream volume
    private var isMuted = false
    private var volumeBeforeMute = 1.0f  // ExoPlayer volume 0.0..1.0

    // Long press detection for volume knob
    private var isLongPress = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPress = true
        showVerticalVolumePopup()
    }

    // Volume popup window
    private var volumePopup: PopupWindow? = null

    // Broadcast receiver to listen for station changes from other components
    private val stationChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MainActivity.ACTION_STATION_CHANGED -> {
                    val station = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getSerializableExtra("station", Station::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getSerializableExtra("station") as? Station
                    }
                    station?.let { newStation ->
                        if (newStation.contentId != currentStation?.contentId) {
                            currentStation = newStation
                            displayStation(newStation)
                            updatePlaybackStatus()
                        }
                    }
                }
            }
        }
    }

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

    override fun onResume() {
        super.onResume()
        updatePlaybackStatus()
        val filter = IntentFilter(MainActivity.ACTION_STATION_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            stationChangeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        syncCurrentStationFromService()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(stationChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val station = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_STATION, Station::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_STATION) as? Station
        }
        station?.let { newStation ->
            if (newStation.contentId != currentStation?.contentId) {
                currentStation = newStation
                displayStation(newStation)
                radioService?.playStation(newStation)
                updatePlaybackStatus()
            }
        }
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

        btnChangeStation = findViewById(R.id.btnChangeStation)
        btnChangeProvince = findViewById(R.id.btnChangeProvince)
        btnVolume = findViewById(R.id.btnVolume)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnSchedule = findViewById(R.id.btnSchedule)

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

    // ---- Volume Control (ExoPlayer-based, does not affect other apps) ----

    /**
     * Get current ExoPlayer volume as 0..VOLUME_STEPS integer
     */
    private fun getPlayerVolumeStep(): Int {
        val playerVol = radioService?.getPlayerVolume() ?: 1.0f
        return (playerVol * VOLUME_STEPS).toInt().coerceIn(0, VOLUME_STEPS)
    }

    /**
     * Set ExoPlayer volume from 0..VOLUME_STEPS integer
     */
    private fun setPlayerVolumeStep(step: Int) {
        val safeStep = step.coerceIn(0, VOLUME_STEPS)
        val volume = safeStep.toFloat() / VOLUME_STEPS
        radioService?.setPlayerVolume(volume)
        // If user adjusts volume while muted, unmute
        if (isMuted && safeStep > 0) {
            isMuted = false
        }
        updateVolumeDisplay()
    }

    private fun updateVolumeDisplay() {
        if (isMuted) {
            volumeLevelText.text = "MUTE"
            return
        }
        val step = getPlayerVolumeStep()
        val percent = (step * 100) / VOLUME_STEPS
        volumeLevelText.text = "$percent%"
    }

    private fun toggleMute() {
        if (isMuted) {
            // Unmute: restore previous volume
            isMuted = false
            radioService?.setPlayerVolume(volumeBeforeMute)
        } else {
            // Mute: save current volume and set to 0
            volumeBeforeMute = radioService?.getPlayerVolume() ?: 1.0f
            isMuted = true
            radioService?.setPlayerVolume(0f)
        }
        updateVolumeDisplay()
        val msg = if (isMuted) "已静音" else "已取消静音"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun adjustVolume(direction: Int) {
        if (isMuted) {
            // If muted and adjusting up, unmute first
            if (direction > 0) {
                isMuted = false
                radioService?.setPlayerVolume(volumeBeforeMute)
                updateVolumeDisplay()
            }
            return
        }
        val currentStep = getPlayerVolumeStep()
        setPlayerVolumeStep(currentStep + direction)
    }

    /**
     * Show vertical volume popup anchored below the volume knob
     */
    private fun showVerticalVolumePopup() {
        val popupView = LayoutInflater.from(this).inflate(R.layout.dialog_volume_vertical, null)
        val seekBar = popupView.findViewById<SeekBar>(R.id.volumeSeekBar)
        val percentText = popupView.findViewById<TextView>(R.id.volumePercentText)

        // If currently muted, unmute for adjustment
        if (isMuted) {
            isMuted = false
            radioService?.setPlayerVolume(volumeBeforeMute)
        }

        seekBar.max = VOLUME_STEPS
        seekBar.progress = getPlayerVolumeStep()

        val currentStep = getPlayerVolumeStep()
        val percent = (currentStep * 100) / VOLUME_STEPS
        percentText.text = "$percent%"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setPlayerVolumeStep(progress)
                }
                val pct = (progress * 100) / VOLUME_STEPS
                percentText.text = "$pct%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        volumePopup?.dismiss()

        // Measure the popup view first so we can calculate correct position
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val measuredHeight = popupView.measuredHeight

        volumePopup = PopupWindow(popupView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 16f
            // Show to the right of the volume knob, vertically centered relative to the button
            val btnLocation = IntArray(2)
            btnVolume.getLocationOnScreen(btnLocation)
            val btnCenterY = btnLocation[1] + btnVolume.height / 2
            val yOffset = btnCenterY - measuredHeight / 2 - btnLocation[1]
            showAsDropDown(btnVolume, btnVolume.height / 2, yOffset)
        }
    }

    // ---- Click Listeners ----

    private fun setupClickListeners() {
        // Station knob: click to cycle to next station (alternating direction)
        btnChangeStation.setOnClickListener {
            navigateToStation(stationDirection)
            stationDirection = -stationDirection
        }

        // Province knob: click to cycle to next province (alternating direction)
        btnChangeProvince.setOnClickListener {
            navigateToProvince(provinceDirection)
            provinceDirection = -provinceDirection
        }

        // Volume knob: short press = toggle mute, long press = show vertical volume slider
        btnVolume.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isLongPress = false
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD_MS)
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (!isLongPress) {
                        // Short press: toggle mute
                        toggleMute()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    true
                }
                else -> false
            }
        }

        // Favorite button: toggle favorite
        btnFavorite.setOnClickListener {
            toggleFavorite()
        }

        // Schedule button: open schedule activity
        btnSchedule.setOnClickListener {
            val intent = Intent(this, ScheduleActivity::class.java)
            startActivity(intent)
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

    private fun startProgramScrolling() {
    }

    private fun syncCurrentStationFromService() {
        if (!isBound || radioService == null) return
        val serviceStation = radioService?.getCurrentStation()
        if (serviceStation != null && serviceStation.contentId != currentStation?.contentId) {
            currentStation = serviceStation
            displayStation(serviceStation)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        longPressHandler.removeCallbacks(longPressRunnable)
        volumePopup?.dismiss()
        try {
            unregisterReceiver(stationChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    /**
     * Steering wheel key bindings:
     * - DPAD_LEFT: change station (previous)
     * - DPAD_RIGHT: change station (next)
     * - DPAD_UP: volume up (ExoPlayer volume, not system)
     * - DPAD_DOWN: volume down (ExoPlayer volume, not system)
     * - ENTER/DPAD_CENTER: play/pause toggle
     */
    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                navigateToStation(-1)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                navigateToStation(1)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                adjustVolume(1)
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                adjustVolume(-1)
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
