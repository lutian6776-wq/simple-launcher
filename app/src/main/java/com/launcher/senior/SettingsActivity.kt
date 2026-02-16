package com.launcher.senior

import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.launcher.senior.viewmodel.FilesViewModel
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.launcher.senior.data.AppPreferences
import com.launcher.senior.data.EmergencyContact
import com.launcher.senior.util.ContactHelper
import com.launcher.senior.viewmodel.SettingsViewModel
import com.launcher.senior.ui.theme.SeniorLauncherTheme
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
    viewModel: SettingsViewModel = viewModel(),
    filesViewModel: FilesViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val filesUiState by filesViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            scope.launch {
                filesViewModel.addFileShortcutFromUri(context, it)
            }
        }
    }

    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        filesViewModel.initialize(context)
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
    

    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "文件宝管理",
                                style = MaterialTheme.typography.titleLarge,
                                fontSize = 22.sp
                            )
                            IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                                Icon(Icons.Default.Add, "添加文件")
                            }
                        }
                        
                        if (filesUiState.fileShortcuts.isEmpty()) {
                            Text(
                                "还没有添加文件快捷方式",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    } else {
                        filesUiState.fileShortcuts.forEach { shortcut ->
                            FileShortcutItem(
                                shortcut = shortcut,
                                onClick = { 
                                     filesViewModel.openFile(context, shortcut)
                                },
                                onRename = { scope.launch { filesViewModel.showRenameDialog(shortcut) } },
                                onDelete = { scope.launch { filesViewModel.removeFileShortcut(context, shortcut) } },
                                onSetDefaultApp = { scope.launch { filesViewModel.showDefaultAppSelector(shortcut) } }
                            )
                        }
                    }
                    }
                }
            }


        }
    }

    // 文件宝相关对话框
    filesUiState.showAppSelector?.let { shortcut ->
        DefaultAppSelectorDialog(
            shortcut = shortcut,
            onAppSelected = { packageName, activityName ->
                scope.launch {
                    filesViewModel.setDefaultApp(context, shortcut, packageName, activityName)
                }
            },
            onDismiss = {
                filesViewModel.dismissAppSelector()
            }
        )
    }
    
    filesUiState.showRenameDialog?.let { shortcut ->
        RenameDialog(
            currentName = shortcut.name,
            onConfirm = { newName ->
                scope.launch {
                    filesViewModel.renameFileShortcut(context, shortcut, newName)
                }
            },
            onDismiss = {
                filesViewModel.dismissRenameDialog()
            }
        )
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
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
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
                
                HorizontalDivider()
                
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
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}


