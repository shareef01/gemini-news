package com.aus.gemini01.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class BlockKind { H1, H2, BULLET, BODY }

internal data class Segment(val text: String, val bold: Boolean)

internal data class Block(val kind: BlockKind, val segments: List<Segment>)

/**
 * Minimal Markdown renderer for AI-generated content: supports `#` / `##` headings,
 * `-` / `*` / `•` bullets, paragraphs and `**bold**` spans. Unknown syntax renders as
 * plain text, so nothing is ever lost.
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                BlockKind.H1 -> Text(
                    text = annotated,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = (22 * fontScale).sp,
                        lineHeight = (28 * fontScale).sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                BlockKind.H2 -> Text(
                    text = annotated,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (17 * fontScale).sp,
                        lineHeight = (23 * fontScale).sp
                    ),
                    fontWeight = FontWeight.Bold
                )

                BlockKind.BULLET -> Text(
                    text = buildAnnotatedString {
                        append("•  ")
                        append(annotated)
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (16 * fontScale).sp,
                        lineHeight = (24 * fontScale).sp
                    ),
                    modifier = Modifier.padding(start = 4.dp)
                )

                BlockKind.BODY -> Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (16 * fontScale).sp,
                        lineHeight = (24 * fontScale).sp
                    )
                )
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
            blocks.add(Block(BlockKind.BODY, parseInline(paragraphBuffer.joinToString(" "))))
            paragraphBuffer = mutableListOf()
        }
    }

    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> flushParagraph()
            line.startsWith("## ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.H2, parseInline(line.removePrefix("## ").trim())))
            }
            line.startsWith("# ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.H1, parseInline(line.removePrefix("# ").trim())))
            }
            line.startsWith("- ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.BULLET, parseInline(line.removePrefix("- ").trim())))
            }
            line.startsWith("* ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.BULLET, parseInline(line.removePrefix("* ").trim())))
            }
            line.startsWith("• ") -> {
                flushParagraph()
                blocks.add(Block(BlockKind.BULLET, parseInline(line.removePrefix("• ").trim())))
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
