package com.xiaojilu.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R

class ContentStatsAdapter(
    private val onItemClick: (AnalysisFragment.ContentStatItem) -> Unit
) : RecyclerView.Adapter<ContentStatsAdapter.ContentStatViewHolder>() {

    private var data: List<AnalysisFragment.ContentStatItem> = emptyList()

    inner class ContentStatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.content_stat_card)
        private val contentText: TextView = itemView.findViewById(R.id.content_stat_text)
        private val countText: TextView = itemView.findViewById(R.id.content_stat_count)

        fun bind(item: AnalysisFragment.ContentStatItem) {
            contentText.text = item.content
            countText.text = "出现次数：${item.count}"

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentStatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_content_stat, parent, false)
        return ContentStatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContentStatViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    fun updateData(newData: List<AnalysisFragment.ContentStatItem>) {
        data = newData
        notifyDataSetChanged()
    }
}
