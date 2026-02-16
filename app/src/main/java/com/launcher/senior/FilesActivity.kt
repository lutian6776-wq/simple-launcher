package com.launcher.senior

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.viewmodel.FilesViewModel
import kotlinx.coroutines.launch

class FilesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorLauncherTheme {
                FilesScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onBack: () -> Unit,
    viewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 文件选择器 - 使用OpenDocument以获取持久化权限
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // 获取持久化URI权限
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            scope.launch {
                viewModel.addFileShortcutFromUri(context, it)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件快捷方式", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }) {
                        Icon(Icons.Default.Add, "添加文件")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
            ) {
                Icon(Icons.Default.Add, "添加文件")
            }
        }
    ) { paddingValues ->
        if (uiState.fileShortcuts.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "还没有添加文件快捷方式",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "点击右上角按钮添加文件",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.fileShortcuts) { shortcut ->
                    FileShortcutItem(
                        shortcut = shortcut,
                        onClick = {
                            viewModel.openFile(context, shortcut)
                        },
                        onRename = {
                            scope.launch {
                                viewModel.showRenameDialog(shortcut)
                            }
                        },
                        onSetDefaultApp = {
                            scope.launch {
                                viewModel.showDefaultAppSelector(shortcut)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                viewModel.removeFileShortcut(context, shortcut)
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 显示应用选择对话框
    uiState.showAppSelector?.let { shortcut ->
        DefaultAppSelectorDialog(
            shortcut = shortcut,
            onAppSelected = { packageName, activityName ->
                scope.launch {
                    viewModel.setDefaultApp(context, shortcut, packageName, activityName)
                }
            },
            onDismiss = {
                viewModel.dismissAppSelector()
            }
        )
    }
    
    // 显示重命名对话框
    uiState.showRenameDialog?.let { shortcut ->
        RenameDialog(
            currentName = shortcut.name,
            onConfirm = { newName ->
                scope.launch {
                    viewModel.renameFileShortcut(context, shortcut, newName)
                }
            },
            onDismiss = {
                viewModel.dismissRenameDialog()
            }
        )
    }
}


