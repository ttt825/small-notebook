package com.xiaojilu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R

class PresetAdapter(
    private var presets: List<String>,
    private val onPresetClick: (String) -> Unit,
    private val onPresetDelete: (Int) -> Unit,
    private val onAddPreset: () -> Unit
) : RecyclerView.Adapter<PresetAdapter.PresetViewHolder>() {

    companion object {
        private const val TYPE_PRESET = 0
        private const val TYPE_ADD = 1
    }

    inner class PresetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val presetText: TextView = itemView.findViewById(R.id.tv_preset_text)
        val deleteButton: ImageView = itemView.findViewById(R.id.iv_preset_delete)

        fun bind(preset: String, position: Int) {
            presetText.text = preset

            presetText.setOnClickListener {
                onPresetClick(preset)
            }

            // 长按显示删除按钮
            presetText.setOnLongClickListener {
                toggleDeleteButton(deleteButton)
                true
            }

            deleteButton.setOnClickListener {
                onPresetDelete(position)
                deleteButton.visibility = View.GONE
            }
        }

        fun bindAddButton() {
            presetText.text = "+"
            presetText.setOnClickListener {
                onAddPreset()
            }
            deleteButton.visibility = View.GONE
            presetText.setOnLongClickListener(null)
        }

        private fun toggleDeleteButton(button: ImageView) {
            button.visibility = if (button.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == presets.size) {
            TYPE_ADD
        } else {
            TYPE_PRESET
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preset, parent, false)
        return PresetViewHolder(view)
    }

    override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
        if (getItemViewType(position) == TYPE_ADD) {
            holder.bindAddButton()
        } else {
            holder.bind(presets[position], position)
        }
    }

    override fun getItemCount(): Int = presets.size + 1 // +1 for add button

    fun updatePresets(newPresets: List<String>) {
        presets = newPresets
        notifyDataSetChanged()
    }
}