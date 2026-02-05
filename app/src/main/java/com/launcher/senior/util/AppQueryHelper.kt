package com.launcher.senior.util

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.MediaStore

object AppQueryHelper {
    /**
     * 查询相机应用
     */
    fun queryCameraApps(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val apps = pm.queryIntentActivities(intent, 0)
        
        // 如果查询结果为空，查询所有应用并过滤
        return if (apps.isEmpty()) {
            queryAllApps(pm).filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                appName.contains("相机") || appName.contains("camera") || 
                resolveInfo.activityInfo.packageName.lowercase().contains("camera")
            }
        } else {
            apps
        }
    }
    
    /**
     * 查询图库应用
     */
    fun queryGalleryApps(pm: PackageManager): List<ResolveInfo> {
        // 首先尝试查询图片查看器
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
        }
        var apps = pm.queryIntentActivities(viewIntent, 0)
        
        // 如果结果太多，尝试更精确的查询
        if (apps.size > 20) {
            // 尝试查询图库相关的 Intent
            val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            apps = pm.queryIntentActivities(galleryIntent, 0)
        }
        
        // 如果结果为空或太多，查询所有应用并过滤
        if (apps.isEmpty() || apps.size > 30) {
            val allApps = queryAllApps(pm)
            val filtered = allApps.filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                val packageName = resolveInfo.activityInfo.packageName.lowercase()
                
                // 过滤图库相关的关键词
                appName.contains("图库") || appName.contains("相册") || 
                appName.contains("gallery") || appName.contains("photo") ||
                appName.contains("album") || packageName.contains("gallery") ||
                packageName.contains("photo") || packageName.contains("album")
            }
            
            // 如果过滤后结果不为空，使用过滤结果；否则使用原始查询结果
            return if (filtered.isNotEmpty()) filtered else apps
        }
        
        return apps
    }
    
    /**
     * 查询天气应用
     */
    fun queryWeatherApps(pm: PackageManager): List<ResolveInfo> {
        // 首先尝试查询 weather:// URI scheme
        val weatherIntent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("weather://")
        }
        var apps = pm.queryIntentActivities(weatherIntent, 0)
        
        // 如果查询结果为空，查询所有应用并过滤名称包含"天气"的应用
        if (apps.isEmpty()) {
            val allApps = queryAllApps(pm)
            apps = allApps.filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                val packageName = resolveInfo.activityInfo.packageName.lowercase()
                
                // 过滤天气相关的关键词
                appName.contains("天气") || appName.contains("weather") ||
                packageName.contains("weather") || packageName.contains("weather")
            }
        }
        
        return apps
    }
    
    /**
     * 查询所有已安装的应用
     */
    fun queryAllApps(pm: PackageManager): List<ResolveInfo> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
    }
    
    /**
     * 查询信息应用
     */
    fun queryMessageApps(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("sms:")
        }
        var apps = pm.queryIntentActivities(intent, 0)
        
        if (apps.isEmpty()) {
            val allApps = queryAllApps(pm)
            apps = allApps.filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                val packageName = resolveInfo.activityInfo.packageName.lowercase()
                appName.contains("信息") || appName.contains("短信") || 
                appName.contains("message") || appName.contains("sms") ||
                packageName.contains("sms") || packageName.contains("message")
            }
        }
        
        return apps
    }
    
    /**
     * 查询联系人应用
     */
    fun queryContactsApps(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.provider.ContactsContract.Contacts.CONTENT_URI
        }
        var apps = pm.queryIntentActivities(intent, 0)
        
        if (apps.isEmpty()) {
            val allApps = queryAllApps(pm)
            apps = allApps.filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                val packageName = resolveInfo.activityInfo.packageName.lowercase()
                appName.contains("联系人") || appName.contains("通讯录") ||
                appName.contains("contact") || packageName.contains("contact")
            }
        }
        
        return apps
    }
    
    /**
     * 查询电话应用
     */
    fun queryPhoneApps(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_DIAL)
        var apps = pm.queryIntentActivities(intent, 0)
        
        if (apps.isEmpty()) {
            val allApps = queryAllApps(pm)
            apps = allApps.filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                val packageName = resolveInfo.activityInfo.packageName.lowercase()
                appName.contains("电话") || appName.contains("拨号") ||
                appName.contains("phone") || appName.contains("dialer") ||
                packageName.contains("phone") || packageName.contains("dialer")
            }
        }
        
        return apps
    }
    
    /**
     * 查询计算器应用
     */
    fun queryCalculatorApps(pm: PackageManager): List<ResolveInfo> {
        val allApps = queryAllApps(pm)
        return allApps.filter { resolveInfo ->
            val appName = resolveInfo.loadLabel(pm).toString().lowercase()
            val packageName = resolveInfo.activityInfo.packageName.lowercase()
            appName.contains("计算器") || appName.contains("计算") ||
            appName.contains("calculator") || packageName.contains("calculator") ||
            packageName.contains("calc")
        }
    }
    
    /**
     * 查询文件管理器应用
     */
    fun queryFileManagerApps(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "*/*"
        }
        var apps = pm.queryIntentActivities(intent, 0)
        
        if (apps.size > 50) {
            // 如果结果太多，过滤文件管理器相关的应用
            val allApps = queryAllApps(pm)
            apps = allApps.filter { resolveInfo ->
                val appName = resolveInfo.loadLabel(pm).toString().lowercase()
                val packageName = resolveInfo.activityInfo.packageName.lowercase()
                appName.contains("文件") || appName.contains("文件管理") ||
                appName.contains("file") || appName.contains("manager") ||
                packageName.contains("file") || packageName.contains("explorer")
            }
        }
        
        return apps
    }
    
    /**
     * 根据应用类型查询应用
     */
    fun queryAppsByType(pm: PackageManager, appType: String): List<ResolveInfo> {
        return when (appType) {
            "camera" -> queryCameraApps(pm)
            "gallery" -> queryGalleryApps(pm)
            "weather" -> queryWeatherApps(pm)
            "message" -> queryMessageApps(pm)
            "contacts" -> queryContactsApps(pm)
            "phone" -> queryPhoneApps(pm)
            "calculator" -> queryCalculatorApps(pm)
            "file_manager" -> queryFileManagerApps(pm)
            else -> queryAllApps(pm)
        }
    }
}
