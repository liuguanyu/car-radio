package com.carradio.ui.schedule

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.carradio.R
import com.carradio.data.model.Province
import com.carradio.data.model.Schedule
import com.carradio.data.model.Station
import com.carradio.data.repository.RadioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for adding/editing a schedule
 */
class ScheduleDialogFragment(
    private val repository: RadioRepository,
    private val schedule: Schedule? = null,
    private val onScheduleSaved: (Schedule) -> Unit
) : DialogFragment() {

    private lateinit var provinceSpinner: Spinner
    private lateinit var stationSpinner: Spinner
    private lateinit var timeTextView: TextView
    private lateinit var enableSwitch: androidx.appcompat.widget.SwitchCompat

    private val dialogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var selectedProvince: Province? = null
    private var selectedStation: Station? = null
    
    private var provinces: List<Province> = emptyList()
    private var stations: List<Station> = emptyList()

    // 默认省份，比如“国家台”或者北京
    private var initialProvinceCode: String = "0"
    private var isFirstLoad = true

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_schedule, null, false)

        initViews(view)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setTitle(if (schedule == null) R.string.add_schedule else R.string.edit_schedule)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        setupClickListeners(alertDialog)
        return alertDialog
    }

    override fun onStart() {
        super.onStart()
        loadProvinces()
    }

    override fun onDestroy() {
        dialogScope.cancel()
        super.onDestroy()
    }

    private fun initViews(view: android.view.View) {
        provinceSpinner = view.findViewById(R.id.provinceSpinner)
        stationSpinner = view.findViewById(R.id.stationSpinner)
        timeTextView = view.findViewById(R.id.timeTextView)
        enableSwitch = view.findViewById(R.id.enableSwitch)

        schedule?.let { s ->
            selectedStation = Station(
                contentId = s.stationId,
                title = s.stationName,
                subtitle = "",
                imageUrl = null,
                playUrlLow = null
            )
            timeTextView.text = s.time
            enableSwitch.isChecked = s.isEnabled
        } ?: run {
            enableSwitch.isChecked = true
        }
    }

    private fun loadProvinces() {
        dialogScope.launch {
            try {
                val loadedProvinces = withContext(Dispatchers.IO) {
                    val list = repository.getAllProvincesSync().toMutableList()
                    // 添加一个"全部/国家台"选项
                    list.add(0, Province(code = 0, name = "国家台"))
                    list
                }
                provinces = loadedProvinces

                if (!isAdded) {
                    return@launch
                }

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    provinces.map { it.name }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                provinceSpinner.adapter = adapter

                // Try to find the province for the current schedule if editing
                if (schedule != null && isFirstLoad) {
                    // Fetch station details to find its province (This is a simplified approach, 
                    // ideally we should store provinceCode in Schedule)
                    val stationDetails = withContext(Dispatchers.IO) {
                        repository.getStationById(schedule.stationId)
                    }
                    if (stationDetails != null) {
                        // Note: If station doesn't contain province info, we might not be able to pre-select it accurately.
                        // Here we assume initialProvinceCode = "0" as fallback.
                        // If API provides a way to get province from station, use it here.
                    }
                }

                provinceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        selectedProvince = provinces[position]
                        loadStations(selectedProvince?.code.toString())
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedProvince = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadStations(provinceCode: String) {
        dialogScope.launch {
            try {
                val loadedStations = withContext(Dispatchers.IO) {
                    repository.refreshStations(provinceCode).getOrDefault(emptyList())
                }
                stations = loadedStations

                if (!isAdded) {
                    return@launch
                }

                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    stations.map { it.title }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                stationSpinner.adapter = adapter

                if (isFirstLoad && schedule != null) {
                    val position = stations.indexOfFirst { it.contentId == schedule.stationId }
                    if (position >= 0) {
                        stationSpinner.setSelection(position)
                        selectedStation = stations[position]
                    }
                    isFirstLoad = false
                } else if (stations.isNotEmpty()) {
                    stationSpinner.setSelection(0)
                    selectedStation = stations[0]
                }

                stationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                        if (position >= 0 && position < stations.size) {
                            selectedStation = stations[position]
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        selectedStation = null
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun setupClickListeners(alertDialog: AlertDialog) {
        timeTextView.setOnClickListener {
            showTimePicker()
        }

        alertDialog.setOnShowListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateInput()) {
                    saveSchedule()
                }
            }
        }
    }

    private fun showTimePicker() {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        val timePicker = android.app.TimePickerDialog(
            requireContext(),
            { _, selectedHour, selectedMinute ->
                val time = String.format("%02d:%02d", selectedHour, selectedMinute)
                timeTextView.text = time
            },
            hour,
            minute,
            true
        )
        timePicker.show()
    }

    private fun validateInput(): Boolean {
        when {
            selectedStation == null -> {
                return false
            }
            timeTextView.text.toString().isEmpty() -> {
                timeTextView.error = getString(R.string.error)
                return false
            }
            else -> return true
        }
    }

    private fun saveSchedule() {
        val station = selectedStation ?: return
        val time = timeTextView.text.toString()

        val newSchedule = Schedule(
            id = schedule?.id ?: 0,
            stationId = station.contentId,
            stationName = station.title,
            provinceCode = selectedProvince?.code ?: 0,
            imageUrl = station.imageUrl,
            time = time,
            isEnabled = enableSwitch.isChecked,
            createdAt = schedule?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        onScheduleSaved(newSchedule)
        dialog?.dismiss()
    }
}
