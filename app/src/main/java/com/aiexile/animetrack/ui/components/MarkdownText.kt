package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int, val checked: Boolean? = null) : MdBlock()
    data class CodeBlock(val code: String) : MdBlock()
    data class Blockquote(val text: String) : MdBlock()
    data class Image(val url: String, val alt: String) : MdBlock()
    data object HorizontalRule : MdBlock()
    data object BlankLine : MdBlock()
}

fun parseMarkdown(markdown: String): List<MdBlock> {
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

        // Horizontal rule: ---, ***, ___ (3+ chars)
        if (Regex("^[-*_]{3,}$").matches(line.trim())) {
            blocks.add(MdBlock.HorizontalRule)
            orderedIndex = 0
            i++
            continue
        }

        // Blockquote: > text
        val blockquoteMatch = Regex("^>\\s?(.*)$").matchEntire(line)
        if (blockquoteMatch != null) {
            blocks.add(MdBlock.Blockquote(blockquoteMatch.groupValues[1].trim()))
            orderedIndex = 0
            i++
            continue
        }

        // Task list: - [x] text or - [ ] text
        val taskMatch = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.+)$").matchEntire(line.trimStart())
        if (taskMatch != null) {
            val checked = taskMatch.groupValues[1].lowercase() == "x"
            blocks.add(MdBlock.ListItem(taskMatch.groupValues[2], ordered = false, 0, checked = checked))
            orderedIndex = 0
            i++
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

        // Image: ![alt](url)
        val imageMatch = Regex("^!\\[(.*?)]\\((.+?)\\)$").matchEntire(line.trim())
        if (imageMatch != null) {
            blocks.add(MdBlock.Image(imageMatch.groupValues[2].trim(), imageMatch.groupValues[1].trim()))
            orderedIndex = 0
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
fun rememberMarkdownBlocks(markdown: String): List<MdBlock> {
    return remember(markdown) {
        parseMarkdown(markdown)
    }
}

fun AnnotatedString.Builder.appendInlineMarkdown(text: String, colorScheme: androidx.compose.material3.ColorScheme) {
    val combinedRegex = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|~~(.+?)~~|`(.+?)`|\[(.+?)]\((.+?)\)""")

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
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[2])
                }
            }
            match.groupValues[3].isNotEmpty() -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = colorScheme.onSurfaceVariant)) {
                    append(match.groupValues[3])
                }
            }
            match.groupValues[4].isNotEmpty() -> {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceContainer,
                    color = colorScheme.primary
                )) {
                    append(" ${match.groupValues[4]} ")
                }
            }
            match.groupValues[5].isNotEmpty() -> {
                withStyle(SpanStyle(
                    color = colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )) {
                    append(match.groupValues[5])
                }
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
fun MarkdownText(markdown: String) {
    val colorScheme = MaterialTheme.colorScheme
    val blocks = rememberMarkdownBlocks(markdown)

    Column(modifier = Modifier.fillMaxWidth()) {
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
                                if (block.checked != null) {
                                    append(if (block.checked) "☑ " else "☐ ")
                                } else {
                                    append(if (block.ordered) "${block.index}. " else "• ")
                                }
                            }
                            appendInlineMarkdown(block.text, colorScheme)
                        },
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                is MdBlock.Blockquote -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        color = colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                appendInlineMarkdown(block.text, colorScheme)
                            },
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MdBlock.Image -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        AsyncImage(
                            model = block.url,
                            contentDescription = block.alt.ifBlank { null },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MdBlock.HorizontalRule -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp),
                        color = colorScheme.outlineVariant
                    ) {}
                    Spacer(modifier = Modifier.height(4.dp))
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
}
