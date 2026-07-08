package com.aiexile.animetrack.ui.settings

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.aiexile.animetrack.data.network.BilibiliFollowItem
import com.aiexile.animetrack.data.network.RetrofitClient
import com.aiexile.animetrack.data.sync.BilibiliSyncManager
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.util.QrCodeGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_QR_RETRY = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliLoginScreen(
    onBack: () -> Unit
) {
    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
    val bilibiliSyncManager = remember { AppContainer.getBilibiliSyncManager() }
    val settingsRepository = remember { AppContainer.getSettingsRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val isLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
    val userAvatar by bilibiliAuthManager.userAvatar.collectAsState(initial = null)
    val userNickname by bilibiliAuthManager.userNickname.collectAsState(initial = null)
    val lastSyncTime by bilibiliAuthManager.lastSyncTime.collectAsState(initial = 0L)
    val autoSyncEnabled by bilibiliAuthManager.bilibiliAutoSync.collectAsState(initial = false)
    val autoSyncVisible by settingsRepository.autoSyncVisible.collectAsState(initial = false)

    var qrCodeUrl by remember { mutableStateOf<String?>(null) }
    var qrCodeKey by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGeneratingQr by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var retryCount by remember { mutableIntStateOf(0) }
    var generateTrigger by remember { mutableIntStateOf(0) }

    // 同步弹窗相关状态
    var showSyncDialog by remember { mutableStateOf(false) }
    var unwatchedItems by remember { mutableStateOf<List<BilibiliFollowItem>>(emptyList()) }
    var selectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var watchedItems by remember { mutableStateOf<List<BilibiliFollowItem>>(emptyList()) }

    // 生成二维码
    LaunchedEffect(generateTrigger) {
        if (generateTrigger <= 0) return@LaunchedEffect
        if (isLoggedIn) return@LaunchedEffect
        if (retryCount >= MAX_QR_RETRY) return@LaunchedEffect

        isGeneratingQr = true
        loginMessage = null
        try {
            val response = RetrofitClient.bilibiliLoginApi.generateQrCode()
            if (response.code == 0 && response.data != null) {
                qrCodeUrl = response.data.url
                qrCodeKey = response.data.qrcodeKey
                qrBitmap = QrCodeGenerator.generateBitmap(response.data.url, 600)
            } else {
                loginMessage = "生成二维码失败: ${response.message}"
                retryCount++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loginMessage = "网络错误: ${e.message}"
            retryCount++
        } finally {
            isGeneratingQr = false
        }
    }

    LaunchedEffect(Unit) {
        if (!isLoggedIn) {
            generateTrigger = 1
        }
    }

    // 轮询扫码状态
    LaunchedEffect(qrCodeKey) {
        if (qrCodeKey.isNullOrBlank()) return@LaunchedEffect

        while (true) {
            try {
                delay(3000)
                val rawResponse = RetrofitClient.bilibiliLoginApi.pollQrCode(qrCodeKey!!)
                val body = rawResponse.body()
                if (rawResponse.isSuccessful && body != null && body.code == 0 && body.data?.code == 0) {
                    val cookies = rawResponse.headers().values("Set-Cookie")
                    var sessData = ""
                    var biliJct = ""

                    for (line in cookies) {
                        if (line.contains("SESSDATA")) {
                            for (part in line.split(";")) {
                                val trimPart = part.trim()
                                if (trimPart.startsWith("SESSDATA=")) {
                                    sessData = trimPart.substringAfter("SESSDATA=")
                                    break
                                }
                            }
                        }
                        if (line.contains("bili_jct")) {
                            for (part in line.split(";")) {
                                val trimPart = part.trim()
                                if (trimPart.startsWith("bili_jct=")) {
                                    biliJct = trimPart.substringAfter("bili_jct=")
                                    break
                                }
                            }
                        }
                    }

                    // 先保存基本 session，让 bilibiliCookieJar 能注入 Cookie
                    bilibiliAuthManager.saveSession(sessData, biliJct, 0L)

                    val navResp = RetrofitClient.bilibiliApi.getNavInfo()
                    if (navResp.code == 0 && navResp.data != null) {
                        bilibiliAuthManager.saveUserProfile(
                            avatar = navResp.data.face,
                            nickname = navResp.data.uname,
                            mid = navResp.data.mid
                        )
                    }
                    loginMessage = "登录成功"
                    break
                } else if (body != null && body.data?.code == 86038) {
                    loginMessage = "二维码已过期，请重新生成"
                    qrCodeKey = null
                    qrCodeUrl = null
                    qrBitmap = null
                    break
                } else if (body != null && body.data?.code == 86090) {
                    loginMessage = "请在手机上确认登录"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { }
        }
    }

    // 同步弹窗
    if (showSyncDialog && unwatchedItems.isNotEmpty()) {
        SyncSelectionDialog(
            items = unwatchedItems,
            selectedIndices = selectedIndices,
            onSelectionChange = { selectedIndices = it },
            onConfirm = {
                showSyncDialog = false
                // 合并有观看进度的 + 用户选中的未观看的
                val itemsToSync = watchedItems + unwatchedItems.filterIndexed { index, _ -> index in selectedIndices }
                scope.launch {
                    isSyncing = true
                    syncResult = null
                    val result = bilibiliSyncManager.syncSelectedItems(itemsToSync)
                    syncResult = if (result.isSuccess) {
                        "同步完成，共 ${result.getOrNull()} 部番剧"
                    } else {
                        "同步失败: ${result.exceptionOrNull()?.message}"
                    }
                    isSyncing = false
                }
            },
            onSkipUnwatched = {
                showSyncDialog = false
                // 只同步有观看进度的
                scope.launch {
                    isSyncing = true
                    syncResult = null
                    val result = bilibiliSyncManager.syncSelectedItems(watchedItems)
                    syncResult = if (result.isSuccess) {
                        "同步完成，共 ${result.getOrNull()} 部番剧"
                    } else {
                        "同步失败: ${result.exceptionOrNull()?.message}"
                    }
                    isSyncing = false
                }
            },
            onDismiss = {
                showSyncDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bilibili 登录",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoggedIn) {
                if (!userAvatar.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(userAvatar),
                        contentDescription = "头像",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = userNickname ?: "已登录",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已登录",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))

                if (lastSyncTime > 0) {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    Text(
                        text = "上次同步: ${dateFormat.format(java.util.Date(lastSyncTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 自动同步开关
                if (autoSyncVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "自动同步",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "打开 App 时自动同步连载中和在看番剧的更新",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoSyncEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    bilibiliAuthManager.setBilibiliAutoSync(enabled)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Button(
                    onClick = {
                        if (!isSyncing) {
                            isSyncing = true
                            syncResult = null
                        }
                    },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("拉取列表中...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (lastSyncTime > 0) "重新同步" else "同步追番列表")
                    }
                }

                LaunchedEffect(isSyncing) {
                    if (isSyncing) {
                        val fetchResult = bilibiliSyncManager.fetchFollowList()
                        if (fetchResult.isFailure) {
                            syncResult = "同步失败: ${fetchResult.exceptionOrNull()?.message}"
                            isSyncing = false
                            return@LaunchedEffect
                        }

                        val allItems = fetchResult.getOrDefault(emptyList())
                        val (watched, unwatched) = allItems.partition { item ->
                            val (watchedEps, _) = BilibiliSyncManager.parseProgressToWatchedEpisodes(item.progress)
                            watchedEps > 0
                        }

                        if (unwatched.isEmpty()) {
                            // 没有未看过的，直接全部同步
                            val result = bilibiliSyncManager.syncSelectedItems(allItems)
                            syncResult = if (result.isSuccess) {
                                "同步完成，共 ${result.getOrNull()} 部番剧"
                            } else {
                                "同步失败: ${result.exceptionOrNull()?.message}"
                            }
                            isSyncing = false
                        } else {
                            // 有未看过的，弹窗让用户选择
                            unwatchedItems = unwatched
                            watchedItems = watched
                            selectedIndices = unwatched.indices.toSet() // 默认全选
                            showSyncDialog = true
                            isSyncing = false
                        }
                    }
                }

                if (syncResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = syncResult!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (syncResult!!.startsWith("同步完成"))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            bilibiliAuthManager.logout()
                        }
                        syncResult = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("退出登录")
                }
            } else {
                if (isGeneratingQr) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在生成二维码...")
                } else if (qrBitmap != null) {
                    ScanLineQrBox(qrBitmap = qrBitmap!!)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "请使用B站手机客户端扫描二维码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val url = qrCodeUrl
                            if (url != null) {
                                try {
                                    val encodedUrl = Uri.encode(url)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("bilibili://browser?url=$encodedUrl")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    // 未安装B站App，尝试打开应用市场
                                    try {
                                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=tv.danmaku.bili"))
                                        context.startActivity(marketIntent)
                                    } catch (_: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.bilibili.com"))
                                        context.startActivity(webIntent)
                                    }
                                }
                            }
                        },
                        modifier = Modifier,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("打开B站")
                    }
                }

                loginMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (msg.contains("成功") || msg.contains("确认"))
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }

                if (qrBitmap == null && !isGeneratingQr) {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (retryCount < MAX_QR_RETRY) {
                        Button(onClick = {
                            loginMessage = null
                            generateTrigger++
                        }) {
                            Text("重新生成二维码")
                        }
                    } else {
                        Text(
                            text = "多次生成失败，请检查网络后重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            retryCount = 0
                            loginMessage = null
                            generateTrigger++
                        }) {
                            Text("重试")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncSelectionDialog(
    items: List<BilibiliFollowItem>,
    selectedIndices: Set<Int>,
    onSelectionChange: (Set<Int>) -> Unit,
    onConfirm: () -> Unit,
    onSkipUnwatched: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("以下番剧尚未观看")
        },
        text = {
            Column {
                Text(
                    text = "已选择 ${selectedIndices.size}/${items.size} 部添加为「计划观看」",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    itemsIndexed(items) { index, item ->
                        val isSelected = index in selectedIndices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectionChange(
                                        if (isSelected) selectedIndices - index
                                        else selectedIndices + index
                                    )
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onSkipUnwatched) {
                    Text("跳过")
                }
                Button(onClick = onConfirm) {
                    Text("确认添加")
                }
            }
        }
    )
}

/**
 * 扫描线擦除过渡容器：生成新二维码时，旧图自上而下被擦除，
 * 新图同步自上而下显露，形成"复印机"式方向感擦除过渡。
 * 参考 AyuGram QrCodeLoginView 的扫描线擦除动效（精简版）。
 */
@Composable
private fun ScanLineQrBox(qrBitmap: Bitmap) {
    // 双缓冲：oldBitmap 擦除淡出，currentBitmap 显露淡入
    var oldBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 用 Animatable 手动驱动动画，避免 animateFloatAsState 初始值与目标值相同导致无动画
    val scanProgress = remember { Animatable(1f) }

    LaunchedEffect(qrBitmap) {
        if (currentBitmap != qrBitmap) {
            oldBitmap = currentBitmap
            currentBitmap = qrBitmap
            // 重置到 0（旧图完整显示，新图未显露），然后动画到 1（旧图擦除完毕，新图完整显露）
            scanProgress.snapTo(0f)
            // 短暂延迟让旧图（若有）可见
            kotlinx.coroutines.delay(80)
            scanProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(1000, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val progress = scanProgress.value
        // 旧图：随扫描线从顶向下擦除（只显示扫描线下方区域）
        oldBitmap?.let { old ->
            Image(
                bitmap = old.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(200.dp)
                    .drawWithContent {
                        val scanY = progress * size.height
                        if (scanY < size.height) {
                            clipRect(0f, scanY, size.width, size.height) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )
        }
        // 新图：随扫描线从顶向下显露（只显示扫描线上方区域）
        currentBitmap?.let { current ->
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "二维码",
                modifier = Modifier
                    .size(200.dp)
                    .drawWithContent {
                        val scanY = progress * size.height
                        if (scanY > 0f) {
                            clipRect(0f, 0f, size.width, scanY) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )
        }
    }
}
