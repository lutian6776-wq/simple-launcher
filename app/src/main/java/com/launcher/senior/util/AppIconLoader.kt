package com.launcher.senior.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object AppIconLoader {
    // 图标缓存：key = "packageName:activityName:size", value = ImageBitmap
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    
    /**
     * 加载应用图标（异步，带缓存）
     */
    suspend fun loadAppIconAsync(
        context: Context, 
        packageName: String?, 
        activityName: String?,
        size: Int = 512
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        if (packageName == null) return@withContext null
        
        // 检查缓存
        val cacheKey = "$packageName:$activityName:$size"
        iconCache[cacheKey]?.let { return@withContext it }
        
        try {
            val pm = context.packageManager
            val drawable = if (activityName != null) {
                // 使用活动名加载图标
                val componentName = android.content.ComponentName(packageName, activityName)
                pm.getActivityIcon(componentName)
            } else {
                // 使用包名加载图标
                pm.getApplicationIcon(packageName)
            }
            
            val bitmap = drawableToBitmap(drawable, size)
            // 存入缓存
            iconCache[cacheKey] = bitmap
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 加载应用图标（同步，用于向后兼容）
     */
    fun loadAppIcon(context: Context, packageName: String?, activityName: String?): Drawable? {
        if (packageName == null) return null
        
        return try {
            val pm = context.packageManager
            if (activityName != null) {
                // 使用活动名加载图标
                val componentName = android.content.ComponentName(packageName, activityName)
                pm.getActivityIcon(componentName)
            } else {
                // 使用包名加载图标
                pm.getApplicationIcon(packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 将Drawable转换为ImageBitmap
     */
    fun drawableToBitmap(drawable: Drawable, size: Int = 512): ImageBitmap {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
        return bitmap.asImageBitmap()
    }
    
    /**
     * 清除缓存（可选，用于内存管理）
     */
    fun clearCache() {
        iconCache.clear()
    }
}

@Composable
fun rememberAppIcon(
    packageName: String?,
    activityName: String?,
    defaultIcon: androidx.compose.ui.graphics.vector.ImageVector
): androidx.compose.ui.graphics.vector.ImageVector {
    val context = LocalContext.current
    
    return remember(packageName, activityName) {
        val drawable = AppIconLoader.loadAppIcon(context, packageName, activityName)
        if (drawable != null) {
            // 如果有实际图标，返回默认图标（因为Compose不支持直接显示Drawable）
            // 实际显示时会在组件中处理
            defaultIcon
        } else {
            defaultIcon
        }
    }
}
