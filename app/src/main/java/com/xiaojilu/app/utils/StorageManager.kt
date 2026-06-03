package com.xiaojilu.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaojilu.app.model.ExportRecord
import com.xiaojilu.app.model.Record
import com.xiaojilu.app.model.RecordData
import com.xiaojilu.app.utils.DateUtils
import kotlinx.coroutines.launch
import java.util.UUID

object IdGenerator {
    fun generateShortId(): String {
        val timestamp = System.currentTimeMillis().toString().takeLast(6)
        val random = (0..9999).random().toString(36)
        return timestamp + random
    }
}

object StorageManager {
    private const val PREFS_NAME = "RecordPrefs"
    private const val KEY_RECORDS = "dailyRecords"
    private const val KEY_AUTH = "recordAuth"
    private const val KEY_PASSWORD = "userPassword"
    private const val KEY_PASSWORD_ENABLED = "passwordEnabled"
    private const val KEY_PRESETS = "contentPresets"
    private const val KEY_DB_ENABLED = "databaseEnabled"
    private const val KEY_DB_URL = "databaseUrl"
    private const val KEY_DB_KEY = "databaseKey"
    private const val KEY_DIRTY_IDS = "dirtyRecordIds"
    private const val KEY_DELETED_IDS = "deletedRecordIds"
    private const val KEY_FAILED_IDS = "failedRecordIds"
    private const val DEFAULT_PASSWORD = "000"
    
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun checkPassword(password: String): Boolean {
        val currentPassword = prefs.getString(KEY_PASSWORD, DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
        return password == currentPassword
    }
    
    fun changePassword(newPassword: String) {
        prefs.edit().putString(KEY_PASSWORD, newPassword).apply()
    }
    
    fun setAuthenticated(isAuthenticated: Boolean) {
        prefs.edit().putBoolean(KEY_AUTH, isAuthenticated).apply()
    }
    
    fun isAuthenticated(): Boolean {
        return prefs.getBoolean(KEY_AUTH, false)
    }

    fun isPasswordEnabled(): Boolean {
        return prefs.getBoolean(KEY_PASSWORD_ENABLED, false)
    }

    fun setPasswordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PASSWORD_ENABLED, enabled).apply()
    }
    
    fun getAllRecords(): RecordData {
        val json = prefs.getString(KEY_RECORDS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, RecordData::class.java)
            } catch (e: Exception) {
                RecordData(emptyList())
            }
        } else {
            RecordData(emptyList())
        }
    }
    
    private fun saveAllRecords(data: RecordData) {
        val json = gson.toJson(data)
        prefs.edit().putString(KEY_RECORDS, json).apply()
    }
    
    fun addRecord(record: Record): Record {
        val data = getAllRecords()
        val updatedRecords = data.records.toMutableList()
        updatedRecords.add(record)
        saveAllRecords(RecordData(updatedRecords))

        if (isDatabaseEnabled()) {
            markDirty(record.id)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val success = DatabaseManager.addRecordToDatabase(record)
                    if (success) {
                        removeDirty(record.id)
                        removeFailedId(record.id)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return record
    }
    
    fun updateRecord(id: String, updated: Record): Record? {
        val data = getAllRecords()
        val updatedRecords = data.records.map { record ->
            if (record.id == id) updated else record
        }
        saveAllRecords(RecordData(updatedRecords))
        val result = updatedRecords.find { it.id == id }

        if (isDatabaseEnabled() && result != null) {
            markDirty(id)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val success = DatabaseManager.updateRecordInDatabase(id, result)
                    if (success) {
                        removeDirty(id)
                        removeFailedId(id)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return result
    }
    
    fun deleteRecord(id: String): Record? {
        val data = getAllRecords()
        val deletedRecord = data.records.find { it.id == id }
        val updatedRecords = data.records.filter { it.id != id }
        saveAllRecords(RecordData(updatedRecords))

        if (isDatabaseEnabled()) {
            removeDirty(id)
            addDeletedId(id)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val success = DatabaseManager.deleteRecordFromDatabase(id)
                    if (success) removeDeletedId(id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return deletedRecord
    }

    fun deleteRecordLocalOnly(id: String): Record? {
        val data = getAllRecords()
        val deletedRecord = data.records.find { it.id == id }
        val updatedRecords = data.records.filter { it.id != id }
        saveAllRecords(RecordData(updatedRecords))
        removeDirty(id)
        removeFailedId(id)
        return deletedRecord
    }
    
    fun getRecordsByDate(date: String): List<Record> {
        return getAllRecords().records.filter { it.date == date }
    }
    
    fun getDateRecordCount(date: String): Int {
        return getRecordsByDate(date).size
    }
    
    fun filterRecords(year: String?, month: String?): List<Record> {
        val records = getAllRecords().records
        return records.filter { record ->
            if (year != null && month != null) {
                record.date.startsWith("$year-${month.padStart(2, '0')}")
            } else if (year != null) {
                record.date.startsWith("$year-")
            } else if (month != null) {
                val recordMonth = record.date.substring(5, 7)
                recordMonth == month.padStart(2, '0')
            } else {
                true
            }
        }
    }
    
    fun batchImport(records: List<Record>): Int {
        val data = getAllRecords()
        val existingIds = data.records.map { it.id }.toSet()
        val uniqueRecords = records.filter { it.id !in existingIds }
        
        if (uniqueRecords.isEmpty()) return 0
        
        val updatedRecords = data.records.toMutableList()
        updatedRecords.addAll(uniqueRecords)
        saveAllRecords(RecordData(updatedRecords))

        if (isDatabaseEnabled()) {
            markDirtyBatch(uniqueRecords.map { it.id })
        }

        return uniqueRecords.size
    }

    fun importFromRemote(records: List<Record>): Int {
        val data = getAllRecords()
        val existingIds = data.records.map { it.id }.toSet()
        val newRecords = records.filter { it.id !in existingIds }

        if (newRecords.isEmpty()) return 0

        val updatedRecords = data.records.toMutableList()
        updatedRecords.addAll(newRecords)
        saveAllRecords(RecordData(updatedRecords))

        return newRecords.size
    }

    fun updateRecordFromRemote(id: String, updated: Record): Record? {
        val data = getAllRecords()
        val updatedRecords = data.records.map { record ->
            if (record.id == id) updated else record
        }
        saveAllRecords(RecordData(updatedRecords))
        return updatedRecords.find { it.id == id }
    }
    
    fun getExportData(): RecordData {
        return getAllRecords()
    }
    
    // 获取用于导出的数据（日期格式转换为yyyy-MM-dd HH:mm:ss）
    fun getExportDataForFile(): List<ExportRecord> {
        val records = getAllRecords().records
        return records.map { record ->
            ExportRecord(
                id = record.id,
                date = record.date,
                content = record.content,
                createdAt = DateUtils.formatBeijingTimeForExport(record.createdAt),
                updatedAt = DateUtils.formatBeijingTimeForExport(record.updatedAt)
            )
        }
    }
    
    // 根据筛选条件获取导出数据
    fun getExportDataForFile(year: String?, month: String?): List<ExportRecord> {
        val records = filterRecords(year, month)
        return records.map { record ->
            ExportRecord(
                id = record.id,
                date = record.date,
                content = record.content,
                createdAt = DateUtils.formatBeijingTimeForExport(record.createdAt),
                updatedAt = DateUtils.formatBeijingTimeForExport(record.updatedAt)
            )
        }
    }
    
    // 从导入的数据转换为Record格式
    fun convertImportData(exportRecords: List<ExportRecord>): List<Record> {
        return exportRecords.map { exportRecord ->
            Record(
                id = exportRecord.id,
                date = exportRecord.date,
                content = exportRecord.content,
                createdAt = DateUtils.exportDatetimeToIso(exportRecord.createdAt),
                updatedAt = DateUtils.exportDatetimeToIso(exportRecord.updatedAt)
            )
        }
    }
    
    fun getRecordYears(): List<String> {
        val years = getAllRecords().records
            .map { it.date.substring(0, 4) }
            .distinct()
            .sorted()
        return years
    }
    
    // ========== 内容预设管理 ==========
    
    fun getPresets(): List<String> {
        val json = prefs.getString(KEY_PRESETS, null)
        return if (json != null) {
            try {
                gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            // 初始状态没有预设
            emptyList()
        }
    }
    
    private fun savePresets(presets: List<String>) {
        val json = gson.toJson(presets)
        prefs.edit().putString(KEY_PRESETS, json).apply()
    }
    
    fun addPreset(content: String): Boolean {
        if (content.isBlank()) return false
        val presets = getPresets().toMutableList()
        if (content in presets) return false
        presets.add(content)
        savePresets(presets)
        return true
    }
    
    fun updatePreset(oldContent: String, newContent: String): Boolean {
        if (newContent.isBlank()) return false
        val presets = getPresets().toMutableList()
        val index = presets.indexOf(oldContent)
        if (index == -1) return false
        if (newContent in presets && newContent != oldContent) return false
        presets[index] = newContent
        savePresets(presets)
        return true
    }
    
    fun deletePreset(content: String): Boolean {
        val presets = getPresets().toMutableList()
        if (content !in presets) return false
        presets.remove(content)
        savePresets(presets)
        return true
    }
    
    fun deletePreset(index: Int): Boolean {
        val presets = getPresets().toMutableList()
        if (index < 0 || index >= presets.size) return false
        presets.removeAt(index)
        savePresets(presets)
        return true
    }
    
    // ========== 数据库配置管理 ==========
    
    fun isDatabaseEnabled(): Boolean {
        return prefs.getBoolean(KEY_DB_ENABLED, false)
    }
    
    fun getDatabaseUrl(): String {
        return prefs.getString(KEY_DB_URL, "") ?: ""
    }
    
    fun getDatabaseKey(): String {
        return prefs.getString(KEY_DB_KEY, "") ?: ""
    }
    
    fun setDatabaseConfig(enabled: Boolean, url: String, key: String) {
        prefs.edit()
            .putBoolean(KEY_DB_ENABLED, enabled)
            .putString(KEY_DB_URL, url)
            .putString(KEY_DB_KEY, key)
            .apply()
    }

    // ========== 离线同步追踪 ==========

    fun getDirtyIds(): Set<String> {
        val json = prefs.getString(KEY_DIRTY_IDS, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Set<String>>() {}.type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun markDirty(id: String) {
        val ids = getDirtyIds().toMutableSet()
        ids.add(id)
        prefs.edit().putString(KEY_DIRTY_IDS, gson.toJson(ids)).apply()
    }

    fun markDirtyBatch(ids: List<String>) {
        val current = getDirtyIds().toMutableSet()
        current.addAll(ids)
        prefs.edit().putString(KEY_DIRTY_IDS, gson.toJson(current)).apply()
    }

    fun removeDirty(id: String) {
        val ids = getDirtyIds().toMutableSet()
        ids.remove(id)
        prefs.edit().putString(KEY_DIRTY_IDS, gson.toJson(ids)).apply()
    }

    fun removeDirtyBatch(ids: List<String>) {
        val current = getDirtyIds().toMutableSet()
        current.removeAll(ids.toSet())
        prefs.edit().putString(KEY_DIRTY_IDS, gson.toJson(current)).apply()
    }

    fun clearDirtyIds() {
        prefs.edit().remove(KEY_DIRTY_IDS).apply()
    }

    fun getDeletedIds(): Set<String> {
        val json = prefs.getString(KEY_DELETED_IDS, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Set<String>>() {}.type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun addDeletedId(id: String) {
        val ids = getDeletedIds().toMutableSet()
        ids.add(id)
        prefs.edit().putString(KEY_DELETED_IDS, gson.toJson(ids)).apply()
    }

    fun removeDeletedId(id: String) {
        val ids = getDeletedIds().toMutableSet()
        ids.remove(id)
        prefs.edit().putString(KEY_DELETED_IDS, gson.toJson(ids)).apply()
    }

    fun clearDeletedIds() {
        prefs.edit().remove(KEY_DELETED_IDS).apply()
    }

    fun hasPendingSync(): Boolean {
        return getDirtyIds().isNotEmpty() || getDeletedIds().isNotEmpty()
    }

    fun getFailedIds(): Set<String> {
        val json = prefs.getString(KEY_FAILED_IDS, null) ?: return emptySet()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<Set<String>>() {}.type) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun addFailedId(id: String) {
        val ids = getFailedIds().toMutableSet()
        ids.add(id)
        prefs.edit().putString(KEY_FAILED_IDS, gson.toJson(ids)).apply()
    }

    fun addFailedBatch(ids: List<String>) {
        val current = getFailedIds().toMutableSet()
        current.addAll(ids)
        prefs.edit().putString(KEY_FAILED_IDS, gson.toJson(current)).apply()
    }

    fun removeFailedId(id: String) {
        val ids = getFailedIds().toMutableSet()
        ids.remove(id)
        prefs.edit().putString(KEY_FAILED_IDS, gson.toJson(ids)).apply()
    }

    fun clearFailedIds() {
        prefs.edit().remove(KEY_FAILED_IDS).apply()
    }

    fun retryFailedIds() {
        val failed = getFailedIds()
        if (failed.isNotEmpty()) {
            markDirtyBatch(failed.toList())
            clearFailedIds()
        }
    }
}