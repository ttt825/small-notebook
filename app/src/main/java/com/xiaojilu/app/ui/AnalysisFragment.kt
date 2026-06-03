package com.xiaojilu.app.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.StorageManager
import java.util.Calendar

class AnalysisFragment : Fragment() {

    private lateinit var monthDetailCard: CardView
    private lateinit var monthTitleText: TextView
    private lateinit var monthCountText: TextView
    private lateinit var emptyAnalysisText: TextView
    private lateinit var chartRecycler: RecyclerView
    private lateinit var contentStatsRecycler: RecyclerView

    private lateinit var chartAdapter: ChartAdapter
    private lateinit var contentStatsAdapter: ContentStatsAdapter

    private var preloadedRecords: List<Record>? = null
    private var isViewCreated = false
    private var currentMonth: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_analysis, container, false)

        initViews(view)
        setupAdapters()
        loadAnalysisData()

        isViewCreated = true

        return view
    }

    override fun onResume() {
        super.onResume()
        refresh(true)
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && isViewCreated) {
            refresh(true)
        }
    }

    private fun initViews(view: View) {
        monthDetailCard = view.findViewById(R.id.month_detail_card)
        monthTitleText = view.findViewById(R.id.tv_month_title)
        monthCountText = view.findViewById(R.id.tv_month_count)
        emptyAnalysisText = view.findViewById(R.id.tv_empty_analysis)
        chartRecycler = view.findViewById(R.id.chart_recycler)
        contentStatsRecycler = view.findViewById(R.id.content_stats_recycler)
    }

    private fun setupAdapters() {
        chartAdapter = ChartAdapter { month ->
            showMonthDetail(month)
        }
        chartRecycler.layoutManager = LinearLayoutManager(requireContext())
        chartRecycler.adapter = chartAdapter
        chartRecycler.isNestedScrollingEnabled = false

        contentStatsAdapter = ContentStatsAdapter { item ->
            showContentDetailDialog(item)
        }
        contentStatsRecycler.layoutManager = LinearLayoutManager(requireContext())
        contentStatsRecycler.adapter = contentStatsAdapter
        contentStatsRecycler.isNestedScrollingEnabled = false
    }

    private fun loadAnalysisData(animate: Boolean = false) {
        try {
            val records = StorageManager.getAllRecords().records

            if (records.isEmpty()) {
                emptyAnalysisText.visibility = View.VISIBLE
                monthDetailCard.visibility = View.GONE
                return
            }

            emptyAnalysisText.visibility = View.GONE
            monthDetailCard.visibility = View.VISIBLE

            val monthCount = mutableMapOf<String, Int>()
            records.forEach { record ->
                try {
                    val dateStr = record.date
                    val month = dateStr.substring(0, 7)
                    monthCount[month] = (monthCount[month] ?: 0) + 1
                } catch (e: Exception) {
                }
            }

            val chartData = monthCount.entries.sortedBy { it.key }.map { entry ->
                ChartItem(entry.key, entry.value)
            }

            chartAdapter.updateData(chartData, animate)

            if (chartData.isNotEmpty()) {
                showMonthDetail(chartData.last().month)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyAnalysisText.visibility = View.VISIBLE
            monthDetailCard.visibility = View.GONE
        }
    }

    private fun showMonthDetail(month: String) {
        try {
            currentMonth = month
            val records = StorageManager.getAllRecords().records
                .filter { it.date.startsWith(month) }

            monthTitleText.text = "${month.substring(0, 4)}年${month.substring(5, 7)}月"
            monthCountText.text = "总记录数：${records.size}"

            val contentStats = mutableMapOf<String, MutableList<Record>>()
            records.forEach { record ->
                val key = record.content.take(20)
                if (contentStats[key] == null) {
                    contentStats[key] = mutableListOf()
                }
                contentStats[key]!!.add(record)
            }

            val statsData = contentStats.entries.map { entry ->
                ContentStatItem(entry.key, entry.value.size, entry.value)
            }.sortedByDescending { it.count }

            contentStatsAdapter.updateData(statsData)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showContentDetailDialog(item: ContentStatItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_content_detail, null)
        val titleText: TextView = dialogView.findViewById(R.id.dialog_detail_title)
        val countText: TextView = dialogView.findViewById(R.id.dialog_detail_count)
        val detailList: RecyclerView = dialogView.findViewById(R.id.dialog_detail_list)

        titleText.text = item.content
        countText.text = "共 ${item.count} 条记录"

        val detailAdapter = RecordDetailAdapter { record ->
            showEditRecordDialog(record)
        }
        detailList.layoutManager = LinearLayoutManager(requireContext())
        detailList.adapter = detailAdapter
        detailAdapter.updateData(item.records)

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .create()
            .also { it.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)) }
            .show()
    }

    private fun showEditRecordDialog(record: Record) {
        EditRecordDialog(
            context = requireContext(),
            record = record,
            onRecordUpdated = {
                if (currentMonth.isNotEmpty()) {
                    showMonthDetail(currentMonth)
                }
                loadAnalysisData(false)
            },
            onRecordDeleted = {
                if (currentMonth.isNotEmpty()) {
                    showMonthDetail(currentMonth)
                }
                loadAnalysisData(false)
            }
        ).show()
    }

    fun refresh(animate: Boolean = false) {
        loadAnalysisData(animate)
    }

    fun preloadData(records: List<Record>) {
        preloadedRecords = records
        if (isViewCreated) {
            applyPreloadedData(false)
        }
    }

    private fun applyPreloadedData(animate: Boolean) {
        preloadedRecords?.let { records ->
            if (records.isEmpty()) {
                emptyAnalysisText.visibility = View.VISIBLE
                monthDetailCard.visibility = View.GONE
                return
            }

            emptyAnalysisText.visibility = View.GONE
            monthDetailCard.visibility = View.VISIBLE

            val monthCount = mutableMapOf<String, Int>()
            records.forEach { record ->
                try {
                    val dateStr = record.date
                    val month = dateStr.substring(0, 7)
                    monthCount[month] = (monthCount[month] ?: 0) + 1
                } catch (e: Exception) {
                }
            }

            val chartData = monthCount.entries.sortedBy { it.key }.map { entry ->
                ChartItem(entry.key, entry.value)
            }

            chartAdapter.updateData(chartData, animate)

            if (chartData.isNotEmpty()) {
                showMonthDetail(chartData.last().month)
            }
        }
    }

    data class ChartItem(val month: String, val count: Int)
    data class ContentStatItem(val content: String, val count: Int, val records: List<Record> = emptyList())
}
