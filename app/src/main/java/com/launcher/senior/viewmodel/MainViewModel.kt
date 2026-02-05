package com.launcher.senior.viewmodel

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.senior.data.AppInfo
import com.launcher.senior.data.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val emergencyPhoneNumber: String = "",
    val quickApps: List<AppInfo> = emptyList(),
    val customApps: List<AppInfo> = emptyList()
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    fun loadData(context: Context) {
        viewModelScope.launch {
            val appPreferences = AppPreferences(context)
            val phoneNumber = appPreferences.getEmergencyPhoneNumber()
            val quickApps = appPreferences.getQuickApps()
            val customApps = appPreferences.getCustomApps()
            
            _uiState.value = MainUiState(
                emergencyPhoneNumber = phoneNumber,
                quickApps = quickApps,
                customApps = customApps
            )
        }
    }
    
    fun refreshApps(context: Context) {
        loadData(context)
    }
    
    fun onCallClick(context: Context) {
        val phoneNumber = _uiState.value.emergencyPhoneNumber
        if (phoneNumber.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
            }
            try {
                context.startActivity(intent)
            } catch (e: SecurityException) {
                // 权限被拒绝
            }
        }
    }
    
    fun onAppClick(context: Context, app: AppInfo) {
        try {
            val intent = when {
                app.packageName != null -> {
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent
                    } else {
                        // 如果应用未安装，尝试打开应用商店
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("market://details?id=${app.packageName}")
                        }
                    }
                }
                app.intentAction != null -> {
                    when (app.id) {
                        "camera" -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        "gallery" -> Intent(Intent.ACTION_VIEW).apply {
                            type = "image/*"
                        }
                        "weather" -> {
                            // 先尝试 weather:// URI scheme
                            val weatherIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("weather://")
                            }
                            val pm = context.packageManager
                            val resolveInfos = pm.queryIntentActivities(weatherIntent, 0)
                            
                            if (resolveInfos.isNotEmpty()) {
                                weatherIntent
                            } else {
                                // 如果找不到，尝试查询所有天气应用
                                val allWeatherApps = com.launcher.senior.util.AppQueryHelper.queryWeatherApps(pm)
                                if (allWeatherApps.isNotEmpty()) {
                                    // 使用第一个天气应用
                                    val resolveInfo = allWeatherApps.first()
                                    pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                                } else {
                                    null
                                }
                            }
                        }
                        "message" -> Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("sms:")
                        }
                        "contacts" -> Intent(Intent.ACTION_VIEW).apply {
                            data = ContactsContract.Contacts.CONTENT_URI
                        }
                        "phone" -> Intent(Intent.ACTION_DIAL)
                        "calculator" -> {
                            // 查询计算器应用
                            val pm = context.packageManager
                            val calcApps = com.launcher.senior.util.AppQueryHelper.queryCalculatorApps(pm)
                            if (calcApps.isNotEmpty()) {
                                val resolveInfo = calcApps.first()
                                pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                            } else {
                                null
                            }
                        }
                        "file_manager" -> {
                            // 查询文件管理器应用
                            val pm = context.packageManager
                            val fileApps = com.launcher.senior.util.AppQueryHelper.queryFileManagerApps(pm)
                            if (fileApps.isNotEmpty()) {
                                val resolveInfo = fileApps.first()
                                pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                            } else {
                                // 如果没有找到，打开文件选择器
                                Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                }
                            }
                        }
                        else -> null
                    }
                }
                else -> null
            }
            
            if (intent != null) {
                try {
                    context.startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    // 如果找不到应用，尝试使用包名启动
                    if (app.packageName != null) {
                        val pm = context.packageManager
                        val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun onPermissionGranted() {
        // 权限授予后的处理
    }
}
