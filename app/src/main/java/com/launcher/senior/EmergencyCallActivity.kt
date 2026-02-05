package com.launcher.senior

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.launcher.senior.data.EmergencyContact
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.viewmodel.EmergencyCallViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.InputStream

class EmergencyCallActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后刷新数据
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
        
        setContent {
            SeniorLauncherTheme {
                EmergencyCallScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyCallScreen(
    onBack: () -> Unit,
    viewModel: EmergencyCallViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.loadContacts(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("紧急呼叫", fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.contacts.isEmpty()) {
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
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "还没有添加紧急联系人",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "请在设置中添加紧急联系人",
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
                items(uiState.contacts) { contact ->
                    ContactCallItem(
                        contact = contact,
                        onCall = { phoneNumber ->
                            scope.launch {
                                viewModel.initiateCall(context, phoneNumber)
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 显示长按确认对话框
    uiState.callConfirmation?.let { phoneNumber ->
        CallConfirmationDialog(
            phoneNumber = phoneNumber,
            onConfirm = {
                scope.launch {
                    viewModel.confirmCall(context, phoneNumber)
                }
            },
            onCancel = {
                viewModel.cancelCall()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCallItem(
    contact: EmergencyContact,
    onCall: (String) -> Unit
) {
    val context = LocalContext.current
    var contactPhoto by remember { mutableStateOf<Bitmap?>(null) }
    
    LaunchedEffect(contact.contactId, contact.photoUri) {
        contactPhoto = if (contact.contactId != null) {
            loadContactPhoto(context, contact.contactId)
        } else if (contact.photoUri != null) {
            loadPhotoFromUri(context, contact.photoUri)
        } else {
            null
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall(contact.phoneNumber) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(64.dp)
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
                        modifier = Modifier.size(32.dp),
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.phoneNumber,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // 拨打电话图标
            Icon(
                Icons.Default.Phone,
                contentDescription = "拨打",
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CallConfirmationDialog(
    phoneNumber: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    
    // 使用 LaunchedEffect 处理计时逻辑
    // 当 isPressed 变为 true 时开始计时，变为 false 时协程会自动取消
    LaunchedEffect(isPressed) {
        if (isPressed) {
            val duration = 2000L // 2秒
            val startTime = System.currentTimeMillis()
            
            while (progress < 1f) {
                val elapsedTime = System.currentTimeMillis() - startTime
                progress = (elapsedTime.toFloat() / duration).coerceAtMost(1f)
                if (progress >= 1f) {
                    onConfirm()
                    isPressed = false // 完成后重置
                }
                delay(16) // 约 60 帧的刷新频率
            }
        } else {
            progress = 0f // 手指松开，进度归零
        }
    }

    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("确认拨打电话", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(phoneNumber, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                Text("长按红色按钮拨出", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 核心交互区域
                Box(
                    modifier = Modifier
                        .size(150.dp) // 稍微加大一点，方便老年人操作
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isPressed = true
                                    tryAwaitRelease()
                                    isPressed = false
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 绘制进度条背景和进度
                    val colorScheme = MaterialTheme.colorScheme
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        val radius = (size.minDimension - strokeWidth) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        // 背景灰色圆环
                        drawCircle(
                            color = colorScheme.surfaceVariant,
                            radius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )

                        // 红色进度圆环
                        if (progress > 0f) {
                            drawArc(
                                color = if (progress >= 1f) Color.Green else colorScheme.error,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round // 圆角线条
                                )
                            )
                        }
                    }

                    // 内部的圆形“按钮”视觉样式
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPressed) colorScheme.error.copy(alpha = 0.8f) 
                                else colorScheme.error
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White
                        )
                    }
                }

                TextButton(onClick = onCancel) {
                    Text("取消", fontSize = 18.sp)
                }
            }
        }
    }
}
fun loadContactPhoto(context: Context, contactId: Long): Bitmap? {
    return try {
        val uri = ContactsContract.Contacts.getLookupUri(contactId, "")
        val inputStream: InputStream? = ContactsContract.Contacts.openContactPhotoInputStream(
            context.contentResolver,
            uri
        )
        inputStream?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: Exception) {
        null
    }
}

fun loadPhotoFromUri(context: Context, photoUriString: String): Bitmap? {
    return try {
        val uri = Uri.parse(photoUriString)
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        inputStream?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: Exception) {
        null
    }
}
