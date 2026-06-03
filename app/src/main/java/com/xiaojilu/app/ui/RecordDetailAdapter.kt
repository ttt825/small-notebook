package com.xiaojilu.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.model.Record

class RecordDetailAdapter(
    private val onEditClick: (Record) -> Unit
) : RecyclerView.Adapter<RecordDetailAdapter.DetailViewHolder>() {

    private var data: List<Record> = emptyList()

    inner class DetailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.record_detail_date)
        private val contentText: TextView = itemView.findViewById(R.id.record_detail_content)

        fun bind(record: Record) {
            dateText.text = record.date
            contentText.text = record.content

            itemView.setOnClickListener {
                onEditClick(record)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record_detail, parent, false)
        return DetailViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetailViewHolder, position: Int) {
        holder.bind(data[position])
    }

    override fun getItemCount(): Int = data.size

    fun updateData(newData: List<Record>) {
        data = newData
        notifyDataSetChanged()
    }
}
