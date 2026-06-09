package com.xiaojilu.app

import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import me.ibrahimsn.lib.SmoothBottomBar
import com.xiaojilu.app.R
import com.xiaojilu.app.ui.AnalysisFragment
import com.xiaojilu.app.ui.CalendarFragment
import com.xiaojilu.app.ui.ListFragment
import com.xiaojilu.app.ui.SettingsFragment
import com.xiaojilu.app.utils.DatabaseManager
import com.xiaojilu.app.utils.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var bottomBar: SmoothBottomBar
    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2

    private val calendarFragment = CalendarFragment()
    private val listFragment = ListFragment()
    private val analysisFragment = AnalysisFragment()
    private val settingsFragment = SettingsFragment()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        StorageManager.init(this)

        window.statusBarColor = android.graphics.Color.parseColor("#F8FAFC")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setupUI()
        registerNetworkCallback()
    }

    override fun onResume() {
        super.onResume()
        tryAutoSync()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
    }

    fun refreshAllFragments() {
        calendarFragment.refresh()
        listFragment.refresh()
        analysisFragment.refresh()
    }

    private fun setupUI() {
        bottomBar = findViewById(R.id.bottomBar)
        viewPager = findViewById(R.id.view_pager)

        val adapter = ViewPagerAdapter(this)
        viewPager.adapter = adapter

        viewPager.offscreenPageLimit = 3

        bottomBar.onItemSelected = {
            viewPager.currentItem = it
        }

        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomBar.itemActiveIndex = position
            }
        })

        preloadAllData()
    }
    
    private fun preloadAllData() {
        // 使用协程在后台预加载数据
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // 预加载记录数据
                val records = StorageManager.getAllRecords()
                val years = StorageManager.getRecordYears()

                // 切换回主线程预初始化所有Fragment
                withContext(Dispatchers.Main) {
                    // 预初始化列表Fragment数据
                    try {
                        listFragment.preloadData(records, years)
                    } catch (e: Exception) {
                        // Fragment可能还没有准备好，忽略错误
                    }

                    // 预初始化日历Fragment数据
                    try {
                        calendarFragment.preloadData(records.records)
                    } catch (e: Exception) {
                        // Fragment可能还没有准备好，忽略错误
                    }

                    // 预初始化分析Fragment数据
                    try {
                        analysisFragment.preloadData(records.records)
                    } catch (e: Exception) {
                        // Fragment可能还没有准备好，忽略错误
                    }
                }
            } catch (e: Exception) {
                // 预加载失败不影响主流程
            }
        }
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> calendarFragment
                1 -> listFragment
                2 -> analysisFragment
                3 -> settingsFragment
                else -> calendarFragment
            }
        }
    }

    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                tryAutoSync()
            }

            override fun onLost(network: Network) {}
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(it)
        }
        networkCallback = null
    }

    private fun tryAutoSync() {
        if (!StorageManager.isDatabaseEnabled()) return
        if (!StorageManager.hasPendingSync()) return

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val result = DatabaseManager.processPendingSync()
                if (result.success && (result.uploadedCount > 0 || result.deletedCount > 0)) {
                    withContext(Dispatchers.Main) {
                        val msg = buildString {
                            append("离线数据已同步\n")
                            if (result.uploadedCount > 0) append("上传：${result.uploadedCount} 条\n")
                            if (result.deletedCount > 0) append("删除：${result.deletedCount} 条")
                        }.trimEnd('\n')
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}