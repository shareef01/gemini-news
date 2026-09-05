package com.aus.gemini01.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class BlockKind {
    H1, H2, H3, BULLET, NUMBERED, QUOTE, DIVIDER, SENTIMENT, BODY
}

internal data class Segment(val text: String, val bold: Boolean)

internal data class Block(
    val kind: BlockKind,
    val segments: List<Segment>,
    val extra: String = "" // For sentiment value, number prefix, etc.
)

/**
 * Rich Markdown renderer for AI summaries, reader mode, and trends.
 * Supports headings, numbered lists, bullet lists, sentiment badges, quotes, dividers, and bold spans.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    fontScale: Float = 1f
) {
    val blocks = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            val annotated = buildAnnotatedString {
                block.segments.forEach { segment ->
                    if (segment.bold) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment.text) }
                    } else {
                        append(segment.text)
                    }
                }
            }

            when (block.kind) {
                BlockKind.H1 -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = (22 * fontScale).sp,
                            lineHeight = (28 * fontScale).sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                BlockKind.H2 -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp, 16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = (16 * fontScale).sp,
                                    lineHeight = (22 * fontScale).sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                BlockKind.H3 -> {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = (15 * fontScale).sp,
                            lineHeight = (20 * fontScale).sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                BlockKind.SENTIMENT -> {
                    val sentiment = block.extra.uppercase()
                    val (badgeBg, badgeFg, icon) = when {
                        sentiment.contains("POS") -> Triple(
                            Color(0xFFE8F5E9),
                            Color(0xFF1B5E20),
                            Icons.Default.SentimentSatisfied
                        )
                        sentiment.contains("NEG") -> Triple(
                            Color(0xFFFFEBEE),
                            Color(0xFFB71C1C),
                            Icons.Default.SentimentDissatisfied
                        )
                        else -> Triple(
                            Color(0xFFEDE7F6),
                            Color(0xFF4A148C),
                            Icons.Default.SentimentNeutral
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = badgeBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = badgeFg,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AI-assessed tone: ${block.extra}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = badgeFg
                                )
                                if (annotated.isNotEmpty()) {
                                    Text(
                                        text = annotated,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = (14 * fontScale).sp,
                                            lineHeight = (19 * fontScale).sp
                                        ),
                                        color = badgeFg.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }

                BlockKind.BULLET -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (15 * fontScale).sp,
                                lineHeight = (22 * fontScale).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                BlockKind.NUMBERED -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 2.dp, end = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = block.extra,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (15 * fontScale).sp,
                                lineHeight = (22 * fontScale).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                BlockKind.QUOTE -> {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (15 * fontScale).sp,
                                    lineHeight = (22 * fontScale).sp,
                                    fontStyle = FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                BlockKind.DIVIDER -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                BlockKind.BODY -> {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (15 * fontScale).sp,
                            lineHeight = (23 * fontScale).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

internal fun parseMarkdown(text: String): List<Block> {
    val lines = text.lines()
    val blocks = mutableListOf<Block>()

    var paragraphBuffer = mutableListOf<String>()
    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            val content = paragraphBuffer.joinToString(" ").trim()
            if (content.isNotEmpty()) {
                blocks.add(Block(BlockKind.BODY, parseInline(content)))
            }
            paragraphBuffer = mutableListOf()
        }
    }

    val numberedHeaderRegex = Regex("""^(\d+)[\.\)]\s+\*\*(.+?)\*\*[:\s]*(.*)""")
    val numberedListRegex = Regex("""^(\d+)[\.\)]\s+(.*)""")
    val sentimentRegex = Regex("""^(?:-|\*|•)?\s*(?:\*\*)?Sentiment(?:\*\*)?[:\s]+(\w+)(?:[\s—–-]+(.*))?""", RegexOption.IGNORE_CASE)

    lines.forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> flushParagraph()

            line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.DIVIDER, emptyList()))
            }

            line.startsWith("### ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.H3, parseInline(line.removePrefix("### ").trim())))
            }

            line.startsWith("## ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.H2, parseInline(line.removePrefix("## ").trim())))
            }

            line.startsWith("# ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.H1, parseInline(line.removePrefix("# ").trim())))
            }

            // Standalone bold header: e.g. **Key Takeaways:** or **1. Key Takeaways**
            line.startsWith("**") && line.endsWith("**") && line.length > 4 && !line.substring(2, line.length - 2).contains("**") -> {
                flushParagraph()
                val headerText = line.removePrefix("**").removeSuffix("**").removeSuffix(":").trim()
                blocks.add(Block(BlockKind.H2, parseInline(headerText)))
            }

            // Numbered section header: 1. **Key Takeaways**: ...
            numberedHeaderRegex.matches(line) -> {
                flushParagraph()
                val match = numberedHeaderRegex.find(line)
                if (match != null) {
                    val title = match.groupValues[2].trim().removeSuffix(":")
                    val remainder = match.groupValues[3].trim()
                    blocks.add(Block(BlockKind.H2, parseInline(title)))
                    if (remainder.isNotEmpty()) {
                        blocks.add(Block(BlockKind.BODY, parseInline(remainder)))
                    }
                }
            }

            // Sentiment line: Sentiment: Positive - Description
            sentimentRegex.matches(line) -> {
                flushParagraph()
                val match = sentimentRegex.find(line)
                if (match != null) {
                    val sentiment = match.groupValues[1].trim()
                    val explanation = match.groupValues[2].trim()
                    blocks.add(Block(BlockKind.SENTIMENT, parseInline(explanation), extra = sentiment))
                }
            }

            // Numbered item in list: 1. Point one
            numberedListRegex.matches(line) -> {
                flushParagraph()
                val match = numberedListRegex.find(line)
                if (match != null) {
                    val number = match.groupValues[1]
                    val content = match.groupValues[2].trim()
                    blocks.add(Block(BlockKind.NUMBERED, parseInline(content), extra = number))
                }
            }

            // Bullet points
            line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                flushParagraph()
                val bulletContent = line.substring(2).trim()
                blocks.add(Block(BlockKind.BULLET, parseInline(bulletContent)))
            }

            // Blockquotes
            line.startsWith("> ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.QUOTE, parseInline(line.removePrefix("> ").trim())))
            }

            else -> paragraphBuffer.add(line)
        }
    }
    flushParagraph()
    return blocks
}

private fun parseInline(text: String): List<Segment> {
    val parts = text.split("**")
    return parts.mapIndexed { index, part ->
        Segment(part, bold = index % 2 == 1)
    }
}
