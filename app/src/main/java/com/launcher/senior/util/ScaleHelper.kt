package com.launcher.senior.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一的缩放辅助工具
 * 同时考虑屏幕尺寸和系统字体缩放
 */
object ScaleHelper {
    /**
     * 获取综合缩放因子
     * @param baseScreenWidth 基准屏幕宽度（dp），默认360dp
     * @param minScale 最小缩放比例
     * @param maxScale 最大缩放比例
     */
    @Composable
    fun getScale(
        baseScreenWidth: Float = 360f,
        minScale: Float = 0.85f,
        maxScale: Float = 1.6f
    ): Float {
        val config = LocalConfiguration.current
        val density = LocalDensity.current

        // 屏幕宽度缩放
        val screenScale = (config.screenWidthDp / baseScreenWidth).coerceIn(minScale, maxScale)

        // 系统字体缩放（fontScale）
        val fontScale = density.fontScale.coerceIn(0.85f, 1.5f)

        // 综合缩放：屏幕缩放 * 字体缩放的平方根（避免过度放大）
        return (screenScale * kotlin.math.sqrt(fontScale)).coerceIn(minScale, maxScale)
    }

    /**
     * 缩放字体大小
     */
    @Composable
    fun scaledSp(baseSp: Int, scale: Float = getScale()): TextUnit {
        return (baseSp * scale).toInt().coerceAtLeast(12).sp
    }

    /**
     * 缩放尺寸
     */
    @Composable
    fun scaledDp(baseDp: Int, scale: Float = getScale()): Dp {
        return (baseDp * scale).toInt().coerceAtLeast(4).dp
    }
}
