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
import com.launcher.senior.util.ScaleHelper
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

    // 统一的缩放因子
    val scale = ScaleHelper.getScale()
    
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
            onContactsSelected = { contacts ->
                scope.launch {
                    val category = uiState.selectedCategory ?: "other"
                    viewModel.addEmergencyContacts(context, contacts, category)
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
                title = { Text("设置", fontSize = ScaleHelper.scaledSp(24, scale)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "返回",
                            modifier = Modifier.size(ScaleHelper.scaledDp(24, scale))
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(ScaleHelper.scaledDp(16, scale)),
            verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale))
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(ScaleHelper.scaledDp(16, scale)),
                        verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(12, scale))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "家人",
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = ScaleHelper.scaledSp(22, scale)
                            )
                            IconButton(onClick = { viewModel.showContactSelector("family") }) {
                                Icon(
                                    Icons.Default.Add,
                                    "添加家人",
                                    modifier = Modifier.size(ScaleHelper.scaledDp(24, scale))
                                )
                            }
                        }

                        if (uiState.emergencyContacts.none { it.category == "family" }) {
                            Text(
                                "还没有添加家人，点击右上角按钮添加",
                                fontSize = ScaleHelper.scaledSp(16, scale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.emergencyContacts.filter { it.category == "family" }.forEach { contact ->
                                EmergencyContactItem(
                                    contact = contact,
                                    scale = scale,
                                    onDelete = {
                                        scope.launch {
                                            viewModel.removeEmergencyContact(context, contact)
                                        }
                                    }
                                )
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = ScaleHelper.scaledDp(8, scale)))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "其他联系人",
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = ScaleHelper.scaledSp(22, scale)
                            )
                            IconButton(onClick = { viewModel.showContactSelector("other") }) {
                                Icon(
                                    Icons.Default.Add,
                                    "添加联系人",
                                    modifier = Modifier.size(ScaleHelper.scaledDp(24, scale))
                                )
                            }
                        }

                        if (uiState.emergencyContacts.none { it.category != "family" }) {
                            Text(
                                "还没有添加其他联系人，点击右上角按钮添加",
                                fontSize = ScaleHelper.scaledSp(16, scale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            uiState.emergencyContacts.filter { it.category != "family" }.forEach { contact ->
                                EmergencyContactItem(
                                    contact = contact,
                                    scale = scale,
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
                        modifier = Modifier.padding(ScaleHelper.scaledDp(16, scale)),
                        verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(12, scale))
                    ) {
                        Text(
                            "快捷应用配置",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = ScaleHelper.scaledSp(22, scale)
                        )
                        Text(
                            "点击应用可以选择系统应用替代",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = ScaleHelper.scaledSp(16, scale)
                        )
                    }
                }
            }

            items(uiState.quickApps) { app ->
                AppConfigItem(
                    app = app,
                    scale = scale,
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
                    Divider(modifier = Modifier.padding(vertical = ScaleHelper.scaledDp(8, scale)))
                    Text(
                        "可恢复的预设应用",
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = ScaleHelper.scaledSp(22, scale),
                        modifier = Modifier.padding(vertical = ScaleHelper.scaledDp(8, scale))
                    )
                }

                items(uiState.availableDefaultApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ScaleHelper.scaledDp(16, scale)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(12, scale))
                            ) {
                                Icon(
                                    app.icon,
                                    contentDescription = app.name,
                                    modifier = Modifier.size(ScaleHelper.scaledDp(32, scale))
                                )
                                Column {
                                    Text(
                                        app.name,
                                        fontSize = ScaleHelper.scaledSp(20, scale),
                                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight
                                    )
                                    Text(
                                        "预设应用",
                                        fontSize = ScaleHelper.scaledSp(14, scale),
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
                                Text("恢复", fontSize = ScaleHelper.scaledSp(18, scale))
                            }
                        }
                    }
                }
            }
            
            item {
                Divider(modifier = Modifier.padding(vertical = ScaleHelper.scaledDp(8, scale)))
                Text(
                    "自定义应用",
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = ScaleHelper.scaledSp(22, scale),
                    modifier = Modifier.padding(vertical = ScaleHelper.scaledDp(8, scale))
                )
            }

            items(uiState.customApps) { app ->
                AppConfigItem(
                    app = app,
                    scale = scale,
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
                    Icon(
                        Icons.Default.Add,
                        "添加",
                        modifier = Modifier.size(ScaleHelper.scaledDp(24, scale))
                    )
                    Spacer(modifier = Modifier.width(ScaleHelper.scaledDp(8, scale)))
                    Text("添加自定义应用", fontSize = ScaleHelper.scaledSp(20, scale))
                }
            }
        }
    }
}

