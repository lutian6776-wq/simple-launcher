package com.launcher.senior.data

import android.net.Uri
import java.io.File

data class FileShortcut(
    val id: String,
    val name: String,
    val filePath: String? = null, // 文件路径，可能为null
    val fileUri: String? = null, // URI字符串，当filePath不可用时使用
    val mimeType: String,
    val fileSize: Long = 0L,
    val thumbnailPath: String? = null,
    val defaultAppPackage: String? = null, // 默认打开应用的包名
    val defaultAppActivity: String? = null // 默认打开应用的活动名
) {
    fun getFile(): File? = filePath?.let { File(it) }
    
    fun getUri(): Uri? {
        return fileUri?.let { Uri.parse(it) } ?: filePath?.let { Uri.fromFile(File(it)) }
    }
    
    fun getFileExtension(): String {
        return (filePath ?: fileUri ?: "").substringAfterLast('.', "")
    }
    
    fun isImage(): Boolean {
        return mimeType.startsWith("image/")
    }
    
    fun isVideo(): Boolean {
        return mimeType.startsWith("video/")
    }
    
    fun isAudio(): Boolean {
        return mimeType.startsWith("audio/")
    }
    
    fun exists(): Boolean {
        return filePath?.let { File(it).exists() } ?: true // 如果有URI就认为存在
    }
    
    fun getDisplayType(): String {
        return when {
            isImage() -> "图片"
            isVideo() -> "视频"
            isAudio() -> "音频"
            mimeType.contains("pdf") -> "PDF文档"
            mimeType.contains("word") || mimeType.contains("document") -> "文档"
            mimeType.contains("text") -> "文本"
            else -> "文件"
        }
    }
}
