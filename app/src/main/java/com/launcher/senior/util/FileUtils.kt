package com.launcher.senior.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File

object FileUtils {
    /**
     * 获取文件的MIME类型
     */
    fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
    
    /**
     * 获取文件大小（格式化）
     */
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 获取图片缩略图（从文件路径）
     */
    fun getImageThumbnail(filePath: String, width: Int = 200, height: Int = 200): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            options.inSampleSize = calculateInSampleSize(options, width, height)
            options.inJustDecodeBounds = false
            
            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取图片缩略图（从URI）
     */
    fun getImageThumbnailFromUri(context: Context, uri: Uri, width: Int = 200, height: Int = 200): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()
            
            options.inSampleSize = calculateInSampleSize(options, width, height)
            options.inJustDecodeBounds = false
            
            val inputStream2 = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2?.close()
            bitmap
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取视频缩略图（从文件路径）
     */
    fun getVideoThumbnail(filePath: String, width: Int = 200, height: Int = 200): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (bitmap != null) {
                ThumbnailUtils.extractThumbnail(bitmap, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取视频缩略图（从URI）
     */
    fun getVideoThumbnailFromUri(context: Context, uri: Uri, width: Int = 200, height: Int = 200): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            
            if (bitmap != null) {
                ThumbnailUtils.extractThumbnail(bitmap, width, height, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 计算采样率
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * 获取文件列表
     */
    fun getFilesInDirectory(directory: File): List<File> {
        return try {
            directory.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取目录列表
     */
    fun getDirectoriesInDirectory(directory: File): List<File> {
        return try {
            directory.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 从URI获取文件路径
     */
    fun getFilePathFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            
            when {
                "com.android.externalstorage.documents" == uri.authority -> {
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        result = Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                }
                "com.android.providers.downloads.documents" == uri.authority -> {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"),
                        docId.toLong()
                    )
                    result = getDataColumn(context, contentUri, null, null)
                }
                "com.android.providers.media.documents" == uri.authority -> {
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    when (type) {
                        "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    result = getDataColumn(context, contentUri, selection, selectionArgs)
                }
            }
        } else if ("content" == uri.scheme) {
            result = getDataColumn(context, uri, null, null)
        } else if ("file" == uri.scheme) {
            result = uri.path
        }
        
        return result
    }
    
    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        
        try {
            cursor = context.contentResolver.query(
                uri!!, projection, selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return null
    }
    
    /**
     * 从URI获取文件名
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path?.let {
                val cut = it.lastIndexOf('/')
                if (cut != -1) {
                    it.substring(cut + 1)
                } else {
                    null
                }
            }
        }
        return result
    }
    
    /**
     * 从URI获取文件大小
     */
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var result: Long = 0L
        if (uri.scheme == "content") {
            var cursor: Cursor? = null
            try {
                cursor = context.contentResolver.query(uri, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0) {
                        result = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        return result
    }
}
