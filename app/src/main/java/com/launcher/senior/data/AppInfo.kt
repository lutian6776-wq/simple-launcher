package com.launcher.senior.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class AppInfo(
    val id: String,
    val name: String,
    val packageName: String? = null,
    val activityName: String? = null, // 用于加载应用图标
    val intentAction: String? = null,
    val icon: ImageVector, // 默认图标，如果packageName存在则会被实际图标替换
    val isSystemApp: Boolean = false
) {
    companion object {
        // 预定义的应用
        @Suppress("DEPRECATION")
        val WECHAT = AppInfo(
            id = "wechat",
            name = "微信",
            packageName = "com.tencent.mm",
            icon = Icons.Default.Chat,
            isSystemApp = false
        )
        
        val DOUYIN = AppInfo(
            id = "douyin",
            name = "抖音",
            packageName = "com.ss.android.ugc.aweme",
            icon = Icons.Default.VideoLibrary,
            isSystemApp = false
        )
        
        val CAMERA = AppInfo(
            id = "camera",
            name = "相机",
            intentAction = android.content.Intent.ACTION_CAMERA_BUTTON,
            icon = Icons.Default.CameraAlt,
            isSystemApp = true
        )
        
        val GALLERY = AppInfo(
            id = "gallery",
            name = "图库",
            intentAction = android.content.Intent.ACTION_VIEW,
            icon = Icons.Default.PhotoLibrary,
            isSystemApp = true
        )
        
        val WEATHER = AppInfo(
            id = "weather",
            name = "天气",
            intentAction = android.content.Intent.ACTION_VIEW,
            icon = Icons.Default.WbSunny,
            isSystemApp = true
        )
        
        @Suppress("DEPRECATION")
        val MESSAGE = AppInfo(
            id = "message",
            name = "信息",
            intentAction = android.content.Intent.ACTION_SENDTO,
            icon = Icons.Default.Message,
            isSystemApp = true
        )
        
        val CONTACTS = AppInfo(
            id = "contacts",
            name = "联系人",
            intentAction = android.content.Intent.ACTION_VIEW,
            icon = Icons.Default.Contacts,
            isSystemApp = true
        )
        
        val PHONE = AppInfo(
            id = "phone",
            name = "电话",
            intentAction = android.content.Intent.ACTION_DIAL,
            icon = Icons.Default.Phone,
            isSystemApp = true
        )
        
        val CALCULATOR = AppInfo(
            id = "calculator",
            name = "计算器",
            intentAction = android.content.Intent.ACTION_VIEW,
            icon = Icons.Default.Calculate,
            isSystemApp = true
        )
        
        val FILE_MANAGER = AppInfo(
            id = "file_manager",
            name = "文件管理器",
            intentAction = android.content.Intent.ACTION_VIEW,
            icon = Icons.Default.Folder,
            isSystemApp = true
        )
    }
}
