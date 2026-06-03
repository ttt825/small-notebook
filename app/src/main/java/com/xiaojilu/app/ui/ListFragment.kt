package com.xiaojilu.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.adapter.DateGroupAdapter
import com.xiaojilu.app.model.DateGroup
import com.xiaojilu.app.model.ExportData
import com.xiaojilu.app.model.ExportRecord
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.DateUtils
import com.xiaojilu.app.utils.StorageManager
import com.google.gson.Gson

class ListFragment : Fragment() {

    private lateinit var yearSpinner: android.widget.Spinner
    private lateinit var monthSpinner: android.widget.Spinner
    private lateinit var listRecycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var sortAscBtn: Button
    private lateinit var sortDescBtn: Button
    private lateinit var exportBtn: Button
    private lateinit var importBtn: Button

    private lateinit var dateGroupAdapter: DateGroupAdapter
    private var sortOrder = "desc" // "asc" or "desc"
    private var tempExportJson: String = ""

    // 预加载数据缓存
    private var preloadedRecords: com.xiaojilu.app.model.RecordData? = null
    private var preloadedYears: List<String>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_list, container, false)

        initViews(view)
        setupAdapters()
        setupListeners()
        updateSortButtons()
        setupSpinners()
        loadRecords()

        return view
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun initViews(view: View) {
        yearSpinner = view.findViewById(R.id.spinner_year)
        monthSpinner = view.findViewById(R.id.spinner_month)
        listRecycler = view.findViewById(R.id.list_recycler)
        emptyText = TextView(requireContext()).apply {
            text = "暂无记录"
            textSize = 16f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_700))
            gravity = android.view.Gravity.CENTER
        }
        sortAscBtn = view.findViewById(R.id.btn_sort_asc)
        sortDescBtn = view.findViewById(R.id.btn_sort_desc)
        exportBtn = view.findViewById(R.id.btn_export)
        importBtn = view.findViewById(R.id.btn_import)
    }

    private fun setupAdapters() {
        dateGroupAdapter = DateGroupAdapter(emptyList()) { record ->
            showEditDialog(record)
        }
        listRecycler.layoutManager = LinearLayoutManager(requireContext())
        listRecycler.adapter = dateGroupAdapter
        listRecycler.isNestedScrollingEnabled = false
    }

    private fun setupListeners() {
        sortAscBtn.setOnClickListener {
            sortOrder = "asc"
            updateSortButtons()
            loadRecords()
        }

        sortDescBtn.setOnClickListener {
            sortOrder = "desc"
            updateSortButtons()
            loadRecords()
        }

        exportBtn.setOnClickListener {
            exportRecords()
        }

        importBtn.setOnClickListener {
            importRecords()
        }
    }

    private fun updateSortButtons() {
        if (sortOrder == "asc") {
            sortAscBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.blue_600)
            )
            sortDescBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.gray_700)
            )
        } else {
            sortAscBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.gray_700)
            )
            sortDescBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.blue_600)
            )
        }
    }

    private fun setupSpinners() {
        try {
            // 使用预加载的年份数据，如果没有则重新获取
            val years = preloadedYears?.toMutableList() ?: StorageManager.getRecordYears().toMutableList()
            years.add(0, "全部年份")
            
            val yearAdapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                years
            )
            yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            yearSpinner.adapter = yearAdapter

            val months = mutableListOf("全部月份")
            for (i in 1..12) {
                months.add("${i}月")
            }

            val monthAdapter = android.widget.ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                months
            )
            monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            monthSpinner.adapter = monthAdapter

            yearSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    loadRecords()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            monthSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    loadRecords()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadRecords() {
        try {
            val year = if (yearSpinner.selectedItemPosition == 0) null else yearSpinner.selectedItem.toString().replace("年", "")
            val month = if (monthSpinner.selectedItemPosition == 0) null else monthSpinner.selectedItem.toString().replace("月", "")

            var records = StorageManager.filterRecords(year, month)

            records = if (sortOrder == "asc") {
                records.sortedBy { it.date }
            } else {
                records.sortedByDescending { it.date }
            }

            if (records.isEmpty()) {
                listRecycler.visibility = View.GONE
            } else {
                listRecycler.visibility = View.VISIBLE
                // 按日期分组
                val groupedRecords = records.groupBy { it.date }
                val dateGroups = groupedRecords.entries.map { (date, recordsList) ->
                    DateGroup(date, recordsList, true) // 默认展开
                }.let { groups ->
                    if (sortOrder == "asc") {
                        groups.sortedBy { it.date }
                    } else {
                        groups.sortedByDescending { it.date }
                    }
                }
                
                dateGroupAdapter.updateDateGroups(dateGroups)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun exportRecords() {
        try {
            // 获取当前筛选条件
            val year = if (yearSpinner.selectedItemPosition == 0) null else yearSpinner.selectedItem.toString().replace("年", "")
            val month = if (monthSpinner.selectedItemPosition == 0) null else monthSpinner.selectedItem.toString().replace("月", "")

            // 根据筛选条件获取导出数据
            val exportRecords = StorageManager.getExportDataForFile(year, month)

            // 使用新的导出格式：{ "records": [{内容}]}
            val exportData = ExportData(exportRecords)
            val gson = Gson()
            val json = gson.toJson(exportData)

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "小记录_${DateUtils.getCurrentBeijingDate()}.json")
            }
            // 临时保存JSON数据
            tempExportJson = json
            startActivityForResult(intent, EXPORT_REQUEST_CODE)
        } catch (e: Exception) {
            showToast("导出失败")
        }
    }

    private fun importRecords() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                EXPORT_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        try {
                            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                                output.write(tempExportJson.toByteArray())
                                showToast("导出成功")
                            }
                        } catch (e: Exception) {
                            showToast("导出失败")
                        }
                    }
                }
                IMPORT_REQUEST_CODE -> {
                    data.data?.let { uri ->
                        try {
                            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                                val json = input.bufferedReader().readText()
                                val gson = Gson()

                                // 尝试解析为新的导出格式 { "records": [{内容}]}
                                var importSuccess = false
                                var importCount = 0

                                try {
                                    val exportData = gson.fromJson(json, com.xiaojilu.app.model.ExportData::class.java)
                                    val exportRecords = exportData.records
                                    val records = StorageManager.convertImportData(exportRecords)
                                    importCount = StorageManager.batchImport(records)
                                    importSuccess = true
                                } catch (e: Exception) {
                                    // 如果新格式解析失败，尝试旧格式（直接数组）
                                    try {
                                        val exportRecords = gson.fromJson(json, Array<com.xiaojilu.app.model.ExportRecord>::class.java).toList()
                                        val records = StorageManager.convertImportData(exportRecords)
                                        importCount = StorageManager.batchImport(records)
                                        importSuccess = true
                                    } catch (e2: Exception) {
                                        // 如果直接数组解析失败，尝试旧格式（RecordData结构）
                                        try {
                                            val recordData = gson.fromJson(json, com.xiaojilu.app.model.RecordData::class.java)

                                            // 检查日期格式，如果是新格式则转换
                                            val records = if (recordData.records.isNotEmpty() &&
                                                recordData.records[0].createdAt.contains(" ")) {
                                                // 新日期格式，需要转换
                                                val exportRecords = recordData.records.map { record ->
                                                    com.xiaojilu.app.model.ExportRecord(
                                                        id = record.id,
                                                        date = record.date,
                                                        content = record.content,
                                                        createdAt = DateUtils.formatBeijingTimeForExport(record.createdAt),
                                                        updatedAt = DateUtils.formatBeijingTimeForExport(record.updatedAt)
                                                    )
                                                }
                                                StorageManager.convertImportData(exportRecords)
                                            } else {
                                                // 旧格式，直接使用
                                                recordData.records
                                            }

                                            importCount = StorageManager.batchImport(records)
                                            importSuccess = true
                                        } catch (e3: Exception) {
                                            importSuccess = false
                                        }
                                    }
                                }

                                if (importSuccess) {
                                    showToast("成功导入 $importCount 条记录")
                                    loadRecords()
                                    setupSpinners()
                                } else {
                                    showToast("导入失败: 文件格式不正确")
                                }
                            }
                        } catch (e: Exception) {
                            showToast("导入失败: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun showEditDialog(record: Record) {
        EditRecordDialog(
            requireContext(),
            record,
            onRecordUpdated = {
                loadRecords()
                setupSpinners()
            },
            onRecordDeleted = {
                loadRecords()
                setupSpinners()
            }
        ).show()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun refresh() {
        setupSpinners()
        loadRecords()
    }

    // 预加载数据方法，由MainActivity在后台调用
    fun preloadData(records: com.xiaojilu.app.model.RecordData, years: List<String>) {
        preloadedRecords = records
        preloadedYears = years
    }

    companion object {
        private const val EXPORT_REQUEST_CODE = 1001
        private const val IMPORT_REQUEST_CODE = 1002
    }
}