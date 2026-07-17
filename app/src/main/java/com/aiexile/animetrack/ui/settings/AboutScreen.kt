package com.aiexile.animetrack.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.components.MarkdownText
import com.aiexile.animetrack.ui.components.UsageStatsDialog
import com.aiexile.animetrack.ui.update.UpdateDialog
import com.aiexile.animetrack.ui.update.UpdateViewModel
import com.aiexile.animetrack.util.AppNavigator
import kotlinx.coroutines.launch

private const val GITHUB_URL = "https://github.com/AieXile/AnimeTrack"
private const val QQ_GROUP_NUMBER = "951059178"
private const val TG_URL = "https://t.me/AnimeTrackovo"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateDeveloper: () -> Unit = {}
) {
    BackHandler { onBack() }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val settingsRepository = remember { AppContainer.getSettingsRepository() }
    val scope = rememberCoroutineScope()
    val developerMode by settingsRepository.developerMode.collectAsState(initial = false)

    var avatarTapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var showSponsorDialog by remember { mutableStateOf(false) }

    val updateViewModel: UpdateViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val updateRepository = UpdateRepository()
                val settingsRepository = AppContainer.getSettingsRepository()
                return UpdateViewModel(updateRepository, settingsRepository) as T
            }
        }
    )

    UpdateDialog(viewModel = updateViewModel)

    if (showSponsorDialog) {
        SponsorDialog(onDismiss = { showSponsorDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        .clip(CircleShape)
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .combinedClickable(
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime > 2000) {
                                    avatarTapCount = 1
                                } else {
                                    avatarTapCount++
                                }
                                lastTapTime = now

                                if (!developerMode && avatarTapCount >= 5) {
                                    avatarTapCount = 0
                                    scope.launch {
                                        settingsRepository.setDeveloperMode(true)
                                        Toast.makeText(context, context.getString(R.string.about_dev_mode_enabled), Toast.LENGTH_SHORT).show()
                                    }
                                } else if (!developerMode && avatarTapCount == 3) {
                                    Toast.makeText(context, context.getString(R.string.about_dev_mode_hint), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.my_avatar),
                        contentDescription = stringResource(R.string.about_avatar),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.size(20.dp))

                Text(
                    text = "AieXile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.size(6.dp))

                Text(
                    text = stringResource(R.string.about_app_slogan),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SocialLinksRow(
                onGithubClick = { uriHandler.openUri(GITHUB_URL) },
                onGithubLongClick = { copyToClipboard(context, GITHUB_URL, context.getString(R.string.about_copied_github)) },
                onQqClick = { AppNavigator.joinAnimeTrackGroup(context) },
                onQqLongClick = { copyToClipboard(context, QQ_GROUP_NUMBER, context.getString(R.string.about_copied_qq)) },
                onTgClick = { uriHandler.openUri(TG_URL) },
                onTgLongClick = { copyToClipboard(context, TG_URL, context.getString(R.string.about_copied_tg)) },
                onSponsorClick = { showSponsorDialog = true }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var showStatsDialog by remember { mutableStateOf(false) }

                Text(
                    text = "AnimeTrack",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { showStatsDialog = true }
                )

                if (showStatsDialog) {
                    UsageStatsDialog(onDismiss = { showStatsDialog = false })
                }

                Spacer(modifier = Modifier.size(4.dp))

                Text(
                    text = "${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.size(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (developerMode) 6.dp else 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val buttonModifier = Modifier.weight(1f)
                    val contentPadding = if (developerMode)
                        PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    else
                        PaddingValues(horizontal = 16.dp, vertical = 8.dp)

                    FilledTonalButton(
                        onClick = { updateViewModel.checkForUpdate(force = true) },
                        modifier = buttonModifier,
                        contentPadding = contentPadding
                    ) {
                        Text(text = stringResource(R.string.about_check_update), maxLines = 1)
                    }

                    var changelogLoading by remember { mutableStateOf(false) }
                    var changelogContent by remember { mutableStateOf<String?>(null) }
                    var showChangelogDialog by remember { mutableStateOf(false) }
                    val changelogScope = rememberCoroutineScope()

                    FilledTonalButton(
                        onClick = {
                            if (changelogContent != null) {
                                showChangelogDialog = true
                            } else {
                                changelogLoading = true
                                changelogScope.launch {
                                    val repo = UpdateRepository()
                                    changelogContent = repo.getCurrentVersionChangelog(BuildConfig.VERSION_NAME)
                                    changelogLoading = false
                                    showChangelogDialog = true
                                }
                            }
                        },
                        enabled = !changelogLoading,
                        modifier = buttonModifier,
                        contentPadding = contentPadding
                    ) {
                        if (changelogLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            Text(text = stringResource(R.string.about_changelog), maxLines = 1)
                        }
                    }

                    if (showChangelogDialog) {
                        ChangelogDialog(
                            versionName = BuildConfig.VERSION_NAME,
                            changelog = changelogContent,
                            onDismiss = { showChangelogDialog = false }
                        )
                    }

                    if (developerMode) {
                        FilledTonalButton(
                            onClick = onNavigateDeveloper,
                            modifier = buttonModifier,
                            contentPadding = contentPadding
                        ) {
                            Text(text = stringResource(R.string.about_developer), maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.size(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SocialLinksRow(
    onGithubClick: () -> Unit,
    onGithubLongClick: () -> Unit,
    onQqClick: () -> Unit,
    onQqLongClick: () -> Unit,
    onTgClick: () -> Unit,
    onTgLongClick: () -> Unit,
    onSponsorClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SocialLinkItem(
            icon = { Icon(imageVector = Icons.Default.Code, contentDescription = "GitHub", modifier = Modifier.size(24.dp)) },
            label = "GitHub",
            onClick = onGithubClick,
            onLongClick = onGithubLongClick,
            modifier = Modifier.weight(1f)
        )
        SocialLinkItem(
            icon = { Icon(imageVector = Icons.Default.Forum, contentDescription = stringResource(R.string.about_qq_group), modifier = Modifier.size(24.dp)) },
            label = stringResource(R.string.about_qq_group),
            onClick = onQqClick,
            onLongClick = onQqLongClick,
            modifier = Modifier.weight(1f)
        )
        SocialLinkItem(
            icon = { Icon(imageVector = Icons.Default.Send, contentDescription = stringResource(R.string.about_tg_group), modifier = Modifier.size(24.dp)) },
            label = "Telegram",
            onClick = onTgClick,
            onLongClick = onTgLongClick,
            modifier = Modifier.weight(1f)
        )
        SocialLinkItem(
            icon = { Icon(imageVector = Icons.Default.Favorite, contentDescription = stringResource(R.string.about_sponsor_label), modifier = Modifier.size(24.dp)) },
            label = stringResource(R.string.about_sponsor_label),
            onClick = onSponsorClick,
            onLongClick = onSponsorClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SocialLinkItem(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(SquircleShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = SquircleShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

private fun copyToClipboard(context: Context, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("label", text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

@Composable
private fun ChangelogDialog(
    versionName: String,
    changelog: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = versionName,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (changelog.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.about_no_changelog),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    shape = SquircleShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(markdown = changelog)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SponsorDialog(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var selected by remember { mutableStateOf(0) } // 0=微信, 1=支付宝

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.about_sponsor),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.size(16.dp))

            // 赞赏码图片
            val imageRes = if (selected == 0) R.drawable.sponsor_wechat else R.drawable.sponsor_alipay
            val imageLabel = if (selected == 0) stringResource(R.string.about_wechat_code) else stringResource(R.string.about_alipay_code)

            Image(
                painter = painterResource(id = imageRes),
                contentDescription = imageLabel,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(SquircleShape(16.dp))
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            saveDrawableToGallery(context, imageRes, imageLabel)
                        }
                    ),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = stringResource(R.string.about_save_image_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.size(20.dp))

            // 微信 / 支付宝 选择 - 滑动指示器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(SquircleShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(4.dp)
            ) {
                BoxWithConstraints {
                    val indicatorWidth = maxWidth / 2
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (selected == 0) 0f else 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        label = "indicatorFraction"
                    )

                    // 滑动指示器（底层）
                    Box(
                        modifier = Modifier
                            .offset(x = lerp(0.dp, indicatorWidth, animatedFraction))
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .clip(SquircleShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )

                    // 文字层（上层）
                    Row(modifier = Modifier.fillMaxHeight()) {
                        listOf(stringResource(R.string.about_wechat) to 0, stringResource(R.string.about_alipay) to 1).forEach { (label, index) ->
                            val isSelected = selected == index
                            val interactionSource = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { selected = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 15.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveDrawableToGallery(context: Context, drawableRes: Int, label: String) {
    try {
        val drawable = context.resources.getDrawable(drawableRes, null)
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
            Toast.makeText(context, context.getString(R.string.about_save_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val filename = "AnimeTrack_${label}_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnimeTrack")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: run {
                Toast.makeText(context, context.getString(R.string.about_save_failed), Toast.LENGTH_SHORT).show()
                return
            }

        resolver.openOutputStream(uri)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        Toast.makeText(context, context.getString(R.string.about_saved_to_album), Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.about_save_failed_with_msg, e.message ?: ""), Toast.LENGTH_SHORT).show()
    }
}