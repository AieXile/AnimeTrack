package com.aiexile.animetrack.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import com.aiexile.animetrack.ui.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import com.aiexile.animetrack.ui.announcement.AnnouncementDialog
import com.aiexile.animetrack.ui.announcement.AnnouncementViewModel
import com.aiexile.animetrack.ui.update.UpdateDialog
import com.aiexile.animetrack.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {}
) {
    val settingsRepository = remember { AppContainer.getSettingsRepository() }
    val animeRepository = remember { AppContainer.getAnimeRepository() }
    val developerMode by settingsRepository.developerMode.collectAsState(initial = true)
    val shareButtonEnabled by settingsRepository.shareButtonEnabled.collectAsState(initial = false)
    val updateNotificationVisible by settingsRepository.updateNotificationVisible.collectAsState(initial = false)
    val playerHubVisible by settingsRepository.playerHubVisible.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val updateViewModel: UpdateViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val updateRepository = UpdateRepository()
                val settingsRepo = AppContainer.getSettingsRepository()
                return UpdateViewModel(updateRepository, settingsRepo) as T
            }
        }
    )
    val uiState by updateViewModel.uiState.collectAsState()

    val announcementViewModel: AnnouncementViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val settingsRepo = AppContainer.getSettingsRepository()
                val userAuthManager = AppContainer.getUserAuthManager()
                return AnnouncementViewModel(settingsRepo, userAuthManager) as T
            }
        }
    )

    var debugCardCount by remember { mutableIntStateOf(5) }

    // 更新弹窗后组合，层级高于公告弹窗（与 HomeScreen 保持一致）
    AnnouncementDialog(viewModel = announcementViewModel)
    UpdateDialog(viewModel = updateViewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.developer_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.developer_mode),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.developer_mode_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppSwitch(
                    checked = developerMode,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setDeveloperMode(enabled)
                            if (!enabled) {
                                Toast.makeText(context, context.getString(R.string.developer_mode_disabled), Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.developer_share_button),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.developer_share_button_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppSwitch(
                    checked = shareButtonEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setShareButtonEnabled(enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.developer_update_notification),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.developer_update_notification_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppSwitch(
                    checked = updateNotificationVisible,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setUpdateNotificationVisible(enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.developer_player_hub_entry),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.developer_player_hub_entry_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppSwitch(
                    checked = playerHubVisible,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settingsRepository.setPlayerHubVisible(enabled)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.size(24.dp))

            AdvancedBlurTuningSection(
                settingsRepository = settingsRepository,
                context = context
            )

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.developer_test_notification),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.developer_test_notification_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = {
                    com.aiexile.animetrack.data.notification.UpdateNotificationManager
                        .triggerTestNotification(context)
                    Toast.makeText(context, context.getString(R.string.developer_test_notification_toast), Toast.LENGTH_SHORT).show()
                },
                shape = SquircleShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = stringResource(R.string.developer_send_test_notification))
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.developer_debug_cards),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.developer_debug_cards_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { if (debugCardCount > 1) debugCardCount-- }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.developer_decrease),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "$debugCardCount",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                IconButton(
                    onClick = { if (debugCardCount < 50) debugCardCount++ }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.developer_increase),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val statuses = AnimeStatus.entries
                            val prefix = context.getString(R.string.developer_debug_anime_prefix)
                            repeat(debugCardCount) { i ->
                                animeRepository.insertAnime(
                                    Anime(
                                        title = "$prefix ${i + 1}",
                                        totalEpisodes = 12,
                                        watchedEpisodes = (0..12).random(),
                                        status = statuses.random(),
                                        rating = (0..10).random().toFloat(),
                                        notes = ""
                                    )
                                )
                            }
                            Toast.makeText(context, context.getString(R.string.developer_added_debug_cards, debugCardCount), Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = SquircleShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(text = stringResource(R.string.common_add))
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val allAnime = animeRepository.getAllAnimes().first()
                            val prefix = context.getString(R.string.developer_debug_anime_prefix)
                            val debugAnime = allAnime.filter { it.title.startsWith(prefix) }
                            debugAnime.forEach { animeRepository.deleteAnime(it) }
                            val count = debugAnime.size
                            Toast.makeText(context, if (count > 0) context.getString(R.string.developer_deleted_debug_cards, count) else context.getString(R.string.developer_no_debug_cards), Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = SquircleShape(12.dp)
                ) {
                    Text(text = stringResource(R.string.common_clear))
                }
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.developer_simulate_update),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.developer_simulate_update_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = { updateViewModel.simulateUpdate() },
                shape = SquircleShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !uiState.isDownloading && uiState.updateInfo == null
            ) {
                Text(text = stringResource(R.string.developer_trigger_simulate_update))
            }

            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.developer_announcement),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.developer_announcement_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = { announcementViewModel.open() },
                shape = SquircleShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Campaign,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.developer_open_announcement))
            }


            Spacer(modifier = Modifier.size(24.dp))

            Text(
                text = stringResource(R.string.developer_onboarding),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.developer_onboarding_desc),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.size(12.dp))

            Button(
                onClick = onNavigateToOnboarding,
                shape = SquircleShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.RocketLaunch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.developer_open_onboarding))
            }

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

