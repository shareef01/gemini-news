package com.aus.gemini01.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aus.gemini01.data.ai.GEMINI_MODEL_LABEL
import com.aus.gemini01.ui.components.ConfirmationDialog
import com.aus.gemini01.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: NewsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val countryCode by viewModel.countryCode.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val remindersEnabled by viewModel.remindersEnabled.collectAsState()
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()
    val newsApiFreeTier by viewModel.newsApiFreeTier.collectAsState()

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setRemindersEnabled(true)
    }

    var countryExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    val countries = mapOf(
        "us" to "United States"
    )
    val languages = listOf("English", "Spanish", "French", "German", "Chinese", "Arabic", "Portuguese")

    if (showClearCacheDialog) {
        ConfirmationDialog(
            title = "Clear Cache & AI Data?",
            text = "This will remove all cached headlines and stored AI summaries. Fresh stories will reload when you browse.",
            confirmText = "Clear Cache",
            isDestructive = true,
            onConfirm = {
                viewModel.clearCache()
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }

    if (showClearHistoryDialog) {
        ConfirmationDialog(
            title = "Clear Reading History?",
            text = "This will delete your local reading history. Cached AI reading-stats results are cleared only when you also clear the headlines & AI cache.",
            confirmText = "Clear History",
            isDestructive = true,
            onConfirm = {
                viewModel.clearHistory()
                showClearHistoryDialog = false
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceM)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)
        ) {
            // Section 1: Content & Language
            SettingsSection(
                title = "Feed & Language",
                icon = Icons.Default.Public
            ) {
                Box {
                    OutlinedTextField(
                        value = countries[countryCode] ?: "United States",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("News Region") },
                        shape = RoundedCornerShape(Dimens.radiusM),
                        trailingIcon = {
                            IconButton(onClick = { countryExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Region")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        countries.forEach { (code, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    viewModel.setCountryCode(code)
                                    countryExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimens.spaceM))

                Box {
                    OutlinedTextField(
                        value = preferredLanguage,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("AI Language") },
                        shape = RoundedCornerShape(Dimens.radiusM),
                        trailingIcon = {
                            IconButton(onClick = { languageExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Language")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        languages.forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                onClick = {
                                    viewModel.setPreferredLanguage(language)
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Section 2: Gemini AI & Performance
            SettingsSection(
                title = "Gemini AI Engine",
                icon = Icons.Default.AutoAwesome
            ) {
                AiDiagnosticsCard(viewModel)

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Dimens.spaceM),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Free-tier data saver",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Pauses background breaking-news polling to conserve NewsAPI quota. Turning this on disables breaking alerts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = newsApiFreeTier,
                        onCheckedChange = { viewModel.setNewsApiFreeTier(it) }
                    )
                }
            }

            // Section 3: Notifications & Reminders
            SettingsSection(
                title = "Notifications",
                icon = Icons.Default.Notifications
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Breaking News Alerts",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Top headline notifications when news breaks. Enables background NewsAPI polling (turns off free-tier saver).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setNotificationsEnabled(checked)
                            }
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Dimens.spaceM),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Daily Reading Reminders",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Daily reminder to read your bookmarked stories",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { checked ->
                            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                reminderPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setRemindersEnabled(checked)
                            }
                        }
                    )
                }
            }

            // Section 4: Storage & Maintenance
            SettingsSection(
                title = "Storage & Cache",
                icon = Icons.Default.Storage
            ) {
                OutlinedButton(
                    onClick = { showClearCacheDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusM)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSizeS)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    Text("Clear Headlines & AI Cache")
                }

                Spacer(modifier = Modifier.height(Dimens.spaceS))

                OutlinedButton(
                    onClick = { showClearHistoryDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusM)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSizeS)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    Text("Clear Reading History")
                }
            }

            // Section 5: About & Sharing
            SettingsSection(
                title = "About",
                icon = Icons.Default.Info
            ) {
                OutlinedButton(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "Check out Gemini News — an AI-powered smart news reader app!")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Gemini News")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Dimens.radiusM)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSizeS)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    Text("Share Gemini News App")
                }

                Spacer(modifier = Modifier.height(Dimens.spaceM))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Gemini News v1.0.0",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Powered by Google Cloud Vertex AI & NewsAPI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spaceXL))
        }
    }
}

@Composable
private fun AiDiagnosticsCard(viewModel: NewsViewModel) {
    val diagnostics by viewModel.aiDiagnostics.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }
    val d = diagnostics

    Surface(
        shape = RoundedCornerShape(Dimens.radiusM),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceL)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(6.dp).size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(Dimens.spaceM))
                    Column {
                        Text(
                            text = GEMINI_MODEL_LABEL,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active & Ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(
                        text = if (isExpanded) "Hide details" else "Diagnostics",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.spaceM),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceXS)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(bottom = Dimens.spaceS)
                    )

                    if (d == null) {
                        Text(
                            "AI telemetry will appear on your first AI action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val totalRequests = d.requestsByFeature.values.sum()
                        val topFeature = d.requestsByFeature.maxByOrNull { it.value }?.key

                        Text(
                            "• Total AI Requests: $totalRequests",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "• Cache Performance: ${d.cacheHits} hits / ${d.cacheMisses} misses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (topFeature != null && d.requestsByFeature[topFeature]!! > 0) {
                            Text(
                                "• Top Feature: ${topFeature.replace('_', ' ')} (${d.requestsByFeature[topFeature]} calls)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (d.errorsByType["quota"] != null && d.errorsByType["quota"]!! > 0) {
                            Text(
                                "• Quota Warnings: ${d.errorsByType["quota"]}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        d.lastRequestAt?.let { last ->
                            val minutesAgo = (System.currentTimeMillis() - last) / 60000
                            val lastText = when {
                                minutesAgo < 1 -> "just now"
                                minutesAgo < 60 -> "$minutesAgo min ago"
                                else -> "${minutesAgo / 60}h ago"
                            }
                            Text(
                                "• Last Invocation: $lastText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.radiusL),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(Dimens.spaceL)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp).size(Dimens.iconSizeS),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.spaceM))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spaceL))
            content()
        }
    }
}

