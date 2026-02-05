package com.launcher.senior.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.launcher.senior.util.AppIconLoader

@Composable
fun AppIcon(
    packageName: String?,
    activityName: String?,
    defaultIcon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName, activityName) { mutableStateOf<ImageBitmap?>(null) }
    
    // 异步加载图标，避免阻塞UI线程
    LaunchedEffect(packageName, activityName, size) {
        if (packageName != null) {
            iconBitmap = AppIconLoader.loadAppIconAsync(
                context = context,
                packageName = packageName,
                activityName = activityName,
                size = size.value.toInt()
            )
        } else {
            iconBitmap = null
        }
    }
    
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        androidx.compose.material3.Icon(
            imageVector = defaultIcon,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    }
}
