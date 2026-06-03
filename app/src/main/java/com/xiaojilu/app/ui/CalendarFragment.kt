package com.xiaojilu.app.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.adapter.CalendarAdapter
import com.xiaojilu.app.adapter.RecordAdapter
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.DateUtils
import com.xiaojilu.app.utils.StorageManager
import java.util.Calendar

class CalendarFragment : Fragment() {

    private lateinit var currentMonthText: TextView
    private lateinit var prevMonthBtn: TextView
    private lateinit var nextMonthBtn: TextView
    private lateinit var calendarRecycler: RecyclerView
    private lateinit var selectedDateText: TextView
    private lateinit var addRecordBtn: Button
    private lateinit var recordsRecycler: RecyclerView
    private lateinit var emptyRecordsText: TextView

    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var recordAdapter: RecordAdapter

    private var currentDate = Calendar.getInstance()
    private var selectedDate = Calendar.getInstance()

    // 预加载数据缓存
    private var preloadedRecords: List<Record>? = null
    private var isViewCreated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_calendar, container, false)

        initViews(view)
        setupAdapters()
        setupListeners()
        renderCalendar()
        loadRecordsForSelectedDate()

        isViewCreated = true

        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun initViews(view: View) {
        currentMonthText = view.findViewById(R.id.tv_current_month)
        prevMonthBtn = view.findViewById(R.id.btn_prev_month)
        nextMonthBtn = view.findViewById(R.id.btn_next_month)
        calendarRecycler = view.findViewById(R.id.calendar_recycler)
        selectedDateText = view.findViewById(R.id.tv_selected_date)
        addRecordBtn = view.findViewById(R.id.btn_add_record)
        recordsRecycler = view.findViewById(R.id.records_recycler)
        emptyRecordsText = view.findViewById(R.id.tv_empty_records)
    }

    private fun setupAdapters() {
        calendarAdapter = CalendarAdapter { dateStr ->
            val parsedDate = DateUtils.parseBeijingDate(dateStr)
            selectedDate.time = parsedDate
            renderCalendar()
            updateSelectedDateText()
            loadRecordsForSelectedDate()
        }

        recordAdapter = RecordAdapter(emptyList()) { record ->
            showEditDialog(record)
        }

        calendarRecycler.layoutManager = GridLayoutManager(requireContext(), 7)
        calendarRecycler.adapter = calendarAdapter

        recordsRecycler.layoutManager = LinearLayoutManager(requireContext())
        recordsRecycler.adapter = recordAdapter
        recordsRecycler.isNestedScrollingEnabled = false
    }

    private fun setupListeners() {
        prevMonthBtn.setOnClickListener {
            currentDate.add(Calendar.MONTH, -1)
            renderCalendar()
        }

        nextMonthBtn.setOnClickListener {
            currentDate.add(Calendar.MONTH, 1)
            renderCalendar()
        }

        addRecordBtn.setOnClickListener {
            addRecord()
        }
    }

    private fun renderCalendar() {
        val year = currentDate.get(Calendar.YEAR)
        val month = currentDate.get(Calendar.MONTH)

        currentMonthText.text = "${year}年${month + 1}月"

        val days = mutableListOf<CalendarAdapter.CalendarDay>()

        // 添加星期标题
        val firstDayOfMonth = Calendar.getInstance().apply {
            set(year, month, 1)
        }
        val startDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK) - 1

        // 添加空白天数
        for (i in 0 until startDayOfWeek) {
            days.add(CalendarAdapter.CalendarDay(0, "", false, false, 0))
        }

        // 添加当月天数
        val lastDayOfMonth = Calendar.getInstance().apply {
            set(year, month + 1, 0)
        }
        val totalDays = lastDayOfMonth.get(Calendar.DAY_OF_MONTH)

        val todayStr = DateUtils.getCurrentBeijingDate()
        val selectedDateStr = DateUtils.formatBeijingDate(selectedDate.time)

        for (day in 1..totalDays) {
            val calendar = Calendar.getInstance().apply {
                set(year, month, day)
            }
            val dateStr = DateUtils.formatBeijingDate(calendar.time)
            val isToday = dateStr == todayStr
            val isSelected = dateStr == selectedDateStr
            val recordCount = StorageManager.getDateRecordCount(dateStr)

            days.add(CalendarAdapter.CalendarDay(day, dateStr, true, isToday, recordCount))
        }

        calendarAdapter.updateDays(days)
        calendarAdapter.updateSelectedDate(selectedDateStr)
    }

    private fun updateSelectedDateText() {
        val dateStr = DateUtils.formatBeijingDate(selectedDate.time)
        val parts = dateStr.split("-")
        selectedDateText.text = "${parts[0]}年${parts[1]}月${parts[2]}日"
    }

    private fun loadRecordsForSelectedDate() {
        try {
            val dateStr = DateUtils.formatBeijingDate(selectedDate.time)
            val records = StorageManager.getRecordsByDate(dateStr)
                .sortedByDescending { it.createdAt }
            recordAdapter.updateRecords(records)

            // 根据是否有记录显示不同的UI
            if (records.isEmpty()) {
                recordsRecycler.visibility = View.GONE
                emptyRecordsText.visibility = View.VISIBLE
                showEmptyAnimation()
            } else {
                recordsRecycler.visibility = View.VISIBLE
                emptyRecordsText.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showEmptyAnimation() {
        // 重置动画
        emptyRecordsText.alpha = 0f
        emptyRecordsText.translationY = 20f

        // 执行淡入和上移动画
        emptyRecordsText.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 添加缩放动画效果
        emptyRecordsText.scaleX = 0.8f
        emptyRecordsText.scaleY = 0.8f
        emptyRecordsText.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun addRecord() {
        val currentDateStr = DateUtils.formatBeijingDate(selectedDate.time)
        
        AddRecordDialog(
            requireContext(),
            currentDateStr,
            onRecordAdded = {
                loadRecordsForSelectedDate()
                renderCalendar()
            }
        ).show()
    }

    private fun showEditDialog(record: Record) {
        EditRecordDialog(
            requireContext(),
            record,
            onRecordUpdated = {
                loadRecordsForSelectedDate()
                renderCalendar()
            },
            onRecordDeleted = {
                loadRecordsForSelectedDate()
                renderCalendar()
            }
        ).show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun refresh() {
        renderCalendar()
        loadRecordsForSelectedDate()
    }

    // 预加载数据方法，由MainActivity在后台调用
    fun preloadData(records: List<Record>) {
        preloadedRecords = records
        // 如果视图已经创建，立即应用预加载的数据
        if (isViewCreated) {
            applyPreloadedData()
        }
    }

    private fun applyPreloadedData() {
        preloadedRecords?.let { records ->
            val selectedDateStr = DateUtils.formatBeijingDate(selectedDate.time)
            val filteredRecords = records
                .filter { it.date == selectedDateStr }
                .sortedByDescending { it.createdAt }
            recordAdapter.updateRecords(filteredRecords)

            // 根据是否有记录显示不同的UI
            if (filteredRecords.isEmpty()) {
                recordsRecycler.visibility = View.GONE
                emptyRecordsText.visibility = View.VISIBLE
            } else {
                recordsRecycler.visibility = View.VISIBLE
                emptyRecordsText.visibility = View.GONE
            }
        }
    }
}