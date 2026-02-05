package com.launcher.senior

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.launcher.senior.data.FileShortcut
import com.launcher.senior.ui.AppIcon
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.util.FileUtils
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
                                viewModel.showRenameDialog(context, shortcut)
                            }
                        },
                        onSetDefaultApp = {
                            scope.launch {
                                viewModel.showDefaultAppSelector(context, shortcut)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShortcutItem(
    shortcut: FileShortcut,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSetDefaultApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var thumbnail by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(shortcut.filePath, shortcut.fileUri) {
        thumbnail = when {
            shortcut.isImage() -> {
                if (shortcut.filePath != null) {
                    FileUtils.getImageThumbnail(shortcut.filePath, 120, 120)
                } else if (shortcut.fileUri != null) {
                    val uri = Uri.parse(shortcut.fileUri)
                    FileUtils.getImageThumbnailFromUri(context, uri, 120, 120)
                } else {
                    null
                }
            }
            shortcut.isVideo() -> {
                if (shortcut.filePath != null) {
                    FileUtils.getVideoThumbnail(shortcut.filePath, 120, 120)
                } else if (shortcut.fileUri != null) {
                    val uri = Uri.parse(shortcut.fileUri)
                    FileUtils.getVideoThumbnailFromUri(context, uri, 120, 120)
                } else {
                    null
                }
            }
            else -> null
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩略图或图标
            Box(
                modifier = Modifier
                    .size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = shortcut.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 显示文件类型图标
                    val icon = when {
                        shortcut.isImage() -> Icons.Default.Image
                        shortcut.isVideo() -> Icons.Default.VideoLibrary
                        shortcut.isAudio() -> Icons.Default.AudioFile
                        shortcut.mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
                        shortcut.mimeType.contains("text") -> Icons.Default.TextSnippet
                        else -> Icons.Default.InsertDriveFile
                    }
                    Icon(
                        icon,
                        contentDescription = shortcut.name,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 文件信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = shortcut.name,
                    fontSize = 20.sp,
                    fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = shortcut.getDisplayType(),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (shortcut.fileSize > 0) {
                    Text(
                        text = FileUtils.formatFileSize(shortcut.fileSize),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onRename) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "重命名",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (onSetDefaultApp != null) {
                    IconButton(onClick = onSetDefaultApp) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置默认打开方式",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultAppSelectorDialog(
    shortcut: FileShortcut,
    onAppSelected: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    // 查询能够打开该文件类型的应用
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setType(shortcut.mimeType)
    }
    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "选择默认打开方式",
                        fontSize = 20.sp,
                        fontWeight = MaterialTheme.typography.titleLarge.fontWeight
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                if (resolveInfos.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "没有找到可以打开此文件类型的应用",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // 应用列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(resolveInfos) { resolveInfo ->
                            val appName = resolveInfo.loadLabel(pm).toString()
                            val packageName = resolveInfo.activityInfo.packageName
                            val activityName = resolveInfo.activityInfo.name
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAppSelected(packageName, activityName)
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(
                                    packageName = packageName,
                                    activityName = activityName,
                                    defaultIcon = Icons.Default.Android,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = appName,
                                    fontSize = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text("名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (textFieldValue.isNotBlank()) {
                        onConfirm(textFieldValue)
                    }
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
