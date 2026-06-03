package com.xiaojilu.app

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.xiaojilu.app.utils.StorageManager

class PasswordActivity : AppCompatActivity() {

    private lateinit var appIcon: ImageView
    private lateinit var passwordInput: TextInputEditText
    private lateinit var btnUnlock: com.google.android.material.button.MaterialButton

    // 密码重置相关变量
    private var clickCount = 0
    private var firstClickTime = 0L
    private val RESET_PASSWORD = "000"
    private val MAX_CLICK_COUNT = 10
    private val TIME_WINDOW_MS = 5000L // 5秒

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)

        // 初始化存储管理器
        StorageManager.init(this)

        // 设置状态栏为白色，与主题颜色对齐
        window.statusBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        initViews()
        setupListeners()

        if (!StorageManager.isPasswordEnabled()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        showKeyboard()
    }

    private fun initViews() {
        appIcon = findViewById(R.id.iv_app_icon)
        passwordInput = findViewById(R.id.password_input)
        btnUnlock = findViewById(R.id.btn_unlock)
    }

    private fun setupListeners() {
        // 应用图标点击监听 - 密码重置功能
        appIcon.setOnClickListener {
            handleIconClick()
        }

        btnUnlock.setOnClickListener {
            verifyPassword()
        }

        passwordInput.setOnEditorActionListener { _, _, _ ->
            verifyPassword()
            true
        }
    }

    private fun handleIconClick() {
        val currentTime = System.currentTimeMillis()

        // 如果是第一次点击，记录时间
        if (clickCount == 0) {
            firstClickTime = currentTime
        }

        // 检查是否在时间窗口内
        if (currentTime - firstClickTime > TIME_WINDOW_MS) {
            // 超过时间窗口，重置计数
            clickCount = 0
            firstClickTime = currentTime
        }

        clickCount++

        // 检查是否达到重置条件
        if (clickCount >= MAX_CLICK_COUNT) {
            resetPassword()
            clickCount = 0
        }
    }

    private fun resetPassword() {
        // 重置密码为默认值
        StorageManager.changePassword(RESET_PASSWORD)
        Toast.makeText(this, "❤", Toast.LENGTH_SHORT).show()

        // 清空密码输入框并聚焦
        passwordInput.text?.clear()
        passwordInput.requestFocus()
    }

    private fun verifyPassword() {
        val password = passwordInput.text.toString().trim()

        if (password.isEmpty()) {
            Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
            return
        }

        if (StorageManager.checkPassword(password)) {
            // 密码正确，跳转到主界面
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } else {
            // 密码错误，清空输入框并提示
            passwordInput.text?.clear()
            Toast.makeText(this, "密码错误，请重试", Toast.LENGTH_SHORT).show()
            passwordInput.requestFocus()
        }
    }

    private fun showKeyboard() {
        passwordInput.postDelayed({
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(passwordInput, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }

    override fun onResume() {
        super.onResume()
        // 重新设置状态栏为白色，与主题颜色对齐
        window.statusBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
}