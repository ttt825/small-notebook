package com.xiaojilu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.DateUtils

class RecordAdapter(
    private var records: List<Record>,
    private val onItemClick: (Record) -> Unit
) : RecyclerView.Adapter<RecordAdapter.RecordViewHolder>() {

    inner class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contentText: TextView = itemView.findViewById(R.id.record_content)
        val timeText: TextView = itemView.findViewById(R.id.record_time)
        val editedText: TextView = itemView.findViewById(R.id.record_edited)

        fun bind(record: Record) {
            contentText.text = record.content
            timeText.text = DateUtils.formatBeijingDisplayTime(record.createdAt)
            
            if (record.createdAt != record.updatedAt) {
                editedText.visibility = View.VISIBLE
            } else {
                editedText.visibility = View.GONE
            }
            
            itemView.setOnClickListener { onItemClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount(): Int = records.size

    fun updateRecords(newRecords: List<Record>) {
        records = newRecords
        notifyDataSetChanged()
    }
}