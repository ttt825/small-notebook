package com.xiaojilu.app

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.textfield.TextInputEditText
import com.xiaojilu.app.utils.StorageManager

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var oldPasswordInput: TextInputEditText
    private lateinit var newPasswordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var btnConfirm: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        StorageManager.init(this)

        window.statusBarColor = android.graphics.Color.parseColor("#F9FAFB")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        initViews()
        setupToolbar()
        setupListeners()
    }

    private fun initViews() {
        oldPasswordInput = findViewById(R.id.old_password_input)
        newPasswordInput = findViewById(R.id.new_password_input)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        btnConfirm = findViewById(R.id.btn_confirm)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        btnConfirm.setOnClickListener {
            changePassword()
        }

        confirmPasswordInput.setOnEditorActionListener { _, _, _ ->
            changePassword()
            true
        }
    }

    private fun changePassword() {
        val oldPassword = oldPasswordInput.text.toString().trim()
        val newPassword = newPasswordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        when {
            oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty() -> {
                Toast.makeText(this, "请填写所有密码字段", Toast.LENGTH_SHORT).show()
                return
            }
            !StorageManager.checkPassword(oldPassword) -> {
                Toast.makeText(this, "旧密码错误，请重试", Toast.LENGTH_SHORT).show()
                oldPasswordInput.text?.clear()
                oldPasswordInput.requestFocus()
                return
            }
            newPassword != confirmPassword -> {
                Toast.makeText(this, "两次输入的新密码不一致，请重试", Toast.LENGTH_SHORT).show()
                confirmPasswordInput.text?.clear()
                confirmPasswordInput.requestFocus()
                return
            }
            newPassword.length < 3 -> {
                Toast.makeText(this, "新密码长度不能少于3位", Toast.LENGTH_SHORT).show()
                newPasswordInput.text?.clear()
                confirmPasswordInput.text?.clear()
                newPasswordInput.requestFocus()
                return
            }
            oldPassword == newPassword -> {
                Toast.makeText(this, "新密码不能与旧密码相同", Toast.LENGTH_SHORT).show()
                newPasswordInput.text?.clear()
                confirmPasswordInput.text?.clear()
                newPasswordInput.requestFocus()
                return
            }
            else -> {
                StorageManager.changePassword(newPassword)
                Toast.makeText(this, "密码修改成功", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.statusBarColor = android.graphics.Color.parseColor("#F9FAFB")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }
}
