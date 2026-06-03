package com.xiaojilu.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import com.xiaojilu.app.utils.DatabaseManager
import com.xiaojilu.app.utils.StorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DatabaseConfigActivity : AppCompatActivity() {

    private lateinit var switchEnableDb: Switch
    private lateinit var etDbUrl: EditText
    private lateinit var etDbKey: EditText
    private lateinit var btnTestConnection: Button
    private lateinit var btnSave: Button
    private lateinit var cardMain: CardView
    private lateinit var cardStatus: CardView
    private lateinit var layoutSteps: LinearLayout

    private var isTestPassed = false
    private var hasSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_database_config)

        window.statusBarColor = Color.parseColor("#F9FAFB")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        initViews()
        setupToolbar()
        loadCurrentConfig()
        setupListeners()
        updateSaveButtonState()
    }

    private fun initViews() {
        switchEnableDb = findViewById(R.id.switch_enable_db)
        etDbUrl = findViewById(R.id.et_db_url)
        etDbKey = findViewById(R.id.et_db_key)
        btnTestConnection = findViewById(R.id.btn_test_connection)
        btnSave = findViewById(R.id.btn_save)
        cardMain = findViewById(R.id.card_main)
        cardStatus = findViewById(R.id.card_status)
        layoutSteps = findViewById(R.id.layout_steps)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadCurrentConfig() {
        val isEnabled = StorageManager.isDatabaseEnabled()
        switchEnableDb.isChecked = isEnabled

        if (isEnabled) {
            etDbUrl.setText(StorageManager.getDatabaseUrl())
            etDbKey.setText(StorageManager.getDatabaseKey())
            enableInputs(true)
        } else {
            enableInputs(false)
        }
    }

    private fun setupListeners() {
        switchEnableDb.setOnCheckedChangeListener { _, isChecked ->
            enableInputs(isChecked)
            if (!isChecked) {
                hideStatus()
                resetTestState()
            } else {
                resetTestState()
            }
        }

        btnTestConnection.setOnClickListener {
            testConnection()
        }

        btnSave.setOnClickListener {
            if (!isSaveButtonEnabled()) {
                Toast.makeText(this, "请先测试数据库连接并确保全部通过", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveConfig()
        }
    }

    private fun enableInputs(enabled: Boolean) {
        etDbUrl.isEnabled = enabled
        etDbKey.isEnabled = enabled
        btnTestConnection.isEnabled = enabled

        val alpha = if (enabled) 1.0f else 0.5f
        cardMain.alpha = alpha
    }

    private fun testConnection() {
        val url = etDbUrl.text.toString().trim()
        val key = etDbKey.text.toString().trim()

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "请输入数据库地址和密钥", Toast.LENGTH_SHORT).show()
            return
        }

        layoutSteps.removeAllViews()
        cardStatus.visibility = View.VISIBLE
        btnTestConnection.isEnabled = false
        resetTestState()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var allPassed = true
                val result = DatabaseManager.testConnection(url, key) { stepResult ->
                    runOnUiThread { 
                        addStepView(stepResult) 
                        if (!stepResult.success) {
                            allPassed = false
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    btnTestConnection.isEnabled = true
                    isTestPassed = allPassed && result.success
                    hasSaved = false
                    updateSaveButtonState()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addStepView(
                        com.xiaojilu.app.utils.StepTestResult(
                            "异常",
                            false,
                            e.message ?: "未知异常"
                        )
                    )
                    btnTestConnection.isEnabled = true
                    isTestPassed = false
                    hasSaved = false
                    updateSaveButtonState()
                }
            }
        }
    }

    private fun addStepView(stepResult: com.xiaojilu.app.utils.StepTestResult) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (layoutSteps.childCount > 0) 12 else 0
            }
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                marginEnd = 12
            }
            setImageResource(
                if (stepResult.success) android.R.drawable.presence_online
                else android.R.drawable.presence_offline
            )
        }

        val textColor = if (stepResult.success) Color.parseColor("#059669") else Color.parseColor("#DC2626")
        val statusTag = if (stepResult.success) "OK" else "FAIL"

        val text = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            text = "${stepResult.stepName}  $statusTag\n${stepResult.message}"
            textSize = 14f
            setTextColor(textColor)
        }

        row.addView(icon)
        row.addView(text)
        layoutSteps.addView(row)
    }

    private fun saveConfig() {
        val isEnabled = switchEnableDb.isChecked
        val url = etDbUrl.text.toString().trim()
        val key = etDbKey.text.toString().trim()

        if (isEnabled && (url.isEmpty() || key.isEmpty())) {
            Toast.makeText(this, "启用数据库时必须输入地址和密钥", Toast.LENGTH_SHORT).show()
            return
        }

        StorageManager.setDatabaseConfig(isEnabled, url, key)

        hasSaved = true
        updateSaveButtonState()

        if (isEnabled) {
            btnSave.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val result = DatabaseManager.bidirectionalSync()
                    withContext(Dispatchers.Main) {
                        btnSave.isEnabled = true
                        if (result.success) {
                            val msg = buildString {
                                append("数据同步完成\n")
                                if (result.downloadedCount > 0) append("从数据库下载：${result.downloadedCount} 条\n")
                                if (result.uploadedCount > 0) append("上传到数据库：${result.uploadedCount} 条\n")
                                if (result.updatedCount > 0) append("冲突更新（云端更新）：${result.updatedCount} 条\n")
                                if (result.deletedCount > 0) append("云端删除：${result.deletedCount} 条\n")
                                append("数据总计：${result.totalCount} 条")
                            }
                            Toast.makeText(this@DatabaseConfigActivity, msg, Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@DatabaseConfigActivity,
                                "配置已保存，但数据同步失败：${result.errorMessage}",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        btnSave.isEnabled = true
                        Toast.makeText(this@DatabaseConfigActivity,
                            "配置已保存，但数据同步出错: ${e.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun hideStatus() {
        cardStatus.visibility = View.GONE
    }

    private fun resetTestState() {
        isTestPassed = false
        hasSaved = false
        updateSaveButtonState()
    }

    private fun isSaveButtonEnabled(): Boolean {
        return isTestPassed && !hasSaved
    }

    private fun updateSaveButtonState() {
        if (isSaveButtonEnabled()) {
            btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#10B981"))
        } else {
            btnSave.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D1D5DB"))
        }
    }

    override fun onResume() {
        super.onResume()
        window.statusBarColor = Color.parseColor("#F9FAFB")
    }
}
