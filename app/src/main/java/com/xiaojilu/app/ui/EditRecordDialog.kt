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
import androidx.appcompat.app.AppCompatActivity
import com.xiaojilu.app.R
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.utils.DateUtils
import com.xiaojilu.app.utils.StorageManager
import java.util.Calendar

class EditRecordDialog(
    private val context: Context,
    private val record: Record,
    private val onRecordUpdated: () -> Unit,
    private val onRecordDeleted: () -> Unit
) {

    private lateinit var dialog: AlertDialog
    private lateinit var datetimeText: TextView
    private lateinit var contentEdit: EditText
    private var selectedDateTime: Calendar = Calendar.getInstance()
    private var originalContent: String = ""
    private var originalDate: String = ""

    fun show() {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_record, null)

        initViews(view)
        setupListeners(view)

        builder.setView(view)
        dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun initViews(view: android.view.View) {
        datetimeText = view.findViewById(R.id.tv_edit_datetime)
        contentEdit = view.findViewById(R.id.et_edit_content)

        // 初始化显示
        try {
            val date = DateUtils.parseBeijingDate(record.date)
            selectedDateTime.time = date
            
            // 解析时间
            val time = DateUtils.formatBeijingDisplayTime(record.createdAt)
            val parts = time.split(":")
            if (parts.size == 2) {
                selectedDateTime.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                selectedDateTime.set(Calendar.MINUTE, parts[1].toInt())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        updateDateTimeDisplay()
        contentEdit.setText(record.content)
        
        // 保存原始数据用于修改检测
        originalContent = record.content
        originalDate = record.date
    }

    private fun setupListeners(view: android.view.View) {
        // 日期时间选择
        datetimeText.setOnClickListener {
            showDatePicker()
        }

        // 删除按钮
        view.findViewById<Button>(R.id.btn_delete_record).setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("确认删除")
                .setMessage("确定要删除这条记录吗？此操作不可恢复！")
                .setPositiveButton("删除") { _, _ ->
                    deleteRecord()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 取消按钮
        view.findViewById<Button>(R.id.btn_cancel_edit).setOnClickListener {
            dialog.dismiss()
        }

        // 保存按钮
        view.findViewById<Button>(R.id.btn_save_record).setOnClickListener {
            saveRecord()
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
            val dateTimeStr = "${dateStr}T${timeStr}" // 添加T分隔符

            // 检测是否有实际修改
            val contentChanged = content != originalContent
            val dateChanged = dateStr != originalDate
            
            // 如果没有修改，直接关闭对话框
            if (!contentChanged && !dateChanged) {
                android.widget.Toast.makeText(context, "未做任何修改", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return
            }

            // 如果有修改，更新updatedAt时间戳
            val updatedRecord = record.copy(
                content = content,
                date = dateStr,
                createdAt = DateUtils.beijingDatetimeToIso(dateTimeStr),
                updatedAt = if (contentChanged || dateChanged) {
                    DateUtils.getCurrentIsoDateTime()
                } else {
                    record.updatedAt
                }
            )

            val result = StorageManager.updateRecord(record.id, updatedRecord)
            if (result != null) {
                android.widget.Toast.makeText(context, "记录保存成功", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onRecordUpdated()
            } else {
                android.widget.Toast.makeText(context, "记录保存失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "保存失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteRecord() {
        try {
            val result = StorageManager.deleteRecord(record.id)
            if (result != null) {
                android.widget.Toast.makeText(context, "记录删除成功", android.widget.Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onRecordDeleted()
            } else {
                android.widget.Toast.makeText(context, "记录删除失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "删除失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}