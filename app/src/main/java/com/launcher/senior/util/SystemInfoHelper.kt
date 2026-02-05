package com.launcher.senior.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.*

data class SystemInfo(
    val currentTime: String = "",
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val wifiSignalStrength: Int = 0, // 0-4
    val mobileSignalStrength: Int = 0, // 0-4
    val isWifiConnected: Boolean = false,
    val isMobileDataEnabled: Boolean = false
)

object SystemInfoHelper {
    private val _systemInfo = MutableStateFlow(SystemInfo())
    val systemInfo: StateFlow<SystemInfo> = _systemInfo.asStateFlow()
    
    private var batteryReceiver: BroadcastReceiver? = null
    private var timeUpdateJob: kotlinx.coroutines.Job? = null
    
    fun startMonitoring(context: Context) {
        // 注册电池接收器
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = if (scale > 0) (level * 100 / scale) else 0
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    
                    _systemInfo.value = _systemInfo.value.copy(
                        batteryLevel = batteryPct,
                        isCharging = isCharging
                    )
                }
            }
        }
        
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, batteryFilter)
        
        // 更新WiFi和移动信号
        updateNetworkInfo(context)
        
        // 启动时间更新
        timeUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val currentTime = timeFormat.format(Date())
                _systemInfo.value = _systemInfo.value.copy(currentTime = currentTime)
                kotlinx.coroutines.delay(1000) // 每秒更新一次
            }
        }
    }
    
    fun stopMonitoring(context: Context) {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        batteryReceiver = null
        timeUpdateJob?.cancel()
        timeUpdateJob = null
    }
    
    private fun updateNetworkInfo(context: Context) {
        try {
            // WiFi信号强度
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val wifiRssi = wifiInfo?.rssi ?: -100
            val wifiLevel = when {
                wifiRssi >= -50 -> 4
                wifiRssi >= -60 -> 3
                wifiRssi >= -70 -> 2
                wifiRssi >= -80 -> 1
                else -> 0
            }
            val isWifiConnected = wifiInfo != null && wifiInfo.networkId != -1
            
            // 移动信号强度
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            var mobileLevel = 0
            var isMobileDataEnabled = false
            
            try {
                if (telephonyManager != null) {
                    // 尝试获取信号强度
                    try {
                        val signalStrength = telephonyManager.signalStrength
                        if (signalStrength != null) {
                            mobileLevel = signalStrength.level // 0-4
                        }
                    } catch (e: Exception) {
                        // 如果无法获取信号强度，尝试其他方法
                        try {
                            // 使用getCellLocation或其他方法
                            val cellInfo = telephonyManager.allCellInfo
                            if (cellInfo != null && cellInfo.isNotEmpty()) {
                                // 简化处理：如果有信号就显示中等强度
                                mobileLevel = 2
                            }
                        } catch (e2: Exception) {
                            // 如果都失败，使用默认值
                        }
                    }
                    
                    // 检查移动数据是否启用
                    isMobileDataEnabled = try {
                        telephonyManager.isDataEnabled
                    } catch (e: Exception) {
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            _systemInfo.value = _systemInfo.value.copy(
                wifiSignalStrength = wifiLevel,
                mobileSignalStrength = mobileLevel,
                isWifiConnected = isWifiConnected,
                isMobileDataEnabled = isMobileDataEnabled
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
