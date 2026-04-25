package com.carradio.ui.province

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.carradio.R
import com.carradio.data.model.Province

/**
 * 省份列表适配器
 * 宫格布局显示省份，支持选中状态高亮和方向键导航
 */
class ProvinceAdapter(
    private val onProvinceClick: (Int) -> Unit
) : RecyclerView.Adapter<ProvinceAdapter.ProvinceViewHolder>() {

    private var provinces: List<Province> = emptyList()
    private var currentSelectedPosition = RecyclerView.NO_POSITION
    private var spanCount = 2 // 默认2列

    val selectedPosition: Int
        get() = currentSelectedPosition

    fun submitList(newProvinces: List<Province>) {
        provinces = newProvinces
        if (currentSelectedPosition !in provinces.indices) {
            currentSelectedPosition = if (provinces.isEmpty()) RecyclerView.NO_POSITION else 0
        }
        notifyDataSetChanged()
    }

    fun setSelectedPosition(position: Int) {
        if (position !in provinces.indices) {
            val previousPosition = currentSelectedPosition
            currentSelectedPosition = RecyclerView.NO_POSITION
            updateSelection(previousPosition)
            return
        }

        val previousPosition = currentSelectedPosition
        currentSelectedPosition = position
        updateSelection(previousPosition)
    }

    fun setSpanCount(count: Int) {
        spanCount = count
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProvinceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_province, parent, false)
        return ProvinceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProvinceViewHolder, position: Int) {
        val province = provinces[position]
        holder.bind(province, position == currentSelectedPosition)

        holder.itemView.setOnClickListener {
            val adapterPosition = holder.bindingAdapterPosition
            if (adapterPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            setSelectedPosition(adapterPosition)
            onProvinceClick(adapterPosition)
        }
    }

    override fun getItemCount(): Int = provinces.size

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

        updateSelection(previousPosition)
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

        updateSelection(previousPosition)
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

        updateSelection(previousPosition)
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

        updateSelection(previousPosition)
    }

    private fun updateSelection(previousPosition: Int) {
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition)
        }
        if (currentSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(currentSelectedPosition)
        }
    }

    class ProvinceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val provinceName: TextView = itemView.findViewById(R.id.provinceName)

        fun bind(province: Province, isSelected: Boolean) {
            provinceName.text = province.name

            if (isSelected) {
                itemView.background = itemView.context.getDrawable(R.drawable.bg_selected_province)
                provinceName.setTextColor(itemView.context.getColor(R.color.white))
            } else {
                itemView.background = itemView.context.getDrawable(android.R.color.transparent)
                provinceName.setTextColor(itemView.context.getColor(R.color.text_primary))
            }
        }
    }
}
