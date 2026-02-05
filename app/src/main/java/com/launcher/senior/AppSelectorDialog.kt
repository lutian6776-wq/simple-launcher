package com.launcher.senior

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.launcher.senior.ui.AppIcon
import com.launcher.senior.util.AppQueryHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAppsSelectorDialog(
    onAppSelected: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    // 查询所有启动器应用（即所有已安装的应用）
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
    
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
                        "选择应用",
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 22.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                // 应用列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 去重：同一个包名只显示一次
                    val uniqueApps = resolveInfos
                        .distinctBy { it.activityInfo.packageName }
                        .sortedBy { it.loadLabel(pm).toString() }
                    
                    items(uniqueApps) { resolveInfo ->
                        val appName = resolveInfo.loadLabel(pm).toString()
                        val packageName = resolveInfo.activityInfo.packageName
                        val activityName = resolveInfo.activityInfo.name
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAppSelected(packageName, appName, activityName)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 应用实际图标
                                AppIcon(
                                    packageName = packageName,
                                    activityName = activityName,
                                    defaultIcon = Icons.Default.Apps,
                                    modifier = Modifier,
                                    size = 40.dp
                                )
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        appName,
                                        fontSize = 18.sp,
                                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                    )
                                    Text(
                                        packageName,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAppSelectorDialog(
    appType: String,
    onAppSelected: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    // 使用智能查询方式
    val resolveInfos: List<ResolveInfo> = AppQueryHelper.queryAppsByType(pm, appType)
    
    AppSelectorDialogContent(
        resolveInfos = resolveInfos,
        pm = pm,
        onAppSelected = onAppSelected,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorDialog(
    intent: Intent,
    onAppSelected: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    // 查询能处理该 Intent 的应用
    val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
    
    AppSelectorDialogContent(
        resolveInfos = resolveInfos,
        pm = pm,
        onAppSelected = onAppSelected,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSelectorDialogContent(
    resolveInfos: List<ResolveInfo>,
    pm: PackageManager,
    onAppSelected: (String, String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    
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
                        "选择应用",
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 22.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                // 应用列表
                if (resolveInfos.isEmpty()) {
                    // 空状态提示
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "未找到相关应用",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 去重：同一个包名只显示一次
                        val uniqueApps = resolveInfos
                            .distinctBy { it.activityInfo.packageName }
                            .sortedBy { it.loadLabel(pm).toString() }
                        
                        items(uniqueApps) { resolveInfo ->
                            val appName = resolveInfo.loadLabel(pm).toString()
                            val packageName = resolveInfo.activityInfo.packageName
                            val activityName = resolveInfo.activityInfo.name
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAppSelected(packageName, appName, activityName)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // 应用实际图标
                                    AppIcon(
                                        packageName = packageName,
                                        activityName = activityName,
                                        defaultIcon = Icons.Default.Apps,
                                        modifier = Modifier,
                                        size = 40.dp
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            appName,
                                            fontSize = 18.sp,
                                            fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                        )
                                        Text(
                                            packageName,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
