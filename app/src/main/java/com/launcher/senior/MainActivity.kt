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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import kotlin.math.ceil
import kotlin.math.min
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import com.launcher.senior.data.AppInfo
import com.launcher.senior.ui.AppIcon
import com.launcher.senior.ui.theme.SeniorLauncherTheme
import com.launcher.senior.util.SystemInfoHelper
import com.launcher.senior.viewmodel.MainViewModel
import com.launcher.senior.EmergencyCallActivity

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
        val config = LocalConfiguration.current
        // 以 360dp 宽度为基准，按屏幕宽度缩放（限制在 0.85~1.6 之间）
        val scale = (config.screenWidthDp / 360f).coerceIn(0.85f, 1.6f)
        val topButtonHeight = (100 * scale).toInt().coerceAtLeast(72).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CallButton(
                    phoneNumber = uiState.emergencyPhoneNumber,
                    onClick = {
                        context.startActivity(Intent(context, EmergencyCallActivity::class.java))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(topButtonHeight),
                    scale = scale
                )
                FilesButton(
                    onClick = {
                        context.startActivity(Intent(context, FilesActivity::class.java))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(topButtonHeight),
                    scale = scale
                )
            }
            
            // 快捷应用网格（3*3分页）- 占满剩余空间
            if (uiState.quickApps.isNotEmpty() || uiState.customApps.isNotEmpty()) {
                val allApps = uiState.quickApps + uiState.customApps
                val itemsPerPage = 9
                Column(modifier = Modifier.weight(1f)) {
                    AppPager(
                        allApps = allApps,
                        itemsPerPage = itemsPerPage,
                        onAppClick = { app -> viewModel.onAppClick(context, app) },
                        scale = scale
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
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val iconSize = (48 * scale).toInt().coerceIn(28, 72).dp
    val titleSp = (32 * scale).toInt().coerceIn(18, 48).sp
    val subtitleSp = (24 * scale).toInt().coerceIn(14, 36).sp
    val hintSp = (18 * scale).toInt().coerceIn(12, 28).sp
    val innerSpacing = (8 * scale).toInt().coerceIn(4, 14).dp
    val cornerDp = (20 * scale).toInt().coerceIn(12, 32).dp

    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(cornerDp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(255, 0, 0),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(innerSpacing)
        ) {
            Icon(
                Icons.Default.Phone,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = Color.White
            )
            Text(
                text = "电话",
                fontSize = titleSp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (phoneNumber.isNotEmpty()) {
                Text(
                    text = phoneNumber,
                    fontSize = subtitleSp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White
                )
            } else {
                Text(
                    text = "请在设置中配置电话号码",
                    fontSize = hintSp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun FilesButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val iconSize = (48 * scale).toInt().coerceIn(28, 72).dp
    val titleSp = (32 * scale).toInt().coerceIn(18, 48).sp
    val innerSpacing = (8 * scale).toInt().coerceIn(4, 14).dp
    val cornerDp = (20 * scale).toInt().coerceIn(12, 32).dp

    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(cornerDp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(51, 102, 0),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(innerSpacing)
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = "文件宝",
                modifier = Modifier.size(iconSize),
                tint = Color.White
            )
            Text(
                text = "文件宝",
                fontSize = titleSp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppPager(
    allApps: List<AppInfo>,
    itemsPerPage: Int = 9,
    onAppClick: (AppInfo) -> Unit,
    scale: Float = 1f
) {
    val totalPages = remember(allApps.size, itemsPerPage) {
        if (allApps.isEmpty()) 1 else ceil(allApps.size.toDouble() / itemsPerPage).toInt()
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { totalPages })

    val gridPaddingH = (4 * scale).toInt().coerceIn(2, 8).dp
    val gridPaddingV = (8 * scale).toInt().coerceIn(4, 14).dp
    val gridSpacing = (10 * scale).toInt().coerceIn(6, 16).dp
    val indicatorPaddingV = (8 * scale).toInt().coerceIn(4, 14).dp

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            // 关键 1：只组合当前页，不预加载左右页（类似 Lawnchair 只布局可见页）
            // Compose Foundation 1.5.x 使用 beyondBoundsPageCount
            beyondBoundsPageCount = 0,
            // 关键 2：每页用 key 稳定，减少无效重组
            key = { it }
        ) { page ->
            val startIndex = page * itemsPerPage
            val pageApps = remember(page, allApps, itemsPerPage) {
                if (startIndex < allApps.size) {
                    allApps.subList(startIndex, minOf(startIndex + itemsPerPage, allApps.size))
                } else {
                    emptyList()
                }
            }
            // 关键 3：用固定 3x3 网格替代 LazyVerticalGrid，无 Lazy 测量/滑动开销
            FixedGridPage(
                pageApps = pageApps,
                onAppClick = onAppClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = gridPaddingH, vertical = gridPaddingV),
                gridSpacing = gridSpacing,
                scale = scale
            )
        }

        if (totalPages > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = indicatorPaddingV),
                contentAlignment = Alignment.Center
            ) {
                PagerIndicator(
                    count = totalPages,
                    currentPage = pagerState.currentPage,
                    scale = scale
                )
            }
        }
    }
}

@Composable
fun PagerIndicator(
    count: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val dotSize = (10 * scale).toInt().coerceIn(6, 16).dp
    val dotSpacing = (8 * scale).toInt().coerceIn(4, 14).dp
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(dotSpacing)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (currentPage == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

/** 固定 3x3 网格，不接 Lazy，适合每页 9 个的固定布局（参考 Lawnchair 单页即一屏内容） */
@Composable
private fun FixedGridPage(
    pageApps: List<AppInfo>,
    onAppClick: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    gridSpacing: Dp = 10.dp,
    scale: Float = 1f
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(gridSpacing)
    ) {
        repeat(3) { rowIndex ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing)
            ) {
                repeat(3) { colIndex ->
                    val index = rowIndex * 3 + colIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                    ) {
                        if (index < pageApps.size) {
                            val app = pageApps[index]
                            QuickAppButton(
                                app = app,
                                onClick = { onAppClick(app) },
                                modifier = Modifier.fillMaxSize(),
                                scale = scale
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
fun QuickAppButton(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f
) {
    val titleSp = (15 * scale).toInt().coerceIn(12, 22).sp
    val lineHeightSp = (18 * scale).toInt().coerceIn(14, 26).sp
    val innerPadding = (4 * scale).toInt().coerceIn(2, 8).dp
    val cornerDp = (12 * scale).toInt().coerceIn(8, 20).dp
    val elevationDp = (4 * scale).toInt().coerceIn(2, 8).dp

    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        shape = RoundedCornerShape(cornerDp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevationDp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            val cellSize = if (maxWidth < maxHeight) maxWidth else maxHeight
            // 图标占按钮边长的大约 70%，防止溢出
            val iconSize = (cellSize * 0.7f).coerceAtMost(cellSize)
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AppIcon(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    defaultIcon = app.icon,
                    modifier = Modifier,
                    size = iconSize
                )
                Spacer(modifier = Modifier.height((4 * scale).toInt().coerceIn(2, 8).dp))
                Text(
                    text = app.name,
                    fontSize = titleSp,
                    lineHeight = lineHeightSp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧占位（与右侧等宽，使标题居中；时间在系统状态栏已有）
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
