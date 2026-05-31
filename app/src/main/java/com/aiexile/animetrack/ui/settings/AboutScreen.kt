package com.aiexile.animetrack.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.remote.UpdateRepository
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.ui.update.UpdateDialog
import com.aiexile.animetrack.ui.update.UpdateViewModel
import com.aiexile.animetrack.util.AppNavigator

private const val GITHUB_URL = "https://github.com/AieXile/AnimeTrack"
private const val QQ_GROUP_NUMBER = "951059178"
private const val TG_URL = "https://t.me/AnimeTrackovo"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.my_avatar),
                        contentDescription = "头像",
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
                    text = "一款简洁的番剧追踪应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SocialLinksRow(
                onGithubClick = { uriHandler.openUri(GITHUB_URL) },
                onGithubLongClick = { copyToClipboard(context, GITHUB_URL, "已复制 GitHub 链接") },
                onQqClick = { AppNavigator.joinAnimeTrackGroup(context) },
                onQqLongClick = { copyToClipboard(context, QQ_GROUP_NUMBER, "已复制 QQ 群号") },
                onTgClick = { uriHandler.openUri(TG_URL) },
                onTgLongClick = { copyToClipboard(context, TG_URL, "已复制 Telegram 链接") }
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
                Text(
                    text = "AnimeTrack",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.size(4.dp))

                Text(
                    text = "v${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.size(16.dp))

                FilledTonalButton(
                    onClick = { updateViewModel.checkForUpdate(force = true) }
                ) {
                    Text(text = "检查更新")
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
    onTgLongClick: () -> Unit
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
            icon = { Icon(imageVector = Icons.Default.Forum, contentDescription = "QQ群", modifier = Modifier.size(24.dp)) },
            label = "QQ群",
            onClick = onQqClick,
            onLongClick = onQqLongClick,
            modifier = Modifier.weight(1f)
        )
        SocialLinkItem(
            icon = { Icon(imageVector = Icons.Default.Send, contentDescription = "TG群", modifier = Modifier.size(24.dp)) },
            label = "Telegram",
            onClick = onTgClick,
            onLongClick = onTgLongClick,
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
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(16.dp),
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