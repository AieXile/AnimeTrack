package com.aiexile.animetrack.ui.home

import android.net.Uri
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import com.aiexile.animetrack.ui.icons.AppIcon
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.dialogBlurBehind
import com.aiexile.animetrack.ui.components.sweepHighlight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.launch

/** 账号面板中当前展开操作项的账号（同一时间只展开一个） */
private enum class AccountActionTarget { User, Bilibili, Bangumi }

@Composable
fun AccountPanelDialog(
    onDismiss: () -> Unit,
    onNavigateUserLogin: () -> Unit,
    onNavigateBilibiliLogin: () -> Unit,
    onNavigateBangumiLogin: () -> Unit,
    onNavigateBangumiAccount: () -> Unit
) {
    val authManager = remember { AppContainer.getAuthManager() }
    val bilibiliAuthManager = remember { AppContainer.getBilibiliAuthManager() }
    val userAuthManager = remember { AppContainer.getUserAuthManager() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val notLoggedInText = stringResource(R.string.account_panel_not_logged_in)
    val connectedText = stringResource(R.string.account_panel_connected)
    val accountManagementText = stringResource(R.string.account_panel_account_management)
    val logoutText = stringResource(R.string.account_panel_logout)

    val bangumiLoggedIn by authManager.isLoggedIn.collectAsState(initial = false)
    val bangumiNickname by authManager.userNickname.collectAsState(initial = null)
    val bangumiAvatar by authManager.userAvatar.collectAsState(initial = null)
    val bangumiExpired by authManager.tokenExpired.collectAsState(initial = false)
    val customAvatarUri by authManager.customAvatarUri.collectAsState(initial = null)

    val bilibiliLoggedIn by bilibiliAuthManager.isLoggedIn.collectAsState(initial = false)
    val bilibiliNickname by bilibiliAuthManager.userNickname.collectAsState(initial = null)
    val bilibiliAvatar by bilibiliAuthManager.userAvatar.collectAsState(initial = null)
    val bilibiliExpired by bilibiliAuthManager.tokenExpired.collectAsState(initial = false)

    val userLoggedIn by userAuthManager.isLoggedIn.collectAsState(initial = false)
    val userUsername by userAuthManager.username.collectAsState(initial = null)
    val userAvatarPath by userAuthManager.avatar.collectAsState(initial = null)
    val userExpired by userAuthManager.tokenExpired.collectAsState(initial = false)
    // 服务器头像存储的是相对路径，需拼接为完整 URL
    val userAvatar = userAvatarPath?.let { if (it.startsWith("http")) it else "https://www.aiexile.top$it" }

    val anyLoggedIn = userLoggedIn || bangumiLoggedIn || bilibiliLoggedIn
    // 头像优先级：自定义头像 > 服务器头像 > Bilibili 头像 > Bangumi 头像
    val displayAvatar = customAvatarUri ?: userAvatar ?: bilibiliAvatar ?: bangumiAvatar
    val displayName = userUsername ?: bilibiliNickname ?: bangumiNickname ?: notLoggedInText

    // 当前展开操作项的账号：点击已登录账号行时展开，再次点击同一行收起
    var expandedAccount by remember { mutableStateOf<AccountActionTarget?>(null) }
    // 头像点击扫光触发器：点击头像只播放高亮扫光，不执行其他操作
    var avatarSweepTrigger by remember { mutableIntStateOf(0) }

    val panelDialogProperties = remember {
        DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                authManager.saveCustomAvatarUri(uri.toString())
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = panelDialogProperties
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .dialogBlurBehind()
        ) {
            // 透明遮罩：点击面板外的空白区域关闭面板（窗口自带 DIM_BEHIND，不额外压暗）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                var cardHeightPx by remember { mutableIntStateOf(0) }
                // 收起态高度：面板按该高度居中定位；展开时顶部固定，新增高度全部向下生长
                var collapsedHeightPx by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val availableHeightPx = with(density) { maxHeight.toPx() }
                val cardWidth = minOf(AccountPanelSpec.MaxWidth, maxWidth * AccountPanelSpec.WidthFraction)
                val centeredTop = (availableHeightPx - collapsedHeightPx) / 2f
                val maxTop = (availableHeightPx - cardHeightPx).coerceAtLeast(0f)
                val topOffset = with(density) {
                    minOf(centeredTop, maxTop).coerceAtLeast(0f).toDp()
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = topOffset)
                        .width(cardWidth)
                        // 首帧尚未测量时先隐藏，避免面板从错误位置闪入
                        .graphicsLayer { alpha = if (cardHeightPx == 0) 0f else 1f }
                        .onSizeChanged { size ->
                            cardHeightPx = size.height
                            if (collapsedHeightPx == 0) collapsedHeightPx = size.height
                        }
                        // 消费面板内的空白点击，避免穿透到遮罩误关闭
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = SquircleShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 32.dp, bottom = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 头像区域
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    color = if (anyLoggedIn) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape
                                )
                                .sweepHighlight(
                                    trigger = avatarSweepTrigger,
                                    // 比欢迎语更快；ease-in 曲线让前半段偏慢、后半段加速
                                    durationMillis = 500,
                                    easing = FastOutLinearInEasing
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { avatarSweepTrigger++ },
                            contentAlignment = Alignment.Center
                        ) {
                            if (displayAvatar != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(
                                            if (customAvatarUri != null) customAvatarUri!!.toUri()
                                            else displayAvatar
                                        )
                                        .build(),
                                    contentDescription = stringResource(R.string.account_panel_avatar),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.ic_launcher_foreground),
                                    fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                                )
                            } else {
                                Icon(
                                    painter = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                                    contentDescription = stringResource(R.string.account_panel_default_avatar),
                                    modifier = Modifier.size(32.dp),
                                    tint = if (anyLoggedIn) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (anyLoggedIn) displayName else notLoggedInText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // AnimeTrack 服务器账号条目（排第一）
                        AccountServiceRow(
                            title = "AnimeTrack",
                            subtitle = when {
                                userLoggedIn && userExpired -> stringResource(R.string.token_expired_card_subtitle)
                                userLoggedIn -> userUsername ?: connectedText
                                else -> stringResource(R.string.account_panel_login_to_sync)
                            },
                            icon = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                            isConnected = userLoggedIn,
                            expired = userLoggedIn && userExpired,
                            expanded = expandedAccount == AccountActionTarget.User,
                            onToggle = {
                                if (userLoggedIn && !userExpired) {
                                    expandedAccount = if (expandedAccount == AccountActionTarget.User) {
                                        null
                                    } else {
                                        AccountActionTarget.User
                                    }
                                } else {
                                    // 未登录或已失效：直达登录页强制重新登录
                                    onDismiss()
                                    onNavigateUserLogin()
                                }
                            },
                            actions = listOf(
                                AccountRowAction(label = accountManagementText) {
                                    expandedAccount = null
                                    onDismiss()
                                    onNavigateUserLogin()
                                },
                                AccountRowAction(label = logoutText, destructive = true) {
                                    expandedAccount = null
                                    scope.launch { userAuthManager.logout() }
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bilibili 条目
                        AccountServiceRow(
                            title = "Bilibili",
                            subtitle = when {
                                bilibiliLoggedIn && bilibiliExpired -> stringResource(R.string.token_expired_card_subtitle)
                                bilibiliLoggedIn -> bilibiliNickname ?: connectedText
                                else -> stringResource(R.string.account_panel_bind_bilibili)
                            },
                            icon = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                            isConnected = bilibiliLoggedIn,
                            expired = bilibiliLoggedIn && bilibiliExpired,
                            expanded = expandedAccount == AccountActionTarget.Bilibili,
                            onToggle = {
                                if (bilibiliLoggedIn && !bilibiliExpired) {
                                    expandedAccount = if (expandedAccount == AccountActionTarget.Bilibili) {
                                        null
                                    } else {
                                        AccountActionTarget.Bilibili
                                    }
                                } else {
                                    // 未登录或已失效：直达登录页强制重新登录
                                    onDismiss()
                                    onNavigateBilibiliLogin()
                                }
                            },
                            actions = listOf(
                                AccountRowAction(label = accountManagementText) {
                                    expandedAccount = null
                                    onDismiss()
                                    onNavigateBilibiliLogin()
                                },
                                AccountRowAction(label = logoutText, destructive = true) {
                                    expandedAccount = null
                                    scope.launch { bilibiliAuthManager.logout() }
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Bangumi 条目
                        AccountServiceRow(
                            title = "Bangumi",
                            subtitle = when {
                                bangumiLoggedIn && bangumiExpired -> stringResource(R.string.token_expired_card_subtitle)
                                bangumiLoggedIn -> bangumiNickname ?: connectedText
                                else -> stringResource(R.string.account_panel_bind_bangumi)
                            },
                            icon = rememberAppIconPainter(AppIcon.ACCOUNT_CIRCLE),
                            isConnected = bangumiLoggedIn,
                            expired = bangumiLoggedIn && bangumiExpired,
                            expanded = expandedAccount == AccountActionTarget.Bangumi,
                            onToggle = {
                                if (bangumiLoggedIn && !bangumiExpired) {
                                    expandedAccount = if (expandedAccount == AccountActionTarget.Bangumi) {
                                        null
                                    } else {
                                        AccountActionTarget.Bangumi
                                    }
                                } else {
                                    // 未登录或已失效：直达登录页强制重新登录
                                    onDismiss()
                                    onNavigateBangumiLogin()
                                }
                            },
                            actions = listOf(
                                AccountRowAction(label = accountManagementText) {
                                    expandedAccount = null
                                    onDismiss()
                                    onNavigateBangumiAccount()
                                },
                                AccountRowAction(label = logoutText, destructive = true) {
                                    expandedAccount = null
                                    scope.launch { authManager.logout() }
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 自定义头像：纯文字按钮，点击直接选择图片（不再弹更换头像对话框）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    expandedAccount = null
                                    pickImageLauncher.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (customAvatarUri != null) {
                                    stringResource(R.string.account_panel_change_avatar)
                                } else {
                                    stringResource(R.string.account_panel_custom_avatar)
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 账号面板卡片尺寸：沿用 M3 AlertDialog 的 560dp 上限与平台宽度比例 */
private object AccountPanelSpec {
    val MaxWidth = 560.dp
    const val WidthFraction = 0.92f
}
