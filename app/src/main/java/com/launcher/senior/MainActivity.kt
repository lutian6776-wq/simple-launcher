package com.launcher.senior

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.draw.clip
import kotlin.math.ceil
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import com.launcher.senior.data.AppInfo
import com.launcher.senior.ui.AppIcon
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.util.SystemInfoHelper
import com.launcher.senior.viewmodel.MainViewModel
import com.launcher.senior.EmergencyCallActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        }
    }
    
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页面返回时刷新数据
        viewModel.loadData(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查并请求权限
        checkPermissions()
        
        setContent {
            SeniorLauncherTheme {
                MainScreen(viewModel = viewModel, onSettingsClick = {
                    val intent = Intent(this, SettingsActivity::class.java)
                    settingsLauncher.launch(intent)
                })
            }
        }
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 检查电话权限
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadData(context)
        SystemInfoHelper.startMonitoring(context)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            SystemInfoHelper.stopMonitoring(context)
        }
    }
    
    Scaffold(
        topBar = {
            CustomTopBar(
                onLongPressTitle = onSettingsClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 一键呼叫和文件快捷方式按钮（左右排列）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 一键呼叫按钮 - 跳转到紧急呼叫界面
                CallButton(
                    phoneNumber = uiState.emergencyPhoneNumber,
                    onClick = {
                        context.startActivity(Intent(context, EmergencyCallActivity::class.java))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                )
                
                // 文件快捷方式入口
                FilesButton(
                    onClick = {
                        context.startActivity(Intent(context, FilesActivity::class.java))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                )
            }
            
            // 快捷应用网格（3*3分页）
            if (uiState.quickApps.isNotEmpty() || uiState.customApps.isNotEmpty()) {
                val allApps = uiState.quickApps + uiState.customApps
                val itemsPerPage = 9 // 3*3
                
                Column {
                    AppPager(
                        allApps = allApps,
                        itemsPerPage = itemsPerPage,
                        onAppClick = { app -> viewModel.onAppClick(context, app) }
                    )
                }
            } else {
                // 空状态提示
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "请在设置中添加应用",
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CallButton(
    phoneNumber: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(255, 0, 0), // 深红色 rgb(255, 0, 0)
            contentColor = Color.White // 纯白
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White // 纯白
            )
            Text(
                text = "一键呼叫",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White // 纯白
            )
            if (phoneNumber.isNotEmpty()) {
                Text(
                    text = phoneNumber,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White // 纯白
                )
            } else {
                Text(
                    text = "请在设置中配置电话号码",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun FilesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(51, 102, 0), // rgb(51, 102, 0)
            contentColor = Color.White // 纯白
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = "文件宝",
                modifier = Modifier.size(48.dp),
                tint = Color.White // 纯白
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "文件宝",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White // 纯白
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPager(
    allApps: List<AppInfo>,
    itemsPerPage: Int = 9, // 默认 3x3
    onAppClick: (AppInfo) -> Unit
) {
    // 1. 提前计算总页数，避免在 UI 渲染路径中计算
    val totalPages = remember(allApps.size, itemsPerPage) {
        if (allApps.isEmpty()) 1 else ceil(allApps.size.toDouble() / itemsPerPage).toInt()
    }
    
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPages })
    
    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f) // 让 Pager 占据剩余空间
                .fillMaxWidth()
        ) { page ->
            // 3. 使用 LazyVerticalGrid 代替手动 Row/Column
            // 使用 remember 缓存页面应用列表，避免每次滑动都重新计算
            val startIndex = page * itemsPerPage
            val pageApps = remember(page, allApps.size) {
                if (startIndex < allApps.size) {
                    allApps.subList(startIndex, minOf(startIndex + itemsPerPage, allApps.size))
                } else {
                    emptyList()
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3), // 固定3列
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = pageApps,
                    key = { app -> app.id } // 使用唯一key优化重组，避免不必要的重新组合
                ) { app ->
                    QuickAppButton(
                        app = app,
                        onClick = { onAppClick(app) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }

        // 页面指示器
        if (totalPages > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                PagerIndicator(
                    count = totalPages,
                    currentPage = pagerState.currentPage
                )
            }
        }
    }
}

@Composable
fun PagerIndicator(count: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (currentPage == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAppButton(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 使用实际应用图标
            AppIcon(
                packageName = app.packageName,
                activityName = app.activityName,
                defaultIcon = app.icon,
                modifier = Modifier,
                size = 48.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：时间
            Text(
                text = systemInfo.currentTime.ifEmpty { "--:--" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // 中间：标题（可长按）
            Text(
                text = "老人桌面",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onLongPressTitle() }
                        )
                    },
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            // 右侧：电量和信号
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WiFi信号
                if (systemInfo.isWifiConnected) {
                    WifiSignalIcon(strength = systemInfo.wifiSignalStrength)
                }
                
                // 移动数据信号
                if (systemInfo.isMobileDataEnabled) {
                    MobileSignalIcon(strength = systemInfo.mobileSignalStrength)
                }
                
                // 电量
                BatteryIcon(
                    level = systemInfo.batteryLevel,
                    isCharging = systemInfo.isCharging
                )
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
                // 根据信号强度调整透明度：4=1.0, 3=0.75, 2=0.5, 1=0.25
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
                // 根据信号强度调整透明度：4=1.0, 3=0.75, 2=0.5, 1=0.25
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
