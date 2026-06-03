package com.xiaojilu.app.utils

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.xiaojilu.app.model.Record
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * 数据库连接测试结果
 */
data class ConnectionTestResult(
    val success: Boolean,
    val errorType: ConnectionErrorType = ConnectionErrorType.UNKNOWN,
    val message: String = ""
)

data class StepTestResult(
    val stepName: String,
    val success: Boolean,
    val message: String = ""
)

/**
 * 数据库连接错误类型
 */
enum class ConnectionErrorType {
    NETWORK_ERROR,
    TIMEOUT_ERROR,
    DATABASE_ACCESS_ERROR,
    DATA_TYPE_MISMATCH,
    AUTHENTICATION_ERROR,
    UNKNOWN
}

object DatabaseManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    var lastUpsertError: String = ""
        private set
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(url: String, key: String, onStepResult: (StepTestResult) -> Unit): ConnectionTestResult {
        val stepResults = mutableListOf<StepTestResult>()
        var finalErrorType = ConnectionErrorType.UNKNOWN

        val networkResult = testNetworkConnection(url)
        stepResults.add(networkResult)
        onStepResult(networkResult)
        if (!networkResult.success) {
            return ConnectionTestResult(false, ConnectionErrorType.NETWORK_ERROR, "网络连接失败").also {
                reportFinal(stepResults, onStepResult)
            }
        }

        val authResult = testApiAuthentication(url, key)
        stepResults.add(authResult)
        onStepResult(authResult)
        if (!authResult.success) {
            return ConnectionTestResult(false, ConnectionErrorType.AUTHENTICATION_ERROR, "API认证失败").also {
                reportFinal(stepResults, onStepResult)
            }
        }

        val accessResult = testDataAccessStep(url, key)
        stepResults.add(accessResult)
        onStepResult(accessResult)
        if (!accessResult.success) {
            return ConnectionTestResult(false, ConnectionErrorType.DATABASE_ACCESS_ERROR, "数据访问失败").also {
                reportFinal(stepResults, onStepResult)
            }
        }

        val typeResult = testDataTypeCheck(url, key)
        stepResults.add(typeResult)
        onStepResult(typeResult)
        if (!typeResult.success) {
            return ConnectionTestResult(false, ConnectionErrorType.DATA_TYPE_MISMATCH, "数据类型不匹配").also {
                reportFinal(stepResults, onStepResult)
            }
        }

        return ConnectionTestResult(true, ConnectionErrorType.UNKNOWN, "数据库连接成功，所有检查通过").also {
            reportFinal(stepResults, onStepResult)
        }
    }

    private fun reportFinal(stepResults: List<StepTestResult>, onStepResult: (StepTestResult) -> Unit) {
        // no-op placeholder for future summary if needed
    }

    private suspend fun testNetworkConnection(url: String): StepTestResult {
        return testNetworkConnectionPublic(url)
    }

    suspend fun testNetworkConnectionPublic(url: String): StepTestResult {
        return try {
            val request = Request.Builder()
                .url("$url/health")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful || response.code in 400..499) {
                StepTestResult("网络连接", true, "成功连接到服务器")
            } else {
                StepTestResult("网络连接", false, "服务器返回 HTTP ${response.code}")
            }
        } catch (e: UnknownHostException) {
            StepTestResult("网络连接", false, "无法解析主机地址，请检查URL")
        } catch (e: ConnectException) {
            StepTestResult("网络连接", false, "无法连接到服务器")
        } catch (e: SocketTimeoutException) {
            StepTestResult("网络连接", false, "连接超时")
        } catch (e: Exception) {
            StepTestResult("网络连接", false, e.message ?: "未知网络错误")
        }
    }

    private suspend fun testApiAuthentication(url: String, key: String): StepTestResult {
        return testApiAuthenticationPublic(url, key)
    }

    suspend fun testApiAuthenticationPublic(url: String, key: String): StepTestResult {
        return try {
            val request = Request.Builder()
                .url("$url/rest/v1/records?limit=1")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            val response = client.newCall(request).execute()
            when {
                response.code == 401 || response.code == 403 -> {
                    StepTestResult("API认证", false, "API密钥无效或权限不足 (HTTP ${response.code})")
                }
                response.isSuccessful -> {
                    StepTestResult("API认证", true, "认证成功")
                }
                response.code == 404 -> {
                    StepTestResult("API认证", true, "认证通过（密钥有效，但records表尚未创建）")
                }
                else -> {
                    StepTestResult("API认证", true, "认证通过（HTTP ${response.code}）")
                }
            }
        } catch (e: Exception) {
            StepTestResult("API认证", false, e.message ?: "认证测试失败")
        }
    }

    private suspend fun testDataAccessStep(url: String, key: String): StepTestResult {
        return try {
            val request = Request.Builder()
                .url("$url/rest/v1/records?limit=1")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            val response = client.newCall(request).execute()
            when {
                response.isSuccessful -> {
                    StepTestResult("数据访问", true, "成功访问records表")
                }
                response.code == 404 -> {
                    StepTestResult("数据访问", false, "找不到records表，请先创建")
                }
                response.code == 401 || response.code == 403 -> {
                    StepTestResult("数据访问", false, "无权访问数据 (HTTP ${response.code})")
                }
                else -> {
                    StepTestResult("数据访问", false, "访问失败 HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            StepTestResult("数据访问", false, e.message ?: "数据访问测试失败")
        }
    }

    private suspend fun testDataTypeCheck(url: String, key: String): StepTestResult {
        return try {
            val request = Request.Builder()
                .url("$url/rest/v1/records?limit=1")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return StepTestResult("数据类型检查", false, "无法获取数据进行检查")
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty() || responseBody == "[]") {
                return StepTestResult("数据类型检查", false, "无法读取数据，请检查是否开启了RLS（行级安全策略）且未配置访问策略")
            }

            try {
                val recordListType = object : TypeToken<List<Record>>() {}.type
                val records = gson.fromJson<List<Record>>(responseBody, recordListType)

                if (records == null || records.isEmpty()) {
                    StepTestResult("数据类型检查", false, "无法读取数据，请检查是否开启了RLS（行级安全策略）且未配置访问策略")
                } else {
                    val record = records[0]
                    val missingFields = mutableListOf<String>()
                    if (record.id.isNullOrEmpty()) missingFields.add("id")
                    if (record.date.isNullOrEmpty()) missingFields.add("date")
                    if (record.content.isNullOrEmpty()) missingFields.add("content")
                    if (record.createdAt.isNullOrEmpty()) missingFields.add("createdAt")
                    if (record.updatedAt.isNullOrEmpty()) missingFields.add("updatedAt")

                    if (missingFields.isNotEmpty()) {
                        StepTestResult("数据类型检查", false, "字段不匹配，缺少：${missingFields.joinToString(", ")}，请检查数据库列名是否为id/date/content/createdAt/updatedAt")
                    } else {
                        StepTestResult("数据类型检查", true, "数据格式完全匹配")
                    }
                }
            } catch (e: Exception) {
                StepTestResult("数据类型检查", false, "数据格式不匹配：${e.message}")
            }
        } catch (e: Exception) {
            StepTestResult("数据类型检查", false, e.message ?: "数据类型检查失败")
        }
    }

    /**
     * 同步本地数据到数据库
     * 将本地数据去重后同步到数据库
     */
    data class SyncResult(
        val success: Boolean,
        val downloadedCount: Int = 0,
        val uploadedCount: Int = 0,
        val deletedCount: Int = 0,
        val updatedCount: Int = 0,
        val failedCount: Int = 0,
        val totalCount: Int = 0,
        val errorMessage: String = ""
    )

    suspend fun syncLocalDataToDatabase(): Boolean {
        val result = bidirectionalSync()
        return result.success
    }

    suspend fun uploadRecordsDirect(records: List<Record>): List<String> {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return emptyList()

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()
        if (url.isEmpty() || key.isEmpty()) return emptyList()

        val batchSize = 50
        val succeededIds = mutableListOf<String>()

        for (i in records.indices step batchSize) {
            val batch = records.subList(i, minOf(i + batchSize, records.size))
            try {
                val success = upsertRecords(url, key, batch)
                if (success) {
                    succeededIds.addAll(batch.map { it.id })
                } else {
                    android.util.Log.e("DatabaseManager", "Batch upload failed for records ${i}-${i + batch.size}")
                }
            } catch (e: Exception) {
                android.util.Log.e("DatabaseManager", "Batch upload exception: ${e.message}", e)
            }
        }

        return succeededIds
    }

    suspend fun processPendingSync(): SyncResult {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return SyncResult(false, errorMessage = "数据库未启用")

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()
        if (url.isEmpty() || key.isEmpty()) return SyncResult(false, errorMessage = "数据库配置不完整")

        var uploadedCount = 0
        var failedCount = 0
        var deletedCount = 0

        try {
            val dirtyIds = StorageManager.getDirtyIds()
            if (dirtyIds.isNotEmpty()) {
                val localRecords = StorageManager.getAllRecords().records
                val dirtyRecords = localRecords.filter { it.id in dirtyIds }
                if (dirtyRecords.isNotEmpty()) {
                    if (dirtyRecords.size >= 2) {
                        for ((index, record) in dirtyRecords.withIndex()) {
                            try {
                                val upsertSuccess = upsertRecords(url, key, listOf(record))
                                if (upsertSuccess) {
                                    uploadedCount++
                                    StorageManager.removeDirty(record.id)
                                    StorageManager.removeFailedId(record.id)
                                } else {
                                    failedCount++
                                    StorageManager.removeDirty(record.id)
                                    StorageManager.addFailedId(record.id)
                                }
                            } catch (e: Exception) {
                                failedCount++
                                StorageManager.removeDirty(record.id)
                                StorageManager.addFailedId(record.id)
                            }
                            if (index < dirtyRecords.size - 1) {
                                kotlinx.coroutines.delay(3000L)
                            }
                        }
                    } else {
                        for (record in dirtyRecords) {
                            try {
                                val upsertSuccess = upsertRecords(url, key, listOf(record))
                                if (upsertSuccess) {
                                    uploadedCount++
                                    StorageManager.removeDirty(record.id)
                                    StorageManager.removeFailedId(record.id)
                                } else {
                                    failedCount++
                                    StorageManager.removeDirty(record.id)
                                    StorageManager.addFailedId(record.id)
                                }
                            } catch (e: Exception) {
                                failedCount++
                                StorageManager.removeDirty(record.id)
                                StorageManager.addFailedId(record.id)
                            }
                        }
                    }
                } else {
                    for (id in dirtyIds) {
                        StorageManager.removeDirty(id)
                    }
                }
            }

            val deletedIds = StorageManager.getDeletedIds()
            if (deletedIds.isNotEmpty()) {
                for (id in deletedIds) {
                    val success = deleteRecordFromDatabase(id)
                    if (success) {
                        deletedCount++
                        StorageManager.removeDeletedId(id)
                    }
                }
            }

            return SyncResult(
                success = true,
                uploadedCount = uploadedCount,
                deletedCount = deletedCount,
                failedCount = failedCount,
                totalCount = StorageManager.getAllRecords().records.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return SyncResult(false, errorMessage = e.message ?: "同步失败")
        }
    }

    suspend fun bidirectionalSync(): SyncResult {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return SyncResult(false, errorMessage = "数据库未启用")

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()
        if (url.isEmpty() || key.isEmpty()) return SyncResult(false, errorMessage = "数据库配置不完整")

        return try {
            val pendingResult = processPendingSync()
            if (!pendingResult.success) return pendingResult

            val localRecords = StorageManager.getAllRecords().records
            val remoteRecords = fetchAllRecords(url, key)
            val deletedIds = StorageManager.getDeletedIds()

            val localMap = localRecords.associateBy { it.id }
            val remoteMap = remoteRecords.associateBy { it.id }

            var downloadedCount = 0
            var updatedCount = 0

            val newFromRemote = remoteRecords.filter { it.id !in localMap && it.id !in deletedIds }
            if (newFromRemote.isNotEmpty()) {
                downloadedCount = StorageManager.importFromRemote(newFromRemote)
            }

            val conflictIds = remoteMap.keys.intersect(localMap.keys)
            for (id in conflictIds) {
                val local = localMap[id]!!
                val remote = remoteMap[id]!!
                if (remote.updatedAt > local.updatedAt) {
                    val merged = local.copy(
                        content = remote.content,
                        date = remote.date,
                        updatedAt = remote.updatedAt
                    )
                    StorageManager.updateRecordFromRemote(id, merged)
                    updatedCount++
                } else if (local.updatedAt > remote.updatedAt) {
                    StorageManager.markDirty(id)
                }
            }

            val toUpload = localRecords.filter { it.id !in remoteMap }
            var uploadedCount = 0
            if (toUpload.isNotEmpty()) {
                val upsertSuccess = upsertRecords(url, key, toUpload)
                if (upsertSuccess) {
                    uploadedCount = toUpload.size
                    for (record in toUpload) {
                        StorageManager.removeDirty(record.id)
                    }
                }
            }

            val finalPending = processPendingSync()

            SyncResult(
                success = true,
                downloadedCount = downloadedCount,
                uploadedCount = uploadedCount + finalPending.uploadedCount,
                deletedCount = finalPending.deletedCount,
                updatedCount = updatedCount,
                totalCount = StorageManager.getAllRecords().records.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SyncResult(false, errorMessage = e.message ?: "同步失败")
        }
    }

    /**
     * 从数据库获取所有记录
     * 适配Supabase API格式
     */
    private suspend fun fetchAllRecords(url: String, key: String): List<Record> {
        val request = Request.Builder()
            .url("$url/rest/v1/records")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Prefer", "return=representation")
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val responseBody = response.body?.string() ?: return emptyList()
        val recordListType = object : TypeToken<List<Record>>() {}.type
        return gson.fromJson<List<Record>>(responseBody, recordListType) ?: emptyList()
    }

    /**
     * 批量上传记录到数据库
     * 适配Supabase API格式
     */
    private suspend fun uploadRecords(url: String, key: String, records: List<Record>): Boolean {
        return upsertRecords(url, key, records)
    }

    suspend fun upsertSingleRecord(record: Record): Boolean {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return false

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()
        if (url.isEmpty() || key.isEmpty()) return false

        return upsertRecords(url, key, listOf(record))
    }

    private suspend fun upsertRecords(url: String, key: String, records: List<Record>): Boolean {
        val json = gson.toJson(records)
        android.util.Log.d("DatabaseManager", "upsertRecords JSON: $json")

        val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$url/rest/v1/records")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Prefer", "return=minimal,resolution=merge-duplicates")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "no error body"
            lastUpsertError = "HTTP ${response.code}: $errorBody"
            android.util.Log.e("DatabaseManager", "upsertRecords failed: code=${response.code}, body=$errorBody")
        } else {
            lastUpsertError = ""
        }
        return response.isSuccessful
    }

    /**
     * 添加记录到数据库
     * 适配Supabase API格式
     */
    suspend fun addRecordToDatabase(record: Record): Boolean {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return true // 如果未启用数据库，返回true不影响本地操作

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()

        if (url.isEmpty() || key.isEmpty()) return false

        return try {
            val json = gson.toJson(record)
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$url/rest/v1/records")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Prefer", "return=minimal,resolution=merge-duplicates")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "no error body"
                lastUpsertError = "HTTP ${response.code}: $errorBody"
                android.util.Log.e("DatabaseManager", "addRecordToDatabase failed: code=${response.code}, body=$errorBody")
            } else {
                lastUpsertError = ""
            }
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 更新数据库中的记录
     * 适配Supabase API格式
     */
    suspend fun updateRecordInDatabase(id: String, record: Record): Boolean {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return true

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()

        if (url.isEmpty() || key.isEmpty()) return false

        return try {
            val json = gson.toJson(record)
            val requestBody = json.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url("$url/rest/v1/records?id=eq.$id")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .patch(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从数据库删除记录
     * 适配Supabase API格式
     */
    suspend fun deleteRecordFromDatabase(id: String): Boolean {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return true

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()

        if (url.isEmpty() || key.isEmpty()) return false

        return try {
            val request = Request.Builder()
                .url("$url/rest/v1/records?id=eq.$id")
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从数据库获取记录
     */
    suspend fun fetchRecordsFromDatabase(): List<Record> {
        val isEnabled = StorageManager.isDatabaseEnabled()
        if (!isEnabled) return emptyList()

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()

        if (url.isEmpty() || key.isEmpty()) return emptyList()

        return fetchAllRecords(url, key)
    }
}