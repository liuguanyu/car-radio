package com.carradio.ui.schedule

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.carradio.R
import com.carradio.data.db.RadioDatabase
import com.carradio.data.model.Schedule
import com.carradio.data.repository.RadioRepository
import com.google.android.material.appbar.MaterialToolbar

/**
 * Activity showing and managing station schedules
 */
class ScheduleActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var adapter: ScheduleAdapter
    private lateinit var repository: RadioRepository

    private val viewModel: ScheduleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_activity_schedule)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        initViews()
        setupRecyclerView()
        setupObservers()

        val database = RadioDatabase.getDatabase(this)
        repository = RadioRepository(
            database.stationStateDao(),
            database.provinceDao(),
            database.scheduleDao()
        )

        viewModel.loadSchedules()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        findViewById<View>(R.id.addScheduleButton).setOnClickListener {
            showAddScheduleDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            onScheduleClick = { schedule -> showEditScheduleDialog(schedule) },
            onToggleEnabled = { schedule, isEnabled ->
                viewModel.toggleScheduleStatus(schedule.id, isEnabled)
            },
            onDelete = { schedule ->
                viewModel.deleteSchedule(schedule)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScheduleActivity)
            adapter = this@ScheduleActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        viewModel.schedules.observe(this) { schedules ->
            adapter.submitList(schedules)
            showEmptyView(schedules.isEmpty())
        }
    }

    private fun showEmptyView(show: Boolean) {
        emptyView.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showAddScheduleDialog() {
        ScheduleDialogFragment(
            repository = repository,
            onScheduleSaved = { schedule ->
                viewModel.insertSchedule(schedule)
            }
        ).show(supportFragmentManager, "AddSchedule")
    }

    private fun showEditScheduleDialog(schedule: Schedule) {
        ScheduleDialogFragment(
            repository = repository,
            schedule = schedule,
            onScheduleSaved = { updatedSchedule ->
                viewModel.updateSchedule(updatedSchedule)
            }
        ).show(supportFragmentManager, "EditSchedule")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
