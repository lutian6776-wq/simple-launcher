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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("一键呼叫", fontSize = ScaleHelper.scaledSp(24, scale)) },
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
                
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = ScaleHelper.scaledDp(3, scale),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        text = { 
                            Text(
                                "家人", 
                                fontSize = ScaleHelper.scaledSp(18, scale),
                                fontWeight = if (pagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        text = { 
                            Text(
                                "其他", 
                                fontSize = ScaleHelper.scaledSp(18, scale),
                                fontWeight = if (pagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> FamilyPage(
                    contacts = uiState.familyContacts,
                    scale = scale,
                    onCall = { contact ->
                        scope.launch {
                            viewModel.initiateCall(contact)
                        }
                    }
                )
                1 -> OtherPage(
                    contacts = uiState.otherContacts,
                    scale = scale,
                    onCall = { contact ->
                        scope.launch {
                            viewModel.initiateCall(contact)
                        }
                    }
                )
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

@Composable
fun FamilyPage(
    contacts: List<EmergencyContact>,
    scale: Float,
    onCall: (EmergencyContact) -> Unit
) {
    if (contacts.isEmpty()) {
         Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale))
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    modifier = Modifier.size(ScaleHelper.scaledDp(64, scale)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "暂无家人",
                    fontSize = ScaleHelper.scaledSp(20, scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale)),
            horizontalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale)),
            contentPadding = PaddingValues(ScaleHelper.scaledDp(16, scale)),
            modifier = Modifier.fillMaxSize()
        ) {
            items(contacts.size) { index ->
                val contact = contacts[index]
                FamilyContactItem(
                    contact = contact,
                    scale = scale,
                    onCall = { onCall(contact) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OtherPage(
    contacts: List<EmergencyContact>,
    scale: Float,
    onCall: (EmergencyContact) -> Unit
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ScaleHelper.scaledDp(16, scale))
            ) {
                Icon(
                    Icons.Default.Contacts,
                    contentDescription = null,
                    modifier = Modifier.size(ScaleHelper.scaledDp(64, scale)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "暂无其他联系人",
                    fontSize = ScaleHelper.scaledSp(20, scale),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val groupedContacts = remember(contacts) {
            contacts.groupBy { it.initialLetter ?: "#" }.toSortedMap()
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            groupedContacts.forEach { (letter, groupContacts) ->
                stickyHeader {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = ScaleHelper.scaledDp(16, scale), vertical = ScaleHelper.scaledDp(8, scale))
                    ) {
                        Text(
                            text = letter,
                            fontSize = ScaleHelper.scaledSp(18, scale),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                items(groupContacts) { contact ->
                    Box(modifier = Modifier.padding(
                        horizontal = ScaleHelper.scaledDp(8, scale),
                        vertical = ScaleHelper.scaledDp(4, scale)
                    )) {
                        EmergencyCallListItem(
                            contact = contact,
                            scale = scale,
                            onCall = { onCall(contact) }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyCallListItem(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyContactItem(
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
            .aspectRatio(0.8f) // 接近方形但略高
            .clickable { onCall() },
        elevation = CardDefaults.cardElevation(defaultElevation = ScaleHelper.scaledDp(4, scale)),
        shape = MaterialTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (contactPhoto != null) {
                Image(
                    bitmap = contactPhoto!!.asImageBitmap(),
                    contentDescription = contact.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = contact.name,
                        modifier = Modifier.size(ScaleHelper.scaledDp(48, scale)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // 底部文字背景
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    ))
                    .padding(ScaleHelper.scaledDp(8, scale))
            ) {
                Text(
                    text = contact.name,
                    color = Color.White,
                    fontSize = ScaleHelper.scaledSp(18, scale),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                    maxLines = 1
                )
            }
        }
    }
}
