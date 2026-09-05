package com.aus.gemini01.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aus.gemini01.data.ai.AiResult
import com.aus.gemini01.data.ai.friendlyMessage
import com.aus.gemini01.ui.components.ErrorStateView
import com.aus.gemini01.ui.components.PulseIndicator
import com.aus.gemini01.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingModeScreen(
    result: AiResult,
    isSpeaking: Boolean,
    onBack: () -> Unit,
    onOpenInWeb: () -> Unit,
    onRetry: () -> Unit = {},
    onToggleReadAloud: () -> Unit
) {
    when (result) {
        is AiResult.Success -> {
            ReadingModeContent(
                content = result.text,
                isSpeaking = isSpeaking,
                onBack = onBack,
                onOpenInWeb = onOpenInWeb,
                onToggleReadAloud = onToggleReadAloud
            )
        }
        is AiResult.Failure -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Reader View") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenInWeb) {
                                Icon(Icons.Default.Public, contentDescription = "Open original in browser")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    ErrorStateView(
                        title = "Could not format story",
                        message = result.error.friendlyMessage(),
                        onRetry = onRetry,
                        actionLabel = "Retry AI Reader"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingModeContent(
    content: String,
    isSpeaking: Boolean,
    onBack: () -> Unit,
    onOpenInWeb: () -> Unit,
    onToggleReadAloud: () -> Unit
) {
    var fontSizeMultiplier by remember { mutableFloatStateOf(1.1f) }
    var copied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val fontSizeLabel = when (fontSizeMultiplier) {
        0.95f -> "Compact"
        1.1f -> "Default"
        1.3f -> "Large"
        else -> "Default"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Reader View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI-formatted from NewsAPI fields",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Article Content", content)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                    }) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy story text",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onToggleReadAloud) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) "Stop Read Aloud" else "Read Aloud",
                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        fontSizeMultiplier = when (fontSizeMultiplier) {
                            0.95f -> 1.1f
                            1.1f -> 1.3f
                            else -> 0.95f
                        }
                    }) {
                        Icon(Icons.Default.TextFields, contentDescription = "Font size: $fontSizeLabel")
                    }
                    IconButton(onClick = onOpenInWeb) {
                        Icon(Icons.Default.Public, contentDescription = "Open in Web")
                    }
                }
            )
        }
    ) { innerPadding ->
        val progress by remember {
            derivedStateOf {
                if (scrollState.maxValue > 0) {
                    scrollState.value.toFloat() / scrollState.maxValue
                } else 0f
            }
        }

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Audio Speaking Banner
                AnimatedVisibility(
                    visible = isSpeaking,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimens.gutterReading, vertical = Dimens.spaceS)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulseIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(Dimens.spaceM))
                                Text(
                                    text = "Reading aloud...",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(onClick = onToggleReadAloud) {
                                Text("Stop", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Reading measure: content column constrained for readability
                Column(
                    modifier = Modifier
                        .widthIn(max = Dimens.readingMeasureMax)
                        .padding(horizontal = Dimens.gutterReading, vertical = Dimens.spaceL)
                ) {
                    MarkdownText(
                        text = content,
                        fontScale = fontSizeMultiplier,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceXXL))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "AI-formatted from NewsAPI fields • Font: $fontSizeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimens.spaceXXXL))
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        }
    }
}