@Composable
fun AppConfigItem(
    app: AppInfo,
    scale: Float = 1f,
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
                .padding(ScaleHelper.scaledDp(16, scale)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(12, scale))
            ) {
                // 使用实际应用图标
                AppIcon(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    defaultIcon = app.icon,
                    modifier = Modifier,
                    size = ScaleHelper.scaledDp(32, scale)
                )
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        app.name,
                        fontSize = ScaleHelper.scaledSp(20, scale),
                        fontWeight = MaterialTheme.typography.titleMedium.fontWeight,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (app.packageName != null) {
                        Text(
                            app.packageName,
                            fontSize = ScaleHelper.scaledSp(14, scale),
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
                horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(4, scale))
            ) {
                if (onSelectApp != null) {
                    TextButton(onClick = onSelectApp) {
                        Text("选择", fontSize = ScaleHelper.scaledSp(16, scale))
                    }
                }
                if (onRename != null) {
                    IconButton(onClick = onRename) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "重命名",
                            modifier = Modifier.size(ScaleHelper.scaledDp(24, scale)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.size(ScaleHelper.scaledDp(24, scale)),
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
    scale: Float = 1f,
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
        elevation = CardDefaults.cardElevation(defaultElevation = ScaleHelper.scaledDp(1, scale))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ScaleHelper.scaledDp(12, scale)),
            horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(12, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(ScaleHelper.scaledDp(48, scale))
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
                        modifier = Modifier.size(ScaleHelper.scaledDp(24, scale)),
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
                    fontSize = ScaleHelper.scaledSp(18, scale),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.phoneNumber,
                    fontSize = ScaleHelper.scaledSp(16, scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(ScaleHelper.scaledDp(24, scale)),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSelectorDialog(
    onContactsSelected: (List<EmergencyContact>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var selectedContacts by remember { mutableStateOf<Set<EmergencyContact>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    val scale = ScaleHelper.getScale()

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
                        .padding(ScaleHelper.scaledDp(16, scale)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "选择联系人",
                        fontSize = ScaleHelper.scaledSp(20, scale),
                        fontWeight = MaterialTheme.typography.titleLarge.fontWeight
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            "关闭",
                            modifier = Modifier.size(ScaleHelper.scaledDp(24, scale))
                        )
                    }
                }

                Divider()

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (contacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(ScaleHelper.scaledDp(32, scale)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale))
                        ) {
                            Icon(
                                Icons.Default.Contacts,
                                contentDescription = null,
                                modifier = Modifier.size(ScaleHelper.scaledDp(48, scale)),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "没有找到联系人",
                                fontSize = ScaleHelper.scaledSp(16, scale),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        items(contacts) { contact ->
                            val isSelected = selectedContacts.contains(contact)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedContacts = if (isSelected) {
                                            selectedContacts - contact
                                        } else {
                                            selectedContacts + contact
                                        }
                                    }
                                    .padding(ScaleHelper.scaledDp(16, scale)),
                                horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedContacts = if (checked) {
                                            selectedContacts + contact
                                        } else {
                                            selectedContacts - contact
                                        }
                                    }
                                )
                                
                                // 头像
                                Box(
                                    modifier = Modifier
                                        .size(ScaleHelper.scaledDp(48, scale))
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = contact.name,
                                        modifier = Modifier.size(ScaleHelper.scaledDp(24, scale)),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = contact.name,
                                        fontSize = ScaleHelper.scaledSp(18, scale),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        fontSize = ScaleHelper.scaledSp(16, scale),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Divider()
                        }
                    }
                    
                    // 底部操作栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ScaleHelper.scaledDp(16, scale)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (selectedContacts.size == contacts.size) {
                                    selectedContacts = emptySet()
                                } else {
                                    selectedContacts = contacts.toSet()
                                }
                            }
                        ) {
                            Text(
                                if (selectedContacts.size == contacts.size) "取消全选" else "全选",
                                fontSize = ScaleHelper.scaledSp(18, scale)
                            )
                        }

                        Button(
                            onClick = { onContactsSelected(selectedContacts.toList()) },
                            enabled = selectedContacts.isNotEmpty()
                        ) {
                            Text(
                                if (selectedContacts.isEmpty()) "确定" else "确定 (${selectedContacts.size})",
                                fontSize = ScaleHelper.scaledSp(18, scale)
                            )
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
