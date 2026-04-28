package com.carradio.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.carradio.R
import com.carradio.data.model.Station

/**
 * RecyclerView adapter for displaying radio stations
 */
class StationAdapter(
    private val onStationClick: (Station) -> Unit,
    private val onFavoriteClick: ((Station) -> Unit)? = null
) : ListAdapter<Station, StationAdapter.StationViewHolder>(StationDiffCallback()) {

    private var currentSelectedPosition = RecyclerView.NO_POSITION
    private var spanCount = 2 // 默认2列
    private var onStationSelectedListener: ((Station?) -> Unit)? = null

    val selectedPosition: Int
        get() = currentSelectedPosition

    val selectedStation: Station?
        get() = if (currentSelectedPosition != RecyclerView.NO_POSITION && currentSelectedPosition < itemCount) {
            getItem(currentSelectedPosition)
        } else null

    fun getStation(position: Int): Station? {
        return if (position in 0 until itemCount) getItem(position) else null
    }

    fun setSpanCount(count: Int) {
        spanCount = count
    }

    fun setOnStationSelectedListener(listener: (Station?) -> Unit) {
        onStationSelectedListener = listener
    }

    fun setSelectedPosition(position: Int) {
        if (position !in 0 until itemCount) {
            val previousPosition = currentSelectedPosition
            currentSelectedPosition = RecyclerView.NO_POSITION
            notifySelectionChanged(previousPosition)
            return
        }

        val previousPosition = currentSelectedPosition
        currentSelectedPosition = position
        notifySelectionChanged(previousPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return StationViewHolder(view)
    }

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        val station = getItem(position)
        holder.bind(station, position == currentSelectedPosition)

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            setSelectedPosition(adapterPosition)
            onStationClick(getItem(adapterPosition))
        }

        holder.favoriteIcon.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            onFavoriteClick?.invoke(getItem(adapterPosition))
        }
    }

    private fun notifySelectionChanged(previousPosition: Int) {
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition)
        }
        if (currentSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(currentSelectedPosition)
            onStationSelectedListener?.invoke(getItem(currentSelectedPosition))
        }
    }

    fun moveToPrevious() {
        if (itemCount == 0) return

        val previousPosition = currentSelectedPosition
        currentSelectedPosition = when {
            currentSelectedPosition == RecyclerView.NO_POSITION || currentSelectedPosition == 0 -> {
                itemCount - 1
            }
            currentSelectedPosition < spanCount -> {
                val lastRowStart = (itemCount - 1) / spanCount * spanCount
                val column = currentSelectedPosition % spanCount
                (lastRowStart + column).coerceAtMost(itemCount - 1)
            }
            else -> {
                currentSelectedPosition - spanCount
            }
        }

        notifySelectionChanged(previousPosition)
    }

    fun moveToNext() {
        if (itemCount == 0) return

        val previousPosition = currentSelectedPosition
        currentSelectedPosition = when {
            currentSelectedPosition == RecyclerView.NO_POSITION || currentSelectedPosition >= itemCount - 1 -> {
                0
            }
            currentSelectedPosition >= (itemCount - 1) / spanCount * spanCount -> {
                currentSelectedPosition % spanCount
            }
            else -> {
                currentSelectedPosition + spanCount
            }
        }

        notifySelectionChanged(previousPosition)
    }

    fun moveLeft() {
        if (itemCount == 0) return

        val previousPosition = currentSelectedPosition
        currentSelectedPosition = when {
            currentSelectedPosition == RecyclerView.NO_POSITION || currentSelectedPosition == 0 -> {
                itemCount - 1
            }
            currentSelectedPosition % spanCount == 0 -> {
                (currentSelectedPosition + spanCount - 1).coerceAtMost(itemCount - 1)
            }
            else -> {
                currentSelectedPosition - 1
            }
        }

        notifySelectionChanged(previousPosition)
    }

    fun moveRight() {
        if (itemCount == 0) return

        val previousPosition = currentSelectedPosition
        currentSelectedPosition = when {
            currentSelectedPosition == RecyclerView.NO_POSITION || currentSelectedPosition >= itemCount - 1 -> {
                0
            }
            (currentSelectedPosition + 1) % spanCount == 0 || currentSelectedPosition + 1 >= itemCount -> {
                currentSelectedPosition / spanCount * spanCount
            }
            else -> {
                currentSelectedPosition + 1
            }
        }

        notifySelectionChanged(previousPosition)
    }

    fun playSelectedStation() {
        if (currentSelectedPosition != RecyclerView.NO_POSITION && currentSelectedPosition < itemCount) {
            onStationClick(getItem(currentSelectedPosition))
        }
    }

    class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val stationImageView: ImageView = itemView.findViewById(R.id.stationImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val subtitleTextView: TextView = itemView.findViewById(R.id.subtitleTextView)
        val favoriteIcon: ImageView = itemView.findViewById(R.id.favoriteIcon)
        private val playingStatus: TextView = itemView.findViewById(R.id.playingStatus)

        fun bind(station: Station, isSelected: Boolean) {
            titleTextView.text = station.title
            subtitleTextView.text = station.getDisplaySubtitle()

            val imageUrl = station.getDisplayImageUrl()
            if (!imageUrl.isNullOrEmpty()) {
                stationImageView.load(imageUrl) {
                    placeholder(R.drawable.ic_radio_station)
                    error(R.drawable.ic_radio_station)
                }
            } else {
                stationImageView.setImageResource(R.drawable.ic_radio_station)
            }

            // 收藏图标始终可见，根据收藏状态切换图标
            if (station.isFavorite) {
                favoriteIcon.setImageResource(R.drawable.ic_favorite)
                favoriteIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                    itemView.context.getColor(R.color.favorite)
                )
            } else {
                favoriteIcon.setImageResource(R.drawable.ic_favorite_border)
                favoriteIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                    itemView.context.getColor(R.color.text_secondary)
                )
            }
            playingStatus.visibility = if (station.lastPlayed > 0) View.VISIBLE else View.GONE

            itemView.isSelected = isSelected
            if (isSelected) {
                // Not changing the background of the item, we can just use a selected state border or overlay.
                // Or simply change the text color.
                // Using MaterialCardView, it has its own stroke/checked state if we enable it, but changing bg color works for now.
                itemView.setBackgroundColor(itemView.context.getColor(R.color.primary))
                titleTextView.setTextColor(itemView.context.getColor(R.color.white))
                subtitleTextView.setTextColor(itemView.context.getColor(R.color.white))
                playingStatus.setTextColor(itemView.context.getColor(R.color.white))
            } else {
                itemView.setBackgroundColor(itemView.context.getColor(android.R.color.transparent))
                titleTextView.setTextColor(itemView.context.getColor(R.color.text_primary))
                subtitleTextView.setTextColor(itemView.context.getColor(R.color.text_secondary))
                playingStatus.setTextColor(itemView.context.getColor(R.color.text_secondary))
            }
        }
    }

    class StationDiffCallback : DiffUtil.ItemCallback<Station>() {
        override fun areItemsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem.contentId == newItem.contentId
        }

        override fun areContentsTheSame(oldItem: Station, newItem: Station): Boolean {
            return oldItem == newItem
        }
    }
}
