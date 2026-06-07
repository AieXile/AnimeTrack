package com.aiexile.animetrack.ui.update

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun UpdateDialog(viewModel: UpdateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val info = uiState.updateInfo

    LifecycleResumeEffect(Unit) {
        viewModel.checkPendingInstall(context)
        onPauseOrDispose { }
    }

    LaunchedEffect(info?.versionName) {
        if (info != null && !uiState.isDownloading && !uiState.downloadComplete && !uiState.isSimulated) {
            viewModel.tryRecoverDownload(context)
        }
    }

    if (uiState.isUpToDate) {
        UpToDateDialog(onDismiss = { viewModel.dismissUpdate() })
        return
    }

    if (info == null) return

    when {
        uiState.error != null -> {
            ErrorDialog(
                message = uiState.error ?: "未知错误",
                onDismiss = {
                    viewModel.clearError()
                    viewModel.dismissUpdate()
                }
            )
        }
        else -> {
            UpdateAvailableDialog(
                currentVersion = uiState.currentVersion,
                newVersion = info.versionName,
                changelog = info.changelog,
                isDownloading = uiState.isDownloading,
                downloadProgress = uiState.downloadProgress,
                downloadComplete = uiState.downloadComplete,
                onUpdate = { if (uiState.isSimulated) viewModel.startSimulatedDownload() else viewModel.startDownload(context) },
                onInstall = { viewModel.installApk(context) },
                onDismiss = { viewModel.dismissUpdate() },
                onSkip = { if (uiState.isSimulated) viewModel.dismissUpdate() else viewModel.skipVersion() }
            )
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    currentVersion: String,
    newVersion: String,
    changelog: String,
    isDownloading: Boolean,
    downloadProgress: Int,
    downloadComplete: Boolean,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        title = {
            Text(
                text = "发现新版本",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = currentVersion,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "→",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = newVersion,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                    ) {
                        if (changelog.isBlank()) {
                            Text(
                                text = "暂无更新日志",
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            MarkdownText(markdown = changelog)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (!isDownloading && !downloadComplete) {
                    TextButton(onClick = onSkip) {
                        Text("跳过该版本")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                }
                if (downloadComplete) {
                    TextButton(onClick = onDismiss) {
                        Text("稍后")
                    }
                    Button(onClick = onInstall) {
                        Text("安装")
                    }
                } else if (isDownloading) {
                    TextButton(onClick = onDismiss, enabled = false) {
                        Text("$downloadProgress%")
                    }
                } else {
                    Button(onClick = onUpdate) {
                        Text("立即更新")
                    }
                }
            }
        }
    )
}

@Composable
private fun MarkdownText(markdown: String) {
    val colorScheme = MaterialTheme.colorScheme
    val blocks = rememberMarkdownBlocks(markdown)

    blocks.forEach { block ->
        when (block) {
            is MdBlock.Heading -> {
                val fontSize = when (block.level) {
                    1 -> 18.sp
                    2 -> 16.sp
                    else -> 14.sp
                }
                val weight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold
                Text(
                    text = block.text,
                    fontSize = fontSize,
                    fontWeight = weight,
                    lineHeight = 22.sp,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            is MdBlock.CodeBlock -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = colorScheme.surfaceContainer
                ) {
                    Text(
                        text = block.code,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            is MdBlock.ListItem -> {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                            append(if (block.ordered) "${block.index}. " else "• ")
                        }
                        appendInlineMarkdown(block.text, colorScheme)
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
            is MdBlock.Paragraph -> {
                Text(
                    text = buildAnnotatedString {
                        appendInlineMarkdown(block.text, colorScheme)
                    },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            is MdBlock.BlankLine -> {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String, colorScheme: androidx.compose.material3.ColorScheme) {
    val combinedRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`""")

    var lastIndex = 0
    combinedRegex.findAll(text).forEach { match ->
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        when {
            match.groupValues[1].isNotEmpty() -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colorScheme.onSurface)) {
                    append(match.groupValues[1])
                }
            }
            match.groupValues[2].isNotEmpty() -> {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(match.groupValues[2])
                }
            }
            match.groupValues[3].isNotEmpty() -> {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceContainer,
                    color = colorScheme.primary
                )) {
                    append(" ${match.groupValues[3]} ")
                }
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MdBlock()
    data class CodeBlock(val code: String) : MdBlock()
    data object BlankLine : MdBlock()
}

@Composable
private fun rememberMarkdownBlocks(markdown: String): List<MdBlock> {
    return androidx.compose.runtime.remember(markdown) {
        parseMarkdown(markdown)
    }
}

private fun parseMarkdown(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.lines()
    var i = 0
    var orderedIndex = 0

    while (i < lines.size) {
        val line = lines[i]

        if (line.isBlank()) {
            blocks.add(MdBlock.BlankLine)
            orderedIndex = 0
            i++
            continue
        }

        val headingMatch = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            blocks.add(MdBlock.Heading(level, headingMatch.groupValues[2].trim()))
            orderedIndex = 0
            i++
            continue
        }

        if (line.trimStart().startsWith("```")) {
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n")))
            i++
            orderedIndex = 0
            continue
        }

        val unorderedMatch = Regex("^[-*+]\\s+(.+)$").matchEntire(line.trimStart())
        if (unorderedMatch != null) {
            blocks.add(MdBlock.ListItem(unorderedMatch.groupValues[1], ordered = false, 0))
            orderedIndex = 0
            i++
            continue
        }

        val orderedMatch = Regex("^\\d+\\.\\s+(.+)$").matchEntire(line.trimStart())
        if (orderedMatch != null) {
            orderedIndex++
            blocks.add(MdBlock.ListItem(orderedMatch.groupValues[1], ordered = true, orderedIndex))
            i++
            continue
        }

        blocks.add(MdBlock.Paragraph(line.trim()))
        orderedIndex = 0
        i++
    }

    return blocks
}

@Composable
private fun UpToDateDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "已是最新版本",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("当前已是最新版本，无需更新。")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@Composable
private fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "更新失败",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
