package com.launcher.senior

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.launcher.senior.data.EmergencyContact
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.util.SystemInfoHelper
import com.launcher.senior.viewmodel.EmergencyCallViewModel
import com.launcher.senior.viewmodel.FilesViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val emergencyViewModel: EmergencyCallViewModel by viewModels()
    private val filesViewModel: FilesViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            emergencyViewModel.loadContacts(this)
        }
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        emergencyViewModel.loadContacts(this)
        filesViewModel.initialize(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContent {
            SeniorLauncherTheme {
                MainScreen(
                    emergencyViewModel = emergencyViewModel,
                    filesViewModel = filesViewModel,
                    onSettingsClick = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        settingsLauncher.launch(intent)
                    }
                )
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS
            )
            val neededPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (neededPermissions.isNotEmpty()) {
                requestPermissionLauncher.launch(neededPermissions.first())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    emergencyViewModel: EmergencyCallViewModel,
    filesViewModel: FilesViewModel,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    val emergencyUiState by emergencyViewModel.uiState.collectAsState()
    val filesUiState by filesViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        emergencyViewModel.loadContacts(context)
        filesViewModel.initialize(context)
        SystemInfoHelper.startMonitoring(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            SystemInfoHelper.stopMonitoring(context)
        }
    }


    // Track long-press progress
    var longPressProgress by remember { mutableStateOf(0f) }
    var isLongPressing by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            Box {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {},  // No action, just indicator
                        text = { Text("一键拨号", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {},  // No action, just indicator
                        text = { Text("文件宝", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Transparent overlay for long-press detection
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    android.util.Log.d("MainActivity", "Press started")
                                    isLongPressing = true
                                    val pressStartTime = System.currentTimeMillis()
                                    val longPressDuration = 1500L // 1.5 seconds
                                    
                                    // Animate progress
                                    scope.launch {
                                        while (isLongPressing && longPressProgress < 1f) {
                                            val elapsed = System.currentTimeMillis() - pressStartTime
                                            longPressProgress = (elapsed.toFloat() / longPressDuration).coerceIn(0f, 1f)
                                            
                                            if (longPressProgress >= 1f) {
                                                android.util.Log.d("MainActivity", "Long press completed, triggering settings")
                                                onSettingsClick()
                                                break
                                            }
                                            kotlinx.coroutines.delay(16) // ~60fps
                                        }
                                    }
                                    
                                    tryAwaitRelease()
                                    android.util.Log.d("MainActivity", "Press released, progress: $longPressProgress")
                                    isLongPressing = false
                                    longPressProgress = 0f
                                }
                            )
                        }
                )

                // Progress indicator overlay
                if (longPressProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f * longPressProgress)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.size(80.dp)
                        ) {
                            val strokeWidth = 8.dp.toPx()
                            val radius = (size.minDimension - strokeWidth) / 2
                            
                            // Background circle
                            drawCircle(
                                color = Color.White.copy(alpha = 0.3f),
                                radius = radius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                            
                            // Progress arc
                            drawArc(
                                color = Color.White,
                                startAngle = -90f,
                                sweepAngle = 360f * longPressProgress,
                                useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> OneTouchDialingTab(
                    contacts = emergencyUiState.contacts,
                    onContactClick = { contact ->
                        emergencyViewModel.initiateCall(contact)
                    }
                )
                1 -> FilesTab(
                    uiState = filesUiState,
                    viewModel = filesViewModel
                )
            }
        }

        emergencyUiState.callConfirmation?.let { contact ->
            CallConfirmationDialog(
                contact = contact,
                onConfirm = {
                    emergencyViewModel.confirmCall(context, contact.phoneNumber)
                },
                onCancel = {
                    emergencyViewModel.cancelCall()
                }
            )
        }
    }
}

@Composable
fun OneTouchDialingTab(
    contacts: List<EmergencyContact>,
    onContactClick: (EmergencyContact) -> Unit
) {
    if (contacts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                    "还没有添加联系人",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "请长按顶部标题进入设置添加",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(contacts) { contact ->
                ContactCallItem(contact = contact, onCall = { onContactClick(contact) })
            }
        }
    }
}



@Composable
fun FilesTab(
    uiState: com.launcher.senior.viewmodel.FilesUiState,
    viewModel: FilesViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    


    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.fileShortcuts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
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
                        "请长按顶部标题进入设置添加",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.fileShortcuts) { shortcut ->
                    FileShortcutItem(
                        shortcut = shortcut,
                        onClick = { viewModel.openFile(context, shortcut) }
                    )
                }
            }
            
        }
    }

    uiState.showAppSelector?.let { shortcut ->
        DefaultAppSelectorDialog(
            shortcut = shortcut,
            onAppSelected = { packageName, activityName ->
                scope.launch { viewModel.setDefaultApp(context, shortcut, packageName, activityName) }
            },
            onDismiss = { viewModel.dismissAppSelector() }
        )
    }
    
    uiState.showRenameDialog?.let { shortcut ->
        RenameDialog(
            currentName = shortcut.name,
            onConfirm = { newName ->
                scope.launch { viewModel.renameFileShortcut(context, shortcut, newName) }
            },
            onDismiss = { viewModel.dismissRenameDialog() }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTopBar(
    onLongPressTitle: () -> Unit
) {
    val systemInfo by SystemInfoHelper.systemInfo.collectAsState()
    
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f))
            Text(
                text = "老人桌面",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onLongPressTitle() }
                        )
                    },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (systemInfo.isWifiConnected) {
                        WifiSignalIcon(strength = systemInfo.wifiSignalStrength)
                    }
                    if (systemInfo.isMobileDataEnabled) {
                        MobileSignalIcon(strength = systemInfo.mobileSignalStrength)
                    }
                    BatteryIcon(
                        level = systemInfo.batteryLevel,
                        isCharging = systemInfo.isCharging
                    )
                }
            }
        }
    }
}

@Composable
fun WifiSignalIcon(strength: Int) {
    val icon = when {
        strength >= 4 -> Icons.Default.SignalWifi4Bar
        strength >= 3 -> Icons.Default.SignalWifi4Bar
        strength >= 2 -> Icons.Default.SignalWifi4Bar
        strength >= 1 -> Icons.Default.SignalWifi4Bar
        else -> Icons.Default.SignalWifiOff
    }
    Icon(
        imageVector = icon,
        contentDescription = "WiFi信号",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(
            alpha = if (strength > 0) {
                when (strength) {
                    4 -> 1.0f
                    3 -> 0.75f
                    2 -> 0.5f
                    1 -> 0.25f
                    else -> 0.5f
                }
            } else 0.5f
        )
    )
}

@Composable
fun MobileSignalIcon(strength: Int) {
    val icon = when {
        strength >= 4 -> Icons.Default.SignalCellular4Bar
        strength >= 3 -> Icons.Default.SignalCellular4Bar
        strength >= 2 -> Icons.Default.SignalCellular4Bar
        strength >= 1 -> Icons.Default.SignalCellular4Bar
        else -> Icons.Default.SignalCellularOff
    }
    Icon(
        imageVector = icon,
        contentDescription = "移动信号",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(
            alpha = if (strength > 0) {
                when (strength) {
                    4 -> 1.0f
                    3 -> 0.75f
                    2 -> 0.5f
                    1 -> 0.25f
                    else -> 0.5f
                }
            } else 0.5f
        )
    )
}

@Composable
fun BatteryIcon(level: Int, isCharging: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
            contentDescription = "电量",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = "$level%",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
