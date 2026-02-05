package com.launcher.senior.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.senior.data.FileShortcut
import com.launcher.senior.data.AppPreferences
import com.launcher.senior.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FilesUiState(
    val fileShortcuts: List<FileShortcut> = emptyList(),
    val showAppSelector: FileShortcut? = null, // 显示应用选择器的文件
    val showRenameDialog: FileShortcut? = null // 显示重命名对话框的文件
)

class FilesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        loadData(context)
    }
    
    private fun loadData(context: Context) {
        viewModelScope.launch {
            val prefs = AppPreferences(context)
            val shortcuts = prefs.getFileShortcuts()
            
            // 加载默认应用设置
            val shortcutsWithDefaults = shortcuts.map { shortcut ->
                val defaultApp = prefs.getDefaultAppForMimeType(shortcut.mimeType)
                if (defaultApp != null) {
                    shortcut.copy(
                        defaultAppPackage = defaultApp.first,
                        defaultAppActivity = defaultApp.second
                    )
                } else {
                    shortcut
                }
            }
            
            _uiState.value = _uiState.value.copy(
                fileShortcuts = shortcutsWithDefaults
            )
        }
    }
    
    suspend fun showDefaultAppSelector(context: Context, shortcut: FileShortcut) {
        _uiState.value = _uiState.value.copy(showAppSelector = shortcut)
    }
    
    suspend fun setDefaultApp(context: Context, shortcut: FileShortcut, packageName: String, activityName: String?) {
        val prefs = AppPreferences(context)
        prefs.setDefaultAppForMimeType(shortcut.mimeType, packageName, activityName)
        
        // 更新当前文件的默认应用
        val updatedShortcut = shortcut.copy(
            defaultAppPackage = packageName,
            defaultAppActivity = activityName
        )
        val currentShortcuts = prefs.getFileShortcuts().toMutableList()
        val index = currentShortcuts.indexOfFirst { it.id == shortcut.id }
        if (index >= 0) {
            currentShortcuts[index] = updatedShortcut
            prefs.setFileShortcuts(currentShortcuts)
        }
        
        loadData(context)
        _uiState.value = _uiState.value.copy(showAppSelector = null)
    }
    
    fun dismissAppSelector() {
        _uiState.value = _uiState.value.copy(showAppSelector = null)
    }
    
    suspend fun showRenameDialog(context: Context, shortcut: FileShortcut) {
        _uiState.value = _uiState.value.copy(showRenameDialog = shortcut)
    }
    
    suspend fun renameFileShortcut(context: Context, shortcut: FileShortcut, newName: String) {
        val prefs = AppPreferences(context)
        val currentShortcuts = prefs.getFileShortcuts().toMutableList()
        val index = currentShortcuts.indexOfFirst { it.id == shortcut.id }
        if (index >= 0) {
            currentShortcuts[index] = shortcut.copy(name = newName)
            prefs.setFileShortcuts(currentShortcuts)
            loadData(context)
        }
        _uiState.value = _uiState.value.copy(showRenameDialog = null)
    }
    
    fun dismissRenameDialog() {
        _uiState.value = _uiState.value.copy(showRenameDialog = null)
    }
    
    
    suspend fun addFileShortcut(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        
        val shortcut = FileShortcut(
            id = "file_${System.currentTimeMillis()}",
            name = file.name,
            filePath = filePath,
            mimeType = FileUtils.getMimeType(filePath),
            fileSize = file.length()
        )
        
        val prefs = AppPreferences(context)
        val currentShortcuts = prefs.getFileShortcuts().toMutableList()
        currentShortcuts.add(shortcut)
        prefs.setFileShortcuts(currentShortcuts)
        loadData(context)
    }
    
    suspend fun addFileShortcutFromUri(context: Context, uri: Uri) {
        try {
            // 尝试获取文件路径
            val filePath = FileUtils.getFilePathFromUri(context, uri)
            
            // 获取文件名
            val fileName = FileUtils.getFileNameFromUri(context, uri) ?: "未知文件"
            
            // 获取MIME类型
            val mimeType = context.contentResolver.getType(uri) ?: FileUtils.getMimeType(fileName)
            
            // 获取文件大小
            var fileSize = 0L
            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    fileSize = file.length()
                }
            } else {
                // 如果无法获取文件路径，尝试从URI获取文件大小
                fileSize = FileUtils.getFileSizeFromUri(context, uri)
            }
            
            val shortcut = FileShortcut(
                id = "file_${System.currentTimeMillis()}",
                name = fileName,
                filePath = filePath,
                fileUri = uri.toString(),
                mimeType = mimeType,
                fileSize = fileSize
            )
            
            val prefs = AppPreferences(context)
            val currentShortcuts = prefs.getFileShortcuts().toMutableList()
            currentShortcuts.add(shortcut)
            prefs.setFileShortcuts(currentShortcuts)
            loadData(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    suspend fun removeFileShortcut(context: Context, shortcut: FileShortcut) {
        val prefs = AppPreferences(context)
        val currentShortcuts = prefs.getFileShortcuts().toMutableList()
        currentShortcuts.removeAll { it.id == shortcut.id }
        prefs.setFileShortcuts(currentShortcuts)
        loadData(context)
    }
    
    fun openFile(context: Context, shortcut: FileShortcut) {
        try {
            val uri = shortcut.getUri()
            if (uri == null) {
                // 如果无法获取URI，尝试从fileUri字符串解析
                val parsedUri = shortcut.fileUri?.let { Uri.parse(it) }
                if (parsedUri == null) {
                    return
                }
                openFileWithUri(
                    context, 
                    parsedUri, 
                    shortcut.mimeType,
                    shortcut.defaultAppPackage,
                    shortcut.defaultAppActivity
                )
                return
            }
            
            // 如果有文件路径，尝试使用FileProvider
            if (shortcut.filePath != null) {
                val file = File(shortcut.filePath)
                if (file.exists()) {
                    val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        // Android 7.0+ 使用 FileProvider
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                    openFileWithUri(
                        context, 
                        fileUri, 
                        shortcut.mimeType,
                        shortcut.defaultAppPackage,
                        shortcut.defaultAppActivity
                    )
                    return
                }
            }
            
            // 使用原始URI
            openFileWithUri(
                context, 
                uri, 
                shortcut.mimeType,
                shortcut.defaultAppPackage,
                shortcut.defaultAppActivity
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun openFileWithUri(context: Context, uri: Uri, mimeType: String, packageName: String? = null, activityName: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            // 如果指定了默认应用，直接使用该应用打开
            if (packageName != null) {
                if (activityName != null) {
                    setClassName(packageName, activityName)
                } else {
                    setPackage(packageName)
                }
            }
        }
        
        try {
            if (packageName != null) {
                // 直接使用默认应用打开
                context.startActivity(intent)
            } else {
                // 使用选择器让用户选择打开方式
                val chooser = Intent.createChooser(intent, "选择打开方式")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            // 如果默认应用无法打开，回退到选择器
            val chooser = Intent.createChooser(intent, "选择打开方式")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(chooser)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
}
