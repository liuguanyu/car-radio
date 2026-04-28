package com.carradio.ui.favorites

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carradio.R
import com.carradio.data.model.Station
import com.carradio.ui.main.StationAdapter
import com.carradio.ui.player.PlayerActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * Activity showing favorite radio stations
 */
class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: StationAdapter

    private val viewModel: FavoritesViewModel by viewModels()

    private lateinit var toolbar: MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupObservers()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
    }

    private fun setupToolbar() {
        toolbar.title = getString(R.string.title_activity_favorites)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = StationAdapter(
            onStationClick = { station ->
                playStation(station)
            },
            onFavoriteClick = { station ->
                viewModel.toggleFavorite(station)
            }
        )

        val gridLayoutManager = GridLayoutManager(this, 3)
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        adapter.setSpanCount(gridLayoutManager.spanCount)
    }

    private fun setupObservers() {
        viewModel.favorites.observe(this) { stations ->
            adapter.submitList(stations)
            showEmptyView(stations.isEmpty())
            if (stations.isNotEmpty() && adapter.selectedPosition == RecyclerView.NO_POSITION) {
                adapter.setSelectedPosition(0)
            }
        }
    }

    private fun showEmptyView(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun playStation(station: Station) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_STATION, station)
        }
        startActivity(intent)
    }


    override fun onResume() {
        super.onResume()
        viewModel.loadFavorites()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                adapter.moveToPrevious()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adapter.moveToNext()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                adapter.moveLeft()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                adapter.moveRight()
                true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                adapter.playSelectedStation()
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
}
