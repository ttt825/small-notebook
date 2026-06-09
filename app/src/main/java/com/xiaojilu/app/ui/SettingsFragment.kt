package com.xiaojilu.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.xiaojilu.app.ChangePasswordActivity
import com.xiaojilu.app.DatabaseConfigActivity
import com.xiaojilu.app.DatabaseStatusActivity
import com.xiaojilu.app.R
import com.xiaojilu.app.utils.StorageManager

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchPassword = view.findViewById<Switch>(R.id.switch_password)
        val layoutPasswordSetting = view.findViewById<LinearLayout>(R.id.layout_password_setting)
        val layoutDatabaseConfig = view.findViewById<LinearLayout>(R.id.layout_database_config)
        val layoutDatabaseStatus = view.findViewById<LinearLayout>(R.id.layout_database_status)
        val tvDatabaseStatusHint = view.findViewById<TextView>(R.id.tv_database_status_hint)

        switchPassword.isChecked = StorageManager.isPasswordEnabled()
        switchPassword.setOnCheckedChangeListener { _, isChecked ->
            StorageManager.setPasswordEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(requireContext(), "启动密码已开启", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "启动密码已关闭", Toast.LENGTH_SHORT).show()
            }
        }

        layoutPasswordSetting.setOnClickListener {
            if (!StorageManager.isPasswordEnabled()) {
                Toast.makeText(requireContext(), "请先开启启动密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), ChangePasswordActivity::class.java))
        }

        layoutDatabaseConfig.setOnClickListener {
            startActivity(Intent(requireContext(), DatabaseConfigActivity::class.java))
        }

        layoutDatabaseStatus.setOnClickListener {
            startActivity(Intent(requireContext(), DatabaseStatusActivity::class.java))
        }

        if (StorageManager.isDatabaseEnabled()) {
            tvDatabaseStatusHint.text = "已配置"
            tvDatabaseStatusHint.setTextColor(resources.getColor(R.color.primary, null))
        } else {
            tvDatabaseStatusHint.text = "未配置"
            tvDatabaseStatusHint.setTextColor(resources.getColor(R.color.muted, null))
        }
    }

    override fun onResume() {
        super.onResume()
        val tvDatabaseStatusHint = view?.findViewById<TextView>(R.id.tv_database_status_hint) ?: return
        if (StorageManager.isDatabaseEnabled()) {
            tvDatabaseStatusHint.text = "已配置"
            tvDatabaseStatusHint.setTextColor(resources.getColor(R.color.primary, null))
        } else {
            tvDatabaseStatusHint.text = "未配置"
            tvDatabaseStatusHint.setTextColor(resources.getColor(R.color.muted, null))
        }
    }
}
