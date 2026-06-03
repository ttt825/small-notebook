package com.xiaojilu.app.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xiaojilu.app.R
import com.xiaojilu.app.adapter.PresetAdapter
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.DateUtils
import com.xiaojilu.app.utils.IdGenerator
import com.xiaojilu.app.utils.StorageManager
import java.util.Calendar

class AddRecordDialog(
    private val context: Context,
    private val initialDate: String,
    private val onRecordAdded: () -> Unit
) {

    private lateinit var dialog: AlertDialog
    private lateinit var datetimeText: TextView
    private lateinit var contentEdit: EditText
    private lateinit var presetsRecyclerView: RecyclerView
    private lateinit var presetAdapter: PresetAdapter
    private var selectedDateTime: Calendar = Calendar.getInstance()

    fun show() {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_record, null)

        initViews(view)
        setupListeners(view)
        setupPresets()

        builder.setView(view)
        dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun initViews(view: android.view.View) {
        datetimeText = view.findViewById(R.id.tv_add_datetime)
        contentEdit = view.findViewById(R.id.et_add_content)
        presetsRecyclerView = view.findViewById(R.id.rv_presets)

        // 初始化为选中的日期，当前时间
        try {
            val date = DateUtils.parseBeijingDate(initialDate)
            selectedDateTime.time = date
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateDateTimeDisplay()
        contentEdit.requestFocus()
    }

    private fun setupListeners(view: android.view.View) {
        // 日期时间选择
        datetimeText.setOnClickListener {
            showDatePicker()
        }

        // 取消按钮
        view.findViewById<Button>(R.id.btn_cancel_add).setOnClickListener {
            dialog.dismiss()
        }

        // 保存按钮
        view.findViewById<Button>(R.id.btn_confirm_add).setOnClickListener {
            saveRecord()
        }
    }

    private fun setupPresets() {
        presetAdapter = PresetAdapter(
            presets = StorageManager.getPresets(),
            onPresetClick = { preset ->
                // 点击预设，添加到输入框
                val currentText = contentEdit.text.toString()
                val newText = if (currentText.isBlank()) {
                    preset
                } else {
                    "$currentText $preset"
                }
                contentEdit.setText(newText)
                contentEdit.setSelection(newText.length)
            },
            onPresetDelete = { index ->
                // 删除预设
                StorageManager.deletePreset(index)
                presetAdapter.updatePresets(StorageManager.getPresets())
            },
            onAddPreset = {
                // 添加新预设
                val currentText = contentEdit.text.toString().trim()
                if (currentText.isNotBlank()) {
                    // 提取第一个词作为预设（最多10个字符）
                    val preset = currentText.split(" ", "，", "。", "\n").firstOrNull()?.trim()?.take(10) ?: currentText.take(10)
                    if (StorageManager.addPreset(preset)) {
                        presetAdapter.updatePresets(StorageManager.getPresets())
                        android.widget.Toast.makeText(context, "已添加预设: $preset", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "预设已存在", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "请先输入内容", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )

        presetsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = presetAdapter
        }
    }

    private fun showDatePicker() {
        val datePickerDialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                selectedDateTime.set(Calendar.YEAR, year)
                selectedDateTime.set(Calendar.MONTH, month)
                selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker()
            },
            selectedDateTime.get(Calendar.YEAR),
            selectedDateTime.get(Calendar.MONTH),
            selectedDateTime.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val timePickerDialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedDateTime.set(Calendar.MINUTE, minute)
                updateDateTimeDisplay()
            },
            selectedDateTime.get(Calendar.HOUR_OF_DAY),
            selectedDateTime.get(Calendar.MINUTE),
            true
        )
        timePickerDialog.show()
    }

    private fun updateDateTimeDisplay() {
        val year = selectedDateTime.get(Calendar.YEAR)
        val month = selectedDateTime.get(Calendar.MONTH) + 1
        val day = selectedDateTime.get(Calendar.DAY_OF_MONTH)
        val hour = selectedDateTime.get(Calendar.HOUR_OF_DAY)
        val minute = selectedDateTime.get(Calendar.MINUTE)
        
        datetimeText.text = String.format("%d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
    }

    private fun saveRecord() {
        try {
            val content = contentEdit.text.toString().trim()
            if (content.isEmpty()) {
                android.widget.Toast.makeText(context, "请输入记录内容", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val dateStr = DateUtils.formatBeijingDate(selectedDateTime.time)
            val timeStr = String.format("%02d:%02d", 
                selectedDateTime.get(Calendar.HOUR_OF_DAY), 
                selectedDateTime.get(Calendar.MINUTE))
            val dateTimeStr = "${dateStr}T${timeStr}"

            val isoTime = DateUtils.beijingDatetimeToIso(dateTimeStr)

            val record = Record(
                id = IdGenerator.generateShortId(),
                date = dateStr,
                content = content,
                createdAt = isoTime,
                updatedAt = isoTime // 新记录的创建时间和更新时间应该相同
            )

            StorageManager.addRecord(record)
            android.widget.Toast.makeText(context, "记录添加成功", android.widget.Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onRecordAdded()
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "添加失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}