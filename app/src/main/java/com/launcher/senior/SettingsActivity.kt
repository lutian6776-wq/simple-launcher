package com.launcher.senior

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import com.launcher.senior.AppSelectorDialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.launcher.senior.data.AppInfo
import com.launcher.senior.data.AppPreferences
import com.launcher.senior.data.EmergencyContact
import com.launcher.senior.util.ContactHelper
import com.launcher.senior.ui.AppIcon
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.util.AppQueryHelper
import com.launcher.senior.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeniorLauncherTheme {
                SettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showAppSelector by remember { mutableStateOf<AppInfo?>(null) }
    var showCustomAppSelector by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<AppInfo?>(null) }
    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // 自定义应用选择对话框（显示所有已安装应用）
    if (showCustomAppSelector) {
        AllAppsSelectorDialog(
            onAppSelected = { packageName, appName, activityName ->
                scope.launch {
                    viewModel.addCustomApp(context, packageName, appName, activityName)
                    showCustomAppSelector = false
                }
            },
            onDismiss = { showCustomAppSelector = false }
        )
    }
    
    // 系统应用选择对话框
    showAppSelector?.let { app ->
        SmartAppSelectorDialog(
            appType = app.id,
            onAppSelected = { packageName, appName, activityName ->
                scope.launch {
                    viewModel.selectSystemApp(context, app, packageName, appName, activityName)
                    showAppSelector = null
                }
            },
            onDismiss = { showAppSelector = null }
        )
    }
    
    // 联系人选择对话框
    if (uiState.showContactSelector) {
        ContactSelectorDialog(
            onContactSelected = { contact ->
                scope.launch {
                    viewModel.addEmergencyContact(context, contact)
                    viewModel.dismissContactSelector()
                }
            },
            onDismiss = {
                viewModel.dismissContactSelector()
            }
        )
    }
    
    // 应用重命名对话框
    showRenameDialog?.let { app ->
        RenameAppDialog(
            currentName = app.name,
            onConfirm = { newName ->
                scope.launch {
                    viewModel.renameApp(context, app, newName)
                    showRenameDialog = null
                }
            },
            onDismiss = {
                showRenameDialog = null
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "紧急联系人",
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 22.sp
                            )
                            IconButton(onClick = { viewModel.showContactSelector() }) {
                                Icon(Icons.Default.Add, "添加联系人")
                            }
                        }
                        
                        if (uiState.emergencyContacts.isEmpty()) {
                            Text(
                                "还没有添加紧急联系人，点击右上角按钮添加",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.emergencyContacts.forEach { contact ->
                                EmergencyContactItem(
                                    contact = contact,
                                    onDelete = {
                                        scope.launch {
                                            viewModel.removeEmergencyContact(context, contact)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "快捷应用配置",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 22.sp
                        )
                        Text(
                            "点击应用可以选择系统应用替代",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            
            items(uiState.quickApps) { app ->
                AppConfigItem(
                    app = app,
                    onSelectApp = if (app.isSystemApp) {
                        { showAppSelector = app }
                    } else {
                        null
                    },
                    onRename = {
                        showRenameDialog = app
                    },
                    onRemove = {
                        scope.launch {
                            viewModel.removeQuickApp(context, app)
                        }
                    }
                )
            }
            
            // 显示可恢复的预设应用
            if (uiState.availableDefaultApps.isNotEmpty()) {
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "可恢复的预设应用",
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 22.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(uiState.availableDefaultApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    app.icon,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(32.dp)
                                )
                                Column {
                                    Text(
                                        app.name,
                                        fontSize = 20.sp,
                                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                    )
                                    Text(
                                        "预设应用",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        viewModel.restoreDefaultApp(context, app)
                                    }
                                }
                            ) {
                                Text("恢复", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
            
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "自定义应用",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(uiState.customApps) { app ->
                AppConfigItem(
                    app = app,
                    onSelectApp = null,
                    onRename = {
                        showRenameDialog = app
                    },
                    onRemove = {
                        scope.launch {
                            viewModel.removeCustomApp(context, app)
                        }
                    }
                )
            }
            
            item {
                Button(
                    onClick = {
                        showCustomAppSelector = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Add, "添加")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加自定义应用", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
fun AppConfigItem(
    app: AppInfo,
    onSelectApp: (() -> Unit)?,
    onRename: (() -> Unit)? = null,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 使用实际应用图标
                AppIcon(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    defaultIcon = app.icon,
                    modifier = Modifier,
                    size = 32.dp
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        app.name,
                        fontSize = 20.sp,
                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.packageName != null) {
                        Text(
                            app.packageName,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSelectApp != null) {
                    TextButton(onClick = onSelectApp) {
                        Text("选择", fontSize = 18.sp)
                    }
                }
                if (onRename != null) {
                    IconButton(onClick = onRename) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "重命名",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onRemove) {
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
fun EmergencyContactItem(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var contactPhoto by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(contact.contactId, contact.photoUri) {
        contactPhoto = if (contact.contactId != null) {
            com.launcher.senior.loadContactPhoto(context, contact.contactId)
        } else if (contact.photoUri != null) {
            com.launcher.senior.loadPhotoFromUri(context, contact.photoUri)
        } else {
            null
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (contactPhoto != null) {
                    Image(
                        bitmap = contactPhoto!!.asImageBitmap(),
                        contentDescription = contact.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = contact.name,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 联系人信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = contact.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = contact.phoneNumber,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 删除按钮
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectorDialog(
    onContactSelected: (EmergencyContact) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        contacts = ContactHelper.getAllContacts(context)
        isLoading = false
    }
    
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
                        "选择联系人",
                        fontSize = 20.sp,
                        fontWeight = MaterialTheme.typography.titleLarge.fontWeight
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                }
                
                Divider()
                
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (contacts.isEmpty()) {
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
                                Icons.Default.Contacts,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "没有找到联系人",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(contacts) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onContactSelected(contact)
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 头像
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = contact.name,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = contact.name,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
fun RenameAppDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名应用") },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                label = { Text("应用名称") },
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
