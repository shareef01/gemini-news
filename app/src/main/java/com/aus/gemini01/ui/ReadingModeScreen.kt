package com.aus.gemini01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aus.gemini01.data.ai.AiResult
import com.aus.gemini01.data.ai.friendlyMessage
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
                                Icon(Icons.Default.Public, contentDescription = "Open in Web")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Could not load Reader View",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result.error.friendlyMessage(),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onOpenInWeb) {
                                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Web")
                            }
                            Button(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Try Again")
                            }
                        }
                    }
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
    val scrollState = rememberScrollState()
    val context = LocalContext.current

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
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Article Content", content)
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Content")
                    }
                    IconButton(onClick = onToggleReadAloud) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isSpeaking) "Stop Reading" else "Read Aloud",
                            tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        fontSizeMultiplier = when (fontSizeMultiplier) {
                            0.9f -> 1.1f
                            1.1f -> 1.4f
                            else -> 0.9f
                        }
                    }) {
                        Icon(Icons.Default.TextFields, contentDescription = "Adjust Font Size")
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
                // Reading measure: content column never exceeds a comfortable
                // line length, on phone or on the tablet two-pane detail pane.
                Column(
                    modifier = Modifier
                        .widthIn(max = Dimens.readingMeasureMax)
                        .padding(horizontal = Dimens.gutterReading, vertical = Dimens.spaceXXL)
                ) {
                    MarkdownText(
                        text = content,
                        fontScale = fontSizeMultiplier,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(Dimens.spaceXXL))

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = "Formatted by Gemini AI",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Dimens.spaceM, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimens.spaceXXL))
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
