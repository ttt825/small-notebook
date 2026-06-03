package com.xiaojilu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.model.DateGroup
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.DateUtils

class DateGroupAdapter(
    private var dateGroups: List<DateGroup>,
    private val onRecordClick: (Record) -> Unit
) : RecyclerView.Adapter<DateGroupAdapter.DateGroupViewHolder>() {

    inner class DateGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.date_group_date)
        private val countText: TextView = itemView.findViewById(R.id.date_group_count)
        private val expandButton: Button = itemView.findViewById(R.id.date_group_expand)
        private val recordsRecycler: RecyclerView = itemView.findViewById(R.id.date_group_records)
        
        private lateinit var recordAdapter: RecordAdapter

        fun bind(group: DateGroup) {
            dateText.text = group.formattedDate
            countText.text = "${group.recordCount}条记录"
            
            // 设置展开/折叠状态
            updateExpandState(group)
            
            // 设置记录列表
            recordAdapter = RecordAdapter(group.records) { record ->
                onRecordClick(record)
            }
            recordsRecycler.layoutManager = LinearLayoutManager(itemView.context)
            recordsRecycler.adapter = recordAdapter
            recordsRecycler.isNestedScrollingEnabled = false
            
            // 展开/折叠按钮点击
            expandButton.setOnClickListener {
                group.isExpanded = !group.isExpanded
                updateExpandState(group)
            }
        }
        
        private fun updateExpandState(group: DateGroup) {
            if (group.isExpanded) {
                expandButton.text = "▼"
                recordsRecycler.visibility = View.VISIBLE
            } else {
                expandButton.text = "▶"
                recordsRecycler.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateGroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_date_group, parent, false)
        return DateGroupViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateGroupViewHolder, position: Int) {
        holder.bind(dateGroups[position])
    }

    override fun getItemCount(): Int = dateGroups.size

    fun updateDateGroups(newGroups: List<DateGroup>) {
        dateGroups = newGroups
        notifyDataSetChanged()
    }
}