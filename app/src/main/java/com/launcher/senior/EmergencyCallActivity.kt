package com.launcher.senior

import android.Manifest
import android.content.ContentUris
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
import com.launcher.senior.util.ScaleHelper
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
    val scale = ScaleHelper.getScale()

    LaunchedEffect(Unit) {
        viewModel.loadContacts(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("紧急呼叫", fontSize = ScaleHelper.scaledSp(24, scale)) },
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
        if (uiState.contacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(ScaleHelper.scaledDp(32, scale)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale))
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(ScaleHelper.scaledDp(64, scale)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "还没有添加紧急联系人",
                        fontSize = ScaleHelper.scaledSp(20, scale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "请在设置中添加紧急联系人",
                        fontSize = ScaleHelper.scaledSp(16, scale),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(ScaleHelper.scaledDp(8, scale)),
                verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(8, scale))
            ) {
                items(uiState.contacts) { contact ->
                    ContactCallItem(
                        contact = contact,
                        scale = scale,
                        onCall = {
                            scope.launch {
                                viewModel.initiateCall(contact)
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 显示长按确认对话框
    uiState.callConfirmation?.let { contact ->
        CallConfirmationDialog(
            contact = contact,
            onConfirm = {
                scope.launch {
                    viewModel.confirmCall(context, contact)
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
    scale: Float = 1f,
    onCall: () -> Unit
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
            .clickable { onCall() },
        elevation = CardDefaults.cardElevation(defaultElevation = ScaleHelper.scaledDp(2, scale))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ScaleHelper.scaledDp(16, scale)),
            horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(ScaleHelper.scaledDp(64, scale))
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
                        modifier = Modifier.size(ScaleHelper.scaledDp(32, scale)),
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
                    fontSize = ScaleHelper.scaledSp(20, scale),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(ScaleHelper.scaledDp(4, scale)))
                Text(
                    text = contact.phoneNumber,
                    fontSize = ScaleHelper.scaledSp(18, scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 拨打电话图标
            Icon(
                Icons.Default.Phone,
                contentDescription = "拨打",
                modifier = Modifier.size(ScaleHelper.scaledDp(32, scale)),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CallConfirmationDialog(
    contact: EmergencyContact,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var progress by remember { mutableStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val scale = ScaleHelper.getScale()
    val context = LocalContext.current
    var contactPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // 加载联系人头像
    LaunchedEffect(contact.contactId, contact.photoUri) {
        contactPhoto = if (contact.contactId != null) {
            loadContactPhoto(context, contact.contactId)
        } else if (contact.photoUri != null) {
            loadPhotoFromUri(context, contact.photoUri)
        } else {
            null
        }
    }

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
                .padding(ScaleHelper.scaledDp(16, scale)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ScaleHelper.scaledDp(24, scale)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale))
            ) {
                Text("确认拨打电话", fontSize = ScaleHelper.scaledSp(24, scale), fontWeight = FontWeight.Bold)

                // 联系人头像
                Box(
                    modifier = Modifier
                        .size(ScaleHelper.scaledDp(80, scale))
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
                            modifier = Modifier.size(ScaleHelper.scaledDp(40, scale)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(contact.name, fontSize = ScaleHelper.scaledSp(22, scale), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Text(contact.phoneNumber, fontSize = ScaleHelper.scaledSp(18, scale), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("长按红色按钮拨出", fontSize = ScaleHelper.scaledSp(16, scale), color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 核心交互区域
                val buttonSize = ScaleHelper.scaledDp(150, scale)
                Box(
                    modifier = Modifier
                        .size(buttonSize)
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
                    val strokeWidth = ScaleHelper.scaledDp(10, scale)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidthPx = strokeWidth.toPx()
                        val radius = (size.minDimension - strokeWidthPx) / 2
                        val center = Offset(size.width / 2, size.height / 2)

                        // 背景灰色圆环
                        drawCircle(
                            color = colorScheme.surfaceVariant,
                            radius = radius,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthPx)
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
                                    width = strokeWidthPx,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                    }

                    // 内部的圆形"按钮"视觉样式
                    Box(
                        modifier = Modifier
                            .size(ScaleHelper.scaledDp(100, scale))
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
                            modifier = Modifier.size(ScaleHelper.scaledDp(48, scale)),
                            tint = Color.White
                        )
                    }
                }

                TextButton(onClick = onCancel) {
                    Text("取消", fontSize = ScaleHelper.scaledSp(18, scale))
                }
            }
        }
    }
}
fun loadContactPhoto(context: Context, contactId: Long): Bitmap? {
    return try {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val inputStream: InputStream? = ContactsContract.Contacts.openContactPhotoInputStream(
            context.contentResolver,
            uri,
            true // 使用高分辨率照片
        )
        inputStream?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: Exception) {
        e.printStackTrace()
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
        e.printStackTrace()
        null
    }
}
