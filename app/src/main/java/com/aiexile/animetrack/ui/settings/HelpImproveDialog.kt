package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.ui.components.AppSwitch
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.components.dialogBlurBehind
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import kotlinx.coroutines.launch

/**
 * 首启「帮助改进应用」弹窗：在隐私政策同意后展示一次，让用户选择是否开启崩溃追踪。
 * 开关切换即写入（与设置页行为一致），「确定」仅关闭弹窗并标记已展示（不再弹出）；
 * 后续调整入口在「设置 → 功能 → 帮助改进应用」。
 */
@Composable
fun HelpImproveDialog(
    settingsRepository: SettingsRepository,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val crashReportEnabled by settingsRepository.crashReportEnabled.collectAsState(true)

    Dialog(
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        ),
        onDismissRequest = { /* 必须点「确定」关闭 */ }
    ) {
        Surface(
            shape = SquircleShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth().dialogBlurBehind()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部 bug 图标（primary 着色，与图中「帮助改进应用」引导样式对应）
                Icon(
                    painter = rememberAppIconPainter(AppIcon.BUG_REPORT),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.help_improve_dialog_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.help_improve_dialog_message),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(R.string.help_improve_dialog_note),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 崩溃追踪开关行：切换即写入（与设置页「功能」中的开关共享同一 DataStore key）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.help_improve_crash_toggle),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.help_improve_crash_toggle_desc),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    AppSwitch(
                        checked = crashReportEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsRepository.setCrashReportEnabled(enabled) }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 确定按钮：右下角文字按钮（与弹窗引导样式对应）
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.help_improve_confirm),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
