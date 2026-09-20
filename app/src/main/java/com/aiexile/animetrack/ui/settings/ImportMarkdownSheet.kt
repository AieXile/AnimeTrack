package com.aiexile.animetrack.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.aiexile.animetrack.ui.components.SquircleShape
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiexile.animetrack.R
import kotlinx.coroutines.launch

// 导入格式示例：展示给用户参考，也可复制给 AI 让其按此格式整理追番记录
// 所有条目均带上「已看/总集数」，便于 AI 生成完整进度信息
private val IMPORT_EXAMPLE_MARKDOWN = """
Now
孤独摇滚！ 8/12
约会大作战IV 5/12

Want
异度侵入 0/13
CLANNAD 0/22

Already

2026.01.20
彻夜之歌 第二季 13/13 (依旧夯)

2026.01.13
游戏人生 12/12 (还行吧)

Dropped
某番剧名称 3/24
""".trimIndent()

/**
 * Markdown 导入弹层：内部包含「格式引导」与「粘贴文本」两个页面，
 * 通过滑动过渡在同一弹层内切换，避免关闭再打开造成的割裂感。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportMarkdownSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    onImport: (String) -> Unit
) {
    var isPasteMode by remember { mutableStateOf(false) }
    // 提升到弹层级别：从粘贴页返回引导页再进入时，已输入内容不丢失
    var pasteText by remember { mutableStateOf("") }
    // 记录引导页实际高度，让粘贴页等高显示，避免切换时弹层高度收缩
    var guideContentHeightPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = {
            // 粘贴页中返回（手势/返回键）先回到引导页，再次返回才关闭弹层
            if (isPasteMode) isPasteMode = false else onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(SquircleShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        AnimatedContent(
            targetState = isPasteMode,
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                (slideInHorizontally(tween(220)) { direction * it / 3 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -direction * it / 3 } + fadeOut(tween(220)))
            },
            label = "importSheetMode"
        ) { pasteMode ->
            if (pasteMode) {
                PasteImportContent(
                    modifier = if (guideContentHeightPx > 0) {
                        Modifier.height(with(density) { guideContentHeightPx.toDp() })
                    } else {
                        Modifier
                    },
                    text = pasteText,
                    onTextChange = { pasteText = it },
                    onBack = { isPasteMode = false },
                    onSubmit = {
                        scope.launch {
                            sheetState.hide()
                            onImport(pasteText)
                        }
                    }
                )
            } else {
                ImportGuideContent(
                    modifier = Modifier.onSizeChanged { size ->
                        // 只记录最大值，避免过渡动画中的中间尺寸覆盖真实高度
                        if (size.height > guideContentHeightPx) {
                            guideContentHeightPx = size.height
                        }
                    },
                    onSelectFile = {
                        scope.launch {
                            sheetState.hide()
                            onSelectFile()
                        }
                    },
                    onPasteImport = { isPasteMode = true }
                )
            }
        }
    }
}

@Composable
private fun ImportGuideContent(
    modifier: Modifier = Modifier,
    onSelectFile: () -> Unit,
    onPasteImport: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.data_manage_import_guide_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.data_manage_import_guide_format_hint),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SquircleShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Text(
                text = IMPORT_EXAMPLE_MARKDOWN,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            TextButton(
                onClick = {
                    copyToClipboard(
                        context,
                        IMPORT_EXAMPLE_MARKDOWN,
                        context.getString(R.string.data_manage_import_example_copied)
                    )
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.CONTENT_COPY),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.data_manage_import_example_copy),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.data_manage_import_ai_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onSelectFile,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = SquircleShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.data_manage_select_file),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            OutlinedButton(
                onClick = onPasteImport,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = SquircleShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.data_manage_paste_import_entry),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PasteImportContent(
    modifier: Modifier = Modifier,
    text: String,
    onTextChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = rememberAppIconPainter(AppIcon.ARROW_BACK),
                    contentDescription = stringResource(R.string.common_back),
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(R.string.data_manage_paste_import_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 键盘弹出时隐藏说明文案，把空间留给输入框
        if (!imeVisible) {
            Text(
                text = stringResource(R.string.data_manage_paste_import_hint),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = {
                Text(
                    text = stringResource(R.string.data_manage_paste_import_placeholder),
                    fontSize = 13.sp
                )
            },
            trailingIcon = {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                    if (clipText.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.data_manage_paste_clipboard_empty),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onTextChange(clipText)
                    }
                }) {
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.CONTENT_PASTE),
                        contentDescription = stringResource(R.string.data_manage_paste_from_clipboard),
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            shape = SquircleShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onSubmit,
            enabled = text.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = SquircleShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = stringResource(R.string.data_manage_paste_import_confirm),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String, toastMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("markdown_example", text))
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
