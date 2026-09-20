package com.aiexile.animetrack.ui.settings

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.components.SquircleShape

/**
 * 首启隐私政策同意弹窗（合规：未同意前不得收集个人信息 / 申请权限 / 初始化上报组件）
 *
 * 不可点击外部或返回键关闭，用户必须显式选择：
 * - 同意并继续 → [onAccept]（记录同意状态并补初始化 JPush、崩溃上报等组件）
 * - 不同意并退出 → 二次确认后退出应用
 * - 查看完整《隐私政策》→ 全屏切换到 [PrivacyPolicyScreen]，返回键/返回按钮回到弹窗
 */
@Composable
fun PrivacyConsentDialog(
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    var showFullPolicy by remember { mutableStateOf(false) }
    var showDeclineConfirm by remember { mutableStateOf(false) }

    // 查看完整政策：全屏覆盖弹窗（复用 PrivacyPolicyScreen，返回回到弹窗）
    if (showFullPolicy) {
        PrivacyPolicyScreen(onBack = { showFullPolicy = false })
        return
    }

    // 「不同意并退出」二次确认（规范要求提供退出途径，但不强制挽留）
    if (showDeclineConfirm) {
        AlertDialog(
            onDismissRequest = { showDeclineConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.privacy_consent_decline_title),
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.privacy_consent_decline_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    (context as? Activity)?.finishAffinity()
                }) {
                    Text(text = stringResource(R.string.privacy_consent_decline_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineConfirm = false }) {
                    Text(text = stringResource(R.string.privacy_consent_decline_cancel))
                }
            }
        )
    }

    Dialog(
        properties = DialogProperties(
            dismissOnClickOutside = false,
            dismissOnBackPress = false
        ),
        onDismissRequest = { /* 必须显式选择，不允许关闭 */ }
    ) {
        Surface(
            shape = SquircleShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 应用图标（adaptive icon 不支持 painterResource，手动组合背景色 + 前景矢量）
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(SquircleShape(18.dp))
                        .background(colorResource(R.color.ic_launcher_background)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.privacy_consent_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 摘要：《隐私政策》内嵌链接，点击查看完整政策
                Text(
                    text = buildConsentMessage(
                        onOpenPolicy = { showFullPolicy = true }
                    ),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 三条要点
                ConsentPoint(text = stringResource(R.string.privacy_consent_point_local))
                ConsentPoint(text = stringResource(R.string.privacy_consent_point_account))
                ConsentPoint(text = stringResource(R.string.privacy_consent_point_upload))

                Spacer(modifier = Modifier.height(8.dp))

                // 查看完整政策
                TextButton(onClick = { showFullPolicy = true }) {
                    Text(text = stringResource(R.string.privacy_consent_view_full))
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth(),
                    shape = SquircleShape(16.dp)
                ) {
                    Text(text = stringResource(R.string.privacy_consent_accept))
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = { showDeclineConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.privacy_consent_decline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 摘要正文：前缀 + 《隐私政策》链接 + 后缀（三语均由 string 资源拼接） */
@Composable
private fun buildConsentMessage(onOpenPolicy: () -> Unit) = buildAnnotatedString {
    append(stringResource(R.string.privacy_consent_message_prefix))
    withLink(
        LinkAnnotation.Clickable(
            tag = "privacy_policy",
            styles = TextLinkStyles(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline
                )
            ),
            linkInteractionListener = { onOpenPolicy() }
        )
    ) {
        append(stringResource(R.string.privacy_policy_title))
    }
    append(stringResource(R.string.privacy_consent_message_suffix))
}

/** 单条要点：圆点 + 文本 */
@Composable
private fun ConsentPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(SquircleShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
