package com.carradio.ui.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.carradio.R
import com.carradio.data.model.Schedule

/**
 * RecyclerView adapter for displaying schedules
 */
class ScheduleAdapter(
    private val onScheduleClick: (Schedule) -> Unit,
    private val onToggleEnabled: (Schedule, Boolean) -> Unit,
    private val onDelete: (Schedule) -> Unit
) : ListAdapter<Schedule, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val schedule = getItem(position)
        holder.bind(schedule)

        holder.itemView.setOnClickListener {
            onScheduleClick(schedule)
        }
    }

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stationImageView: ImageView = itemView.findViewById(R.id.stationImageView)
        private val stationNameTextView: TextView = itemView.findViewById(R.id.stationNameTextView)
        private val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        private val statusTextView: TextView = itemView.findViewById(R.id.statusTextView)
        private val enableSwitch: Switch = itemView.findViewById(R.id.enableSwitch)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(schedule: Schedule) {
            stationNameTextView.text = schedule.stationName
            timeTextView.text = getTimeDisplay(schedule.time)
            statusTextView.text = if (schedule.isEnabled) getString(R.string.enabled) else getString(R.string.disabled)

            // Load station icon with circle crop
            if (!schedule.imageUrl.isNullOrEmpty()) {
                stationImageView.load(schedule.imageUrl) {
                    placeholder(R.drawable.ic_radio_station)
                    error(R.drawable.ic_radio_station)
                    transformations(CircleCropTransformation())
                }
            } else {
                stationImageView.load(R.drawable.ic_radio_station) {
                    transformations(CircleCropTransformation())
                }
            }

            enableSwitch.isChecked = schedule.isEnabled
            enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggleEnabled(schedule, isChecked)
            }

            deleteButton.setOnClickListener {
                onDelete(schedule)
            }
        }

        private fun getString(resId: Int): String {
            return itemView.context.getString(resId)
        }

        private fun getTimeDisplay(time: String): String {
            // Format HH:mm to a more readable format if needed
            return time
        }
    }

    class ScheduleDiffCallback : DiffUtil.ItemCallback<Schedule>() {
        override fun areItemsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Schedule, newItem: Schedule): Boolean {
            return oldItem == newItem
        }
    }
}