/**
 * 开发者选项中的「高级模糊（毛玻璃）」参数调节区。
 *
 * 高级模糊开关与导航自定义中的悬浮胶囊高级模糊开关同步；
 * 四个滑杆（模糊半径 / 底色不透明度 / 着色不透明度 / 噪点强度）
 * 同时作用于悬浮胶囊与悬浮按钮。
 *
 * 滑杆拖动期间仅更新本地状态，松手后才写入 DataStore，避免拖拽时频繁持久化。
 */
@Composable
private fun AdvancedBlurTuningSection(
    settingsRepository: SettingsRepository,
    context: Context
) {
    val scope = rememberCoroutineScope()
    val capsuleAdvancedBlur by settingsRepository.capsuleAdvancedBlurEnabled.collectAsState(false)
    val blurRadius by settingsRepository.advancedBlurRadius.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_RADIUS)
    val backgroundAlpha by settingsRepository.advancedBlurBackgroundAlpha.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_BACKGROUND_ALPHA)
    val tintAlpha by settingsRepository.advancedBlurTintAlpha.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_TINT_ALPHA)
    val noise by settingsRepository.advancedBlurNoise.collectAsState(SettingsRepository.DEFAULT_ADVANCED_BLUR_NOISE)

    // 本地拖拽状态：松手前只改本地值
    var localRadius by remember { mutableFloatStateOf(blurRadius) }
    var localBackgroundAlpha by remember { mutableFloatStateOf(backgroundAlpha) }
    var localTintAlpha by remember { mutableFloatStateOf(tintAlpha) }
    var localNoise by remember { mutableFloatStateOf(noise) }

    // 持久化值变化（含恢复默认）时同步回滑杆
    LaunchedEffect(blurRadius) { localRadius = blurRadius }
    LaunchedEffect(backgroundAlpha) { localBackgroundAlpha = backgroundAlpha }
    LaunchedEffect(tintAlpha) { localTintAlpha = tintAlpha }
    LaunchedEffect(noise) { localNoise = noise }

    Column {
        Text(
            text = stringResource(R.string.developer_advanced_blur),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.developer_advanced_blur_desc),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.size(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_custom_advanced_blur),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.developer_blur_switch_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppSwitch(
                checked = capsuleAdvancedBlur,
                onCheckedChange = { enabled ->
                    scope.launch { settingsRepository.setCapsuleAdvancedBlurEnabled(enabled) }
                }
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeveloperBlurSlider(
                title = stringResource(R.string.developer_blur_radius),
                value = localRadius,
                valueRange = 0f..50f,
                valueLabel = { it2 -> it2.roundToInt().toString() + " dp" },
                onValueChange = { localRadius = it },
                onValueChangeFinished = { scope.launch { settingsRepository.setAdvancedBlurRadius(localRadius) } }
            )
            DeveloperBlurSlider(
                title = stringResource(R.string.developer_blur_background_alpha),
                value = localBackgroundAlpha,
                valueRange = 0f..1f,
                valueLabel = { it2 -> ((it2 * 100).roundToInt()).toString() + "%" },
                onValueChange = { localBackgroundAlpha = it },
                onValueChangeFinished = { scope.launch { settingsRepository.setAdvancedBlurBackgroundAlpha(localBackgroundAlpha) } }
            )
            DeveloperBlurSlider(
                title = stringResource(R.string.developer_blur_tint_alpha),
                value = localTintAlpha,
                valueRange = 0f..1f,
                valueLabel = { it2 -> ((it2 * 100).roundToInt()).toString() + "%" },
                onValueChange = { localTintAlpha = it },
                onValueChangeFinished = { scope.launch { settingsRepository.setAdvancedBlurTintAlpha(localTintAlpha) } }
            )
            DeveloperBlurSlider(
                title = stringResource(R.string.developer_blur_noise),
                value = localNoise,
                valueRange = 0f..1f,
                valueLabel = { it2 -> ((it2 * 100).roundToInt()).toString() + "%" },
                onValueChange = { localNoise = it },
                onValueChangeFinished = { scope.launch { settingsRepository.setAdvancedBlurNoise(localNoise) } }
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    settingsRepository.setAdvancedBlurRadius(SettingsRepository.DEFAULT_ADVANCED_BLUR_RADIUS)
                    settingsRepository.setAdvancedBlurBackgroundAlpha(SettingsRepository.DEFAULT_ADVANCED_BLUR_BACKGROUND_ALPHA)
                    settingsRepository.setAdvancedBlurTintAlpha(SettingsRepository.DEFAULT_ADVANCED_BLUR_TINT_ALPHA)
                    settingsRepository.setAdvancedBlurNoise(SettingsRepository.DEFAULT_ADVANCED_BLUR_NOISE)
                }
                Toast.makeText(context, context.getString(R.string.developer_blur_reset_toast), Toast.LENGTH_SHORT).show()
            },
            shape = SquircleShape(12.dp)
        ) {
            Text(text = stringResource(R.string.developer_blur_reset))
        }
    }
}

/** 单个模糊参数滑杆：标题 + 当前值 + 滑杆 */
@Composable
private fun DeveloperBlurSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valueLabel(value),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange
        )
    }
}
