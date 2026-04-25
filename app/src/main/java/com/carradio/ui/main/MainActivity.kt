package com.carradio.ui.main

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.carradio.R
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import com.carradio.ui.favorites.FavoritesActivity
import com.carradio.ui.player.PlayerActivity
import com.carradio.ui.province.ProvinceAdapter
import com.carradio.ui.schedule.ScheduleActivity
import com.carradio.util.PreferenceHelper
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

/**
 * Main activity showing list of radio stations
 */
class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var provinceRecyclerView: RecyclerView
    private lateinit var stationRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView

    private lateinit var provinceAdapter: ProvinceAdapter
    private lateinit var stationAdapter: StationAdapter

    private lateinit var repository: RadioRepository
    private lateinit var preferenceHelper: PreferenceHelper

    private val viewModel: MainViewModel by viewModels()

    private var playFabBackground: ImageView? = null

    // Broadcast receiver to listen for station changes
    private val stationChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STATION_CHANGED -> {
                    val station = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent?.getSerializableExtra("station", Station::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent?.getSerializableExtra("station") as? Station
                    }
                    playFabBackground?.let { updatePlayFabBackground(station, it) }
                    
                    // Update selected station in adapter
                    station?.let { newStation ->
                        val currentStations = viewModel.stations.value ?: emptyList()
                        val index = currentStations.indexOfFirst { it.contentId == newStation.contentId }
                        if (index != -1 && index != stationAdapter.selectedPosition) {
                            stationAdapter.setSelectedPosition(index)
                        }
                        
                        // Also reload stations to update the "playing" status indicator
                        val currentCode = viewModel.currentProvince.value?.code?.toString() ?: "0"
                        viewModel.loadStations(currentCode)
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LAST_PLAYED_STATION = "last_played_station"
        const val ACTION_STATION_CHANGED = "com.carradio.STATION_CHANGED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerViews()
        setupPlayFab() // Move this after setupRecyclerViews so stationAdapter is initialized
        setupObservers()
        checkLastPlayedStation()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Register receiver to listen for station changes
        val filter = IntentFilter(ACTION_STATION_CHANGED)
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            stationChangeReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver
        try {
            unregisterReceiver(stationChangeReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        provinceRecyclerView = findViewById(R.id.provinceRecyclerView)
        stationRecyclerView = findViewById(R.id.stationRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)

        setSupportActionBar(toolbar)
        supportActionBar?.setTitle(R.string.title_activity_main)

        val database = RadioDatabase.getDatabase(this)
        repository = RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )

        preferenceHelper = PreferenceHelper(this)
    }

    private fun setupRecyclerViews() {
        provinceAdapter = ProvinceAdapter { position ->
            val provinces = viewModel.provinces.value
            if (provinces != null && position in provinces.indices) {
                val province = provinces[position]
                viewModel.setProvince(province.code)
                preferenceHelper.saveCurrentProvince(province)
            }
        }
        provinceAdapter.setSpanCount(1)
        provinceRecyclerView.layoutManager = LinearLayoutManager(this)
        provinceRecyclerView.adapter = provinceAdapter
        provinceRecyclerView.setHasFixedSize(true)

        stationAdapter = StationAdapter { station ->
            playStation(station)
        }
        val gridLayoutManager = GridLayoutManager(this, 3)
        stationRecyclerView.layoutManager = gridLayoutManager
        stationRecyclerView.adapter = stationAdapter
        stationRecyclerView.setHasFixedSize(true)
        stationAdapter.setSpanCount(gridLayoutManager.spanCount)
    }

    private fun setupObservers() {
        viewModel.provinces.observe(this) { provinces ->
            provinceAdapter.submitList(provinces)

            val savedProvinceCode = preferenceHelper.getCurrentProvinceCode()
            val indexToSelect = if (savedProvinceCode != 0) {
                provinces.indexOfFirst { it.code == savedProvinceCode }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }

            if (provinces.isNotEmpty()) {
                provinceAdapter.setSelectedPosition(indexToSelect)
                viewModel.setProvince(provinces[indexToSelect].code)
            }
        }

        viewModel.stations.observe(this) { stations ->
            stationAdapter.submitList(stations)
            showEmptyView(stations.isEmpty())
            if (stations.isNotEmpty() && stationAdapter.selectedPosition == RecyclerView.NO_POSITION) {
                stationAdapter.setSelectedPosition(0)
            }
            // 当电台列表变化时，更新playFab背景
            val playFabBackground = findViewById<ImageView>(R.id.playFabBackground)
            updatePlayFabBackground(stationAdapter.selectedStation, playFabBackground)
        }

        viewModel.loading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
            }
        }

        viewModel.currentProvince.observe(this) { province ->
            if (province != null && province.code != 0) {
                supportActionBar?.title = getString(R.string.current_province, province.name)
            } else {
                supportActionBar?.setTitle(R.string.title_activity_main)
            }
        }
    }

    private fun showEmptyView(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        stationRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun loadData() {
    }

    private fun checkLastPlayedStation() {
        val station = intent.extras?.getSerializable(EXTRA_LAST_PLAYED_STATION) as? Station
        station?.let {
            playStation(it)
        }
    }

    private fun playStation(station: Station) {
        lifecycleScope.launch {
            repository.updatePlayLasted(station, System.currentTimeMillis())
        }

        // Update the play fab button immediately
        playFabBackground?.let { updatePlayFabBackground(station, it) }

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STATION, station)
        }
        startActivity(intent)
    }

    private fun setupPlayFab() {
        val playFabContainer = findViewById<FrameLayout>(R.id.playFabContainer)
        playFabBackground = findViewById<ImageView>(R.id.playFabBackground)
        val playFabIcon = findViewById<ImageView>(R.id.playFabIcon)
        val playFabOverlay = findViewById<View>(R.id.playFabOverlay)

        // 设置播放按钮点击事件
        playFabContainer.setOnClickListener {
            val selectedStation = stationAdapter.selectedStation
            if (selectedStation != null) {
                playStation(selectedStation)
            } else if (stationAdapter.itemCount > 0) {
                val firstStation = stationAdapter.getStation(0)
                if (firstStation != null) {
                    playStation(firstStation)
                }
            }
        }

        // 监听选中电台变化，更新playFab背景
        // Add null safety check
        stationAdapter.setOnStationSelectedListener { station ->
            playFabBackground?.let { updatePlayFabBackground(station, it) }
        }

        // 初始状态：如果已有选中电台，更新背景
        val initialStation = stationAdapter.selectedStation
        if (initialStation != null) {
            playFabBackground?.let { updatePlayFabBackground(initialStation, it) }
        }
    }

    private fun updatePlayFabBackground(station: Station?, imageView: ImageView) {
        if (station != null) {
            val imageUrl = station.getDisplayImageUrl()
            if (!imageUrl.isNullOrEmpty()) {
                imageView.load(imageUrl) {
                    placeholder(R.drawable.ic_radio_station)
                    error(R.drawable.ic_radio_station)
                    transformations(CircleCropTransformation())
                }
            } else {
                imageView.setImageResource(R.drawable.ic_radio_station)
            }
        } else {
            imageView.setImageResource(R.drawable.ic_radio_station)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.findItem(R.id.action_province)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorites -> {
                startActivity(Intent(this, FavoritesActivity::class.java))
                true
            }
            R.id.action_schedule -> {
                startActivity(Intent(this, ScheduleActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                val currentCode = viewModel.currentProvince.value?.code?.toString() ?: "0"
                viewModel.loadStations(currentCode)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val isProvinceFocused = provinceRecyclerView.hasFocus() || provinceRecyclerView.findFocus() != null

        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                if (isProvinceFocused) {
                    provinceAdapter.moveToPrevious()
                } else {
                    stationAdapter.moveToPrevious()
                }
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isProvinceFocused) {
                    provinceAdapter.moveToNext()
                } else {
                    stationAdapter.moveToNext()
                }
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isProvinceFocused) {
                    provinceRecyclerView.requestFocus()
                }
                true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isProvinceFocused) {
                    stationRecyclerView.requestFocus()
                } else {
                    stationAdapter.moveRight()
                }
                true
            }
            android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (isProvinceFocused) {
                    val position = provinceAdapter.selectedPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val provinces = viewModel.provinces.value
                        if (provinces != null && position in provinces.indices) {
                            val province = provinces[position]
                            viewModel.setProvince(province.code)
                            preferenceHelper.saveCurrentProvince(province)
                        }
                    }
                } else {
                    stationAdapter.playSelectedStation()
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}
