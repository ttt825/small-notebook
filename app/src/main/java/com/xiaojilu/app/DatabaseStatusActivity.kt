package com.xiaojilu.app

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.xiaojilu.app.utils.DatabaseManager
import com.xiaojilu.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseStatusActivity : AppCompatActivity() {

    private lateinit var tvDbUrl: TextView
    private lateinit var tvDbKey: TextView
    private lateinit var ivToggleKey: ImageView
    private lateinit var tvNetworkStatus: TextView
    private lateinit var tvDatabaseStatus: TextView
    private lateinit var tvLocalCount: TextView
    private lateinit var tvRemoteCount: TextView
    private lateinit var tvPendingCount: TextView
    private lateinit var tvSuccessCount: TextView
    private lateinit var tvFailedCount: TextView
    private lateinit var tvSyncDetail: TextView
    private lateinit var btnRefreshConnection: Button
    private lateinit var btnRefreshStats: Button
    private lateinit var btnRetryFailed: Button
    private lateinit var btnSyncNow: Button
    private lateinit var btnProcessPending: Button
    private lateinit var ivClearQueue: ImageView

    private var keyVisible = false
    private var lastSyncTime = 0L
    private var lastRefreshConnectionTime = 0L
    private var lastRefreshStatsTime = 0L
    private var isSyncing = false
    private var isProcessingQueue = false
    private var successCount = 0
    private var failedCount = 0
    private var currentLocalCount = -1
    private var currentRemoteCount = -1

    private val uiHandler = Handler(Looper.getMainLooper())
    private val queueUpdateRunnable = object : Runnable {
        override fun run() {
            updateQueueCounts()
            if (isProcessingQueue || StorageManager.getDirtyIds().isNotEmpty()) {
                uiHandler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_database_status)

        window.statusBarColor = android.graphics.Color.parseColor("#F8FAFC")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        initViews()
        setupToolbar()
        setupToggleKey()
        setupLongPressCopy()
        setupButtons()
        loadStaticInfo()
        loadDynamicInfo()
        updateQueueCounts()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(queueUpdateRunnable)
    }

    private fun initViews() {
        tvDbUrl = findViewById(R.id.tv_db_url)
        tvDbKey = findViewById(R.id.tv_db_key)
        ivToggleKey = findViewById(R.id.iv_toggle_key)
        tvNetworkStatus = findViewById(R.id.tv_network_status)
        tvDatabaseStatus = findViewById(R.id.tv_database_status)
        tvLocalCount = findViewById(R.id.tv_local_count)
        tvRemoteCount = findViewById(R.id.tv_remote_count)
        tvPendingCount = findViewById(R.id.tv_pending_count)
        tvSuccessCount = findViewById(R.id.tv_success_count)
        tvFailedCount = findViewById(R.id.tv_failed_count)
        tvSyncDetail = findViewById(R.id.tv_sync_detail)
        btnRefreshConnection = findViewById(R.id.btn_refresh_connection)
        btnRefreshStats = findViewById(R.id.btn_refresh_stats)
        btnRetryFailed = findViewById(R.id.btn_retry_failed)
        btnSyncNow = findViewById(R.id.btn_sync_now)
        btnProcessPending = findViewById(R.id.btn_process_pending)
        ivClearQueue = findViewById(R.id.iv_clear_queue)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupToggleKey() {
        ivToggleKey.setOnClickListener {
            keyVisible = !keyVisible
            updateKeyDisplay()
        }
    }

    private fun setupLongPressCopy() {
        tvDbUrl.setOnLongClickListener {
            val url = StorageManager.getDatabaseUrl()
            if (url.isNotEmpty()) {
                copyToClipboard("数据库地址", url)
                Toast.makeText(this, "数据库地址已复制", Toast.LENGTH_SHORT).show()
            }
            true
        }

        tvDbKey.setOnLongClickListener {
            val key = StorageManager.getDatabaseKey()
            if (key.isNotEmpty()) {
                copyToClipboard("API Key", key)
                Toast.makeText(this, "API Key已复制", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
    }

    private fun setupButtons() {
        btnRefreshConnection.setOnClickListener {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefreshConnectionTime
            if (elapsed < 5000L) {
                val remaining = (5000L - elapsed) / 1000 + 1
                Toast.makeText(this, "请${remaining}秒后重试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastRefreshConnectionTime = now
            setButtonGray(btnRefreshConnection)
            uiHandler.postDelayed({ setButtonSuccess(btnRefreshConnection) }, 5000L)
            loadDynamicInfo()
        }

        btnRefreshStats.setOnClickListener {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefreshStatsTime
            if (elapsed < 5000L) {
                val remaining = (5000L - elapsed) / 1000 + 1
                Toast.makeText(this, "请${remaining}秒后重试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastRefreshStatsTime = now
            setButtonGray(btnRefreshStats)
            uiHandler.postDelayed({ setButtonSuccess(btnRefreshStats) }, 5000L)
            refreshStats()
        }

        btnRetryFailed.setOnClickListener {
            val failedIds = StorageManager.getFailedIds()
            if (failedIds.isEmpty()) {
                Toast.makeText(this, "没有失败记录需要重试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            StorageManager.retryFailedIds()
            successCount = 0
            failedCount = 0
            updateQueueCounts()
            updateSyncNowButton()
            tvSyncDetail.text = "已将${failedIds.size}条失败记录重新加入更新队列"
        }

        btnSyncNow.setOnClickListener {
            handleSyncNowClick()
        }

        btnProcessPending.setOnClickListener {
            if (isProcessingQueue) {
                Toast.makeText(this, "正在处理中，请稍候", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dirtyIds = StorageManager.getDirtyIds()
            if (dirtyIds.isEmpty()) {
                Toast.makeText(this, "没有待更新的记录", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val now = System.currentTimeMillis()
            val elapsed = now - lastSyncTime
            if (elapsed < 5000L) {
                val remaining = (5000L - elapsed) / 1000 + 1
                Toast.makeText(this, "请${remaining}秒后重试", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lastSyncTime = now
            startProcessQueue()
        }

        ivClearQueue.setOnClickListener {
            val pendingCount = StorageManager.getDirtyIds().size
            val currentFailedCount = StorageManager.getFailedIds().size
            val totalCount = pendingCount + currentFailedCount + successCount
            if (totalCount == 0) {
                Toast.makeText(this, "无数据", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("确认清除")
                .setMessage("确定要清除全部待更新/成功/失败数据吗？")
                .setPositiveButton("确定") { _, _ ->
                    StorageManager.clearDirtyIds()
                    StorageManager.clearFailedIds()
                    successCount = 0
                    failedCount = 0
                    updateQueueCounts()
                    tvSyncDetail.text = "暂无更新条目"
                    updateSyncNowButton()
                    Toast.makeText(this, "已清除全部队列数据", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun handleSyncNowClick() {
        val dirtyIds = StorageManager.getDirtyIds()
        val failedIds = StorageManager.getFailedIds()
        val hasNetwork = isNetworkAvailable()

        if (dirtyIds.isNotEmpty() || failedIds.isNotEmpty()) {
            Toast.makeText(this, "更新队列有待更新条目", Toast.LENGTH_SHORT).show()
            return
        }

        if (!hasNetwork) {
            Toast.makeText(this, "无网络连接", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentLocalCount >= 0 && currentRemoteCount >= 0 && currentLocalCount == currentRemoteCount) {
            Toast.makeText(this, "数据条目一致，无需同步", Toast.LENGTH_SHORT).show()
            return
        }

        if (!StorageManager.isDatabaseEnabled()) {
            Toast.makeText(this, "数据库未启用", Toast.LENGTH_SHORT).show()
            return
        }

        showSyncDialog()
    }

    private fun showSyncDialog() {
        val options = arrayOf("本地覆盖云端", "云端覆盖本地")
        AlertDialog.Builder(this)
            .setTitle("选择同步方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> syncLocalToRemote()
                    1 -> syncRemoteToLocal()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun syncLocalToRemote() {
        if (isSyncing) return
        isSyncing = true
        btnSyncNow.isEnabled = false
        btnSyncNow.alpha = 0.5f
        tvSyncDetail.text = "正在同步：本地覆盖云端..."

        GlobalScope.launch(Dispatchers.IO) {
            var uploadCount = 0
            var deleteCount = 0
            var failCount = 0

            try {
                val localRecords = StorageManager.getAllRecords().records
                val remoteRecords = DatabaseManager.fetchRecordsFromDatabase()
                val localMap = localRecords.associateBy { it.id }
                val remoteMap = remoteRecords.associateBy { it.id }

                val toUpload = localRecords.filter { it.id !in remoteMap }
                for (record in toUpload) {
                    try {
                        val success = DatabaseManager.upsertSingleRecord(record)
                        if (success) {
                            uploadCount++
                            StorageManager.removeDirty(record.id)
                        } else {
                            failCount++
                            StorageManager.markDirty(record.id)
                        }
                    } catch (e: Exception) {
                        failCount++
                        StorageManager.markDirty(record.id)
                    }
                }

                val toUpdate = localRecords.filter { it.id in remoteMap }
                for (record in toUpdate) {
                    try {
                        val success = DatabaseManager.upsertSingleRecord(record)
                        if (success) {
                            uploadCount++
                            StorageManager.removeDirty(record.id)
                        } else {
                            failCount++
                            StorageManager.markDirty(record.id)
                        }
                    } catch (e: Exception) {
                        failCount++
                        StorageManager.markDirty(record.id)
                    }
                }

                val toDelete = remoteRecords.filter { it.id !in localMap }
                for (record in toDelete) {
                    try {
                        val success = DatabaseManager.deleteRecordFromDatabase(record.id)
                        if (success) {
                            deleteCount++
                        } else {
                            failCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }
            } catch (e: Exception) {
                failCount++
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    isSyncing = false
                    btnSyncNow.isEnabled = true
                    btnSyncNow.alpha = 1.0f
                    if (failCount > 0) {
                        tvSyncDetail.text = "同步完成：上传${uploadCount}条，删除${deleteCount}条，失败${failCount}条"
                    } else {
                        tvSyncDetail.text = "同步完成：上传${uploadCount}条，删除${deleteCount}条"
                    }
                    refreshStats()
                    updateQueueCounts()
                    updateSyncNowButton()
                }
            }
        }
    }

    private fun syncRemoteToLocal() {
        if (isSyncing) return
        isSyncing = true
        btnSyncNow.isEnabled = false
        btnSyncNow.alpha = 0.5f
        tvSyncDetail.text = "正在同步：云端覆盖本地..."

        GlobalScope.launch(Dispatchers.IO) {
            var downloadCount = 0
            var deleteCount = 0
            var failCount = 0

            try {
                val localRecords = StorageManager.getAllRecords().records
                val remoteRecords = DatabaseManager.fetchRecordsFromDatabase()
                val localMap = localRecords.associateBy { it.id }
                val remoteMap = remoteRecords.associateBy { it.id }

                val newFromRemote = remoteRecords.filter { it.id !in localMap }
                for (record in newFromRemote) {
                    StorageManager.importFromRemote(listOf(record))
                    downloadCount++
                }

                val toUpdate = remoteRecords.filter { it.id in localMap }
                for (record in toUpdate) {
                    StorageManager.updateRecordFromRemote(record.id, record)
                    downloadCount++
                }

                val toDeleteLocal = localRecords.filter { it.id !in remoteMap }
                for (record in toDeleteLocal) {
                    StorageManager.deleteRecordLocalOnly(record.id)
                    deleteCount++
                }

                StorageManager.clearDirtyIds()
                StorageManager.clearFailedIds()
            } catch (e: Exception) {
                failCount++
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    isSyncing = false
                    btnSyncNow.isEnabled = true
                    btnSyncNow.alpha = 1.0f
                    if (failCount > 0) {
                        tvSyncDetail.text = "同步完成：下载${downloadCount}条，删除本地${deleteCount}条，失败${failCount}条"
                    } else {
                        tvSyncDetail.text = "同步完成：下载${downloadCount}条，删除本地${deleteCount}条"
                    }
                    refreshStats()
                    updateQueueCounts()
                    updateSyncNowButton()
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun updateSyncNowButton() {
        val dirtyIds = StorageManager.getDirtyIds()
        val failedIds = StorageManager.getFailedIds()
        val hasNetwork = isNetworkAvailable()
        val countsMatch = currentLocalCount >= 0 && currentRemoteCount >= 0 && currentLocalCount == currentRemoteCount

        val canSync = dirtyIds.isEmpty() && failedIds.isEmpty() && hasNetwork && !countsMatch && StorageManager.isDatabaseEnabled()

        if (canSync) {
            setButtonSuccess(btnSyncNow)
        } else {
            setButtonGray(btnSyncNow)
        }
    }

    private fun startProcessQueue() {
        if (!StorageManager.isDatabaseEnabled()) {
            Toast.makeText(this, "数据库未启用", Toast.LENGTH_SHORT).show()
            return
        }

        val dirtyIds = StorageManager.getDirtyIds()
        if (dirtyIds.isEmpty()) {
            tvSyncDetail.text = "暂无更新条目"
            return
        }

        isProcessingQueue = true
        successCount = 0
        failedCount = 0
        StorageManager.clearFailedIds()
        btnProcessPending.isEnabled = false
        btnProcessPending.alpha = 0.5f

        uiHandler.post(queueUpdateRunnable)

        GlobalScope.launch(Dispatchers.IO) {
            val localRecords = StorageManager.getAllRecords().records
            val dirtyRecords = localRecords.filter { it.id in dirtyIds }

            for ((index, record) in dirtyRecords.withIndex()) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        tvSyncDetail.text = "正在更新ID：${record.id}"
                    }
                }

                var recordSuccess = false
                var errorMsg = ""
                try {
                    val upsertSuccess = DatabaseManager.upsertSingleRecord(record)
                    recordSuccess = upsertSuccess
                    if (upsertSuccess) {
                        successCount++
                        StorageManager.removeDirty(record.id)
                        StorageManager.removeFailedId(record.id)
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                tvSyncDetail.text = "ID：${record.id} 更新成功"
                            }
                        }
                    } else {
                        failedCount++
                        errorMsg = DatabaseManager.lastUpsertError
                        StorageManager.removeDirty(record.id)
                        StorageManager.addFailedId(record.id)
                        withContext(Dispatchers.Main) {
                            if (!isFinishing && !isDestroyed) {
                                tvSyncDetail.text = "ID：${record.id} 更新失败\n$errorMsg"
                            }
                        }
                    }
                } catch (e: Exception) {
                    failedCount++
                    errorMsg = e.message ?: "未知异常"
                    StorageManager.removeDirty(record.id)
                    StorageManager.addFailedId(record.id)
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            tvSyncDetail.text = "ID：${record.id} 更新失败\n$errorMsg"
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        updateQueueCounts()
                    }
                }

                if (index < dirtyRecords.size - 1) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            val statusText = if (recordSuccess) "更新成功" else "更新失败"
                            tvSyncDetail.text = "ID：${record.id} ${statusText}\n3秒后进行下次更新"
                        }
                    }
                    kotlinx.coroutines.delay(3000L)
                }
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    isProcessingQueue = false
                    btnProcessPending.isEnabled = true
                    btnProcessPending.alpha = 1.0f
                    updateQueueCounts()
                    if (failedCount > 0) {
                        tvSyncDetail.text = "处理完成：成功${successCount}条，失败${failedCount}条"
                    } else if (successCount > 0) {
                        tvSyncDetail.text = "处理完成：全部${successCount}条更新成功"
                    } else {
                        tvSyncDetail.text = "暂无更新条目"
                    }
                    refreshStats()
                    updateSyncNowButton()
                }
            }
        }
    }

    private fun updateQueueCounts() {
        val pendingCount = StorageManager.getDirtyIds().size
        val currentFailedCount = StorageManager.getFailedIds().size
        tvPendingCount.text = "${pendingCount} 条数据"
        tvSuccessCount.text = "${successCount} 条数据"
        tvFailedCount.text = "${currentFailedCount} 条数据"
        updateRetryFailedButton()
        updateProcessPendingButton()
    }

    private fun setButtonGray(button: Button) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.muted_light))
        button.setTextColor(getColor(R.color.white))
    }

    private fun setButtonSuccess(button: Button) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.primary))
        button.setTextColor(getColor(R.color.white))
    }

    private fun setButtonWarning(button: Button) {
        button.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.warning))
        button.setTextColor(getColor(R.color.white))
    }

    private fun updateRetryFailedButton() {
        val failedIds = StorageManager.getFailedIds()
        if (failedIds.isNotEmpty()) {
            setButtonWarning(btnRetryFailed)
        } else {
            setButtonGray(btnRetryFailed)
        }
    }

    private fun updateProcessPendingButton() {
        val dirtyIds = StorageManager.getDirtyIds()
        if (dirtyIds.isNotEmpty() && !isProcessingQueue) {
            setButtonSuccess(btnProcessPending)
        } else {
            setButtonGray(btnProcessPending)
        }
    }

    private fun refreshStats() {
        tvLocalCount.text = "- 条数据"
        tvRemoteCount.text = "- 条数据"
        currentLocalCount = -1
        currentRemoteCount = -1
        updateSyncNowButton()

        if (!StorageManager.isDatabaseEnabled()) {
            val localCount = StorageManager.getAllRecords().records.size
            currentLocalCount = localCount
            tvLocalCount.text = "${localCount} 条数据"
            tvRemoteCount.text = "-"
            updateSyncNowButton()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            var remoteCount = -1
            try {
                remoteCount = DatabaseManager.fetchRecordsFromDatabase().size
            } catch (_: Exception) {
                remoteCount = -1
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    val localCount = StorageManager.getAllRecords().records.size
                    currentLocalCount = localCount
                    currentRemoteCount = remoteCount
                    tvLocalCount.text = "${localCount} 条数据"

                    if (remoteCount >= 0) {
                        tvRemoteCount.text = "${remoteCount} 条数据"
                    } else {
                        tvRemoteCount.text = "获取失败"
                    }
                    updateSyncNowButton()
                }
            }
        }
    }

    private fun updateKeyDisplay() {
        val key = StorageManager.getDatabaseKey()
        if (keyVisible) {
            tvDbKey.text = key
            ivToggleKey.setImageResource(R.drawable.ic_visibility_on)
        } else {
            tvDbKey.text = maskKey(key)
            ivToggleKey.setImageResource(R.drawable.ic_visibility_off)
        }
    }

    private fun maskKey(key: String): String {
        if (key.isEmpty()) return "-"
        if (key.length <= 8) return "*".repeat(key.length)
        return key.substring(0, 4) + "*".repeat(key.length - 8) + key.substring(key.length - 4)
    }

    private fun loadStaticInfo() {
        val url = StorageManager.getDatabaseUrl()
        tvDbUrl.text = url.ifEmpty { "-" }
        updateKeyDisplay()

        val localCount = StorageManager.getAllRecords().records.size
        currentLocalCount = localCount
        tvLocalCount.text = "${localCount} 条数据"
    }

    private fun loadDynamicInfo() {
        if (!StorageManager.isDatabaseEnabled()) {
            tvNetworkStatus.text = "未配置"
            tvNetworkStatus.setTextColor(getColor(R.color.muted))
            tvDatabaseStatus.text = "未配置"
            tvDatabaseStatus.setTextColor(getColor(R.color.muted))
            tvRemoteCount.text = "-"
            updateSyncNowButton()
            return
        }

        val url = StorageManager.getDatabaseUrl()
        val key = StorageManager.getDatabaseKey()

        tvNetworkStatus.text = "检测中..."
        tvNetworkStatus.setTextColor(getColor(R.color.muted))
        tvDatabaseStatus.text = "检测中..."
        tvDatabaseStatus.setTextColor(getColor(R.color.muted))

        GlobalScope.launch(Dispatchers.IO) {
            var networkOk = false
            var dbOk = false
            var dbMsg = ""
            var remoteCount = -1

            try {
                val networkResult = DatabaseManager.testNetworkConnectionPublic(url)
                networkOk = networkResult.success

                if (networkOk) {
                    try {
                        val authResult = DatabaseManager.testApiAuthenticationPublic(url, key)
                        if (authResult.success) {
                            dbOk = true
                            dbMsg = "成功连接"
                            try {
                                val records = DatabaseManager.fetchRecordsFromDatabase()
                                remoteCount = records.size
                            } catch (_: Exception) {
                                remoteCount = -1
                            }
                        } else {
                            dbOk = false
                            dbMsg = "连接失败：${authResult.message}"
                        }
                    } catch (e: Exception) {
                        dbOk = false
                        dbMsg = "连接失败：${e.message}"
                    }
                } else {
                    dbMsg = "网络不可用"
                }
            } catch (_: Exception) {
                networkOk = false
                dbMsg = "网络不可用"
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                    if (networkOk) {
                        tvNetworkStatus.text = "成功连接"
                        tvNetworkStatus.setTextColor(getColor(R.color.primary))
                    } else {
                        tvNetworkStatus.text = "连接失败"
                        tvNetworkStatus.setTextColor(getColor(R.color.error))
                    }

                    if (dbOk) {
                        tvDatabaseStatus.text = "成功连接"
                        tvDatabaseStatus.setTextColor(getColor(R.color.primary))
                    } else {
                        tvDatabaseStatus.text = dbMsg
                        tvDatabaseStatus.setTextColor(getColor(R.color.error))
                    }

                    currentRemoteCount = remoteCount
                    if (remoteCount >= 0) {
                        tvRemoteCount.text = "${remoteCount} 条数据"
                    } else {
                        tvRemoteCount.text = "获取失败"
                    }
                    updateSyncNowButton()
                }
            }
        }
    }
}
