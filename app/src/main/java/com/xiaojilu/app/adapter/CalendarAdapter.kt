package com.xiaojilu.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.utils.StorageManager
import java.util.Calendar

class CalendarAdapter(
    private val onDateClick: (String) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var days: List<CalendarDay> = emptyList()
    private var selectedDate: String? = null
    private var currentDate: String = ""

    data class CalendarDay(
        val day: Int,
        val dateStr: String,
        val isCurrentMonth: Boolean,
        val isToday: Boolean,
        val recordCount: Int
    )

    inner class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dayText: TextView = itemView.findViewById(R.id.calendar_day_text)
        private val countText: TextView = itemView.findViewById(R.id.calendar_record_count)
        private val background: View = itemView.findViewById(R.id.calendar_day_bg)

        fun bind(day: CalendarDay) {
            // 如果是空白区域（day=0），不显示任何内容
            if (day.day == 0) {
                dayText.text = ""
                countText.visibility = View.GONE
                background.setBackgroundResource(android.R.color.transparent)
                itemView.setOnClickListener(null)
                return
            }

            dayText.text = day.day.toString()

            if (day.recordCount > 0) {
                countText.text = day.recordCount.toString()
                countText.visibility = View.VISIBLE
            } else {
                countText.visibility = View.GONE
            }

            if (day.dateStr == selectedDate) {
                background.setBackgroundResource(R.drawable.calendar_day_selected)
                dayText.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else if (day.isToday) {
                background.setBackgroundResource(R.drawable.calendar_day_today)
                dayText.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary_light))
            } else if (day.isCurrentMonth) {
                background.setBackgroundResource(R.drawable.calendar_day_normal)
                dayText.setTextColor(ContextCompat.getColor(itemView.context, R.color.ink))
            } else {
                background.setBackgroundResource(R.drawable.calendar_day_empty)
                dayText.setTextColor(ContextCompat.getColor(itemView.context, R.color.muted_light))
            }

            itemView.setOnClickListener {
                if (day.isCurrentMonth) {
                    onDateClick(day.dateStr)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        holder.bind(days[position])
    }

    override fun getItemCount(): Int = days.size

    fun updateDays(newDays: List<CalendarDay>) {
        days = newDays
        notifyDataSetChanged()
    }

    fun updateSelectedDate(date: String) {
        selectedDate = date
        notifyDataSetChanged()
    }

    fun updateCurrentDate(date: String) {
        currentDate = date
        notifyDataSetChanged()
    }
}