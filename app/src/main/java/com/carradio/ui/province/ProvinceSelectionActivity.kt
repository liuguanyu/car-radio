package com.carradio.ui.province

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carradio.R
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Province
import com.carradio.data.repository.RadioRepository
import com.carradio.ui.player.PlayerActivity
import kotlinx.coroutines.launch

/**
 * 省切换界面
 * 显示所有省份的宫格布局，支持方向键和点击选择
 */
class ProvinceSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ProvinceAdapter
    private lateinit var repository: RadioRepository
    private lateinit var emptyView: TextView

    private var provinces: List<Province> = emptyList()
    private var selectedPosition = 0
    private var currentProvinceCode: Int = 0

    companion object {
        const val EXTRA_SELECTED_PROVINCE_CODE = "selected_province_code"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_province_selection)

        initViews()
        setupRecyclerView()
        loadProvinces()

        currentProvinceCode = intent.getIntExtra(EXTRA_SELECTED_PROVINCE_CODE, 0)
        updateSelectedPosition()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
    }

    private fun setupRecyclerView() {
        adapter = ProvinceAdapter { position ->
            selectProvince(position)
        }

        val gridLayoutManager = GridLayoutManager(this, 2)
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        adapter.setSpanCount(gridLayoutManager.spanCount)
    }

    private fun loadProvinces() {
        lifecycleScope.launch {
            val database = RadioDatabase.getDatabase(this@ProvinceSelectionActivity)
            repository = RadioRepository(
                database.stationStateDao(),
                database.provinceDao(),
                database.scheduleDao()
            )

            provinces = repository.getAllProvincesSync()
            adapter.submitList(provinces)
            showEmptyView(provinces.isEmpty())
            updateSelectedPosition()
        }
    }

    private fun showEmptyView(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateSelectedPosition() {
        selectedPosition = provinces.indexOfFirst { it.code == currentProvinceCode }
        if (selectedPosition == -1) {
            selectedPosition = if (provinces.isEmpty()) RecyclerView.NO_POSITION else 0
        }
        adapter.setSelectedPosition(selectedPosition)
        if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition < adapter.itemCount) {
            recyclerView.scrollToPosition(selectedPosition)
        }
    }

    private fun selectProvince(position: Int) {
        if (position !in provinces.indices) return

        val province = provinces[position]
        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_PROVINCE_CODE, province.code)
        }
        setResult(RESULT_OK, resultIntent)

        lifecycleScope.launch {
            val firstStation = repository.fetchFirstStationByProvince(province.code)
            if (firstStation != null) {
                val playIntent = Intent(this@ProvinceSelectionActivity, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_STATION, firstStation as java.io.Serializable)
                }
                startActivity(playIntent)
            } else {
                Toast.makeText(
                    this@ProvinceSelectionActivity,
                    getString(R.string.error_load_stations),
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (adapter.itemCount == 0) return super.onKeyUp(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                adapter.moveToPrevious()
                selectedPosition = adapter.selectedPosition
                updateAdapterSelection()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                adapter.moveToNext()
                selectedPosition = adapter.selectedPosition
                updateAdapterSelection()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                adapter.moveLeft()
                selectedPosition = adapter.selectedPosition
                updateAdapterSelection()
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                adapter.moveRight()
                selectedPosition = adapter.selectedPosition
                updateAdapterSelection()
                true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                selectProvince(selectedPosition)
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun updateAdapterSelection() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            recyclerView.scrollToPosition(selectedPosition)
        }
    }
}
