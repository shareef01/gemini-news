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
import com.aus.gemini01.BuildConfig
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

    val newsProviderLabel = if (
        BuildConfig.NEWS_PROXY_BASE_URL.isNotBlank() ||
        BuildConfig.NEWS_PROVIDER.equals("newsapi", ignoreCase = true)
    ) "NewsAPI" else "GDELT"

    val reminderPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setRemindersEnabled(true)
    }

    var countryExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    val regions = com.aus.gemini01.data.NewsRegion.SUPPORTED_REGIONS
    val currentRegionName = com.aus.gemini01.data.NewsRegion.fromCountryCode(countryCode).displayName
    val languages = listOf("English", "Spanish", "French", "German", "Chinese", "Arabic", "Portuguese")

    if (showClearCacheDialog) {
        ConfirmationDialog(
            title = "Clear Cache & AI Data?",
            text = "This removes saved headlines and AI summaries. Stories already on screen stay visible; pull to refresh when you want fresh headlines.",
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
            text = "This will remove your reading history across all sessions. Bookmarks will be kept.",
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
                title = { Text("Settings") },
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
                        value = currentRegionName,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("News Region") },
                        supportingText = {
                            Text("United States headlines are enabled for this build.")
                        },
                        shape = RoundedCornerShape(Dimens.radiusM),
                        trailingIcon = if (regions.size > 1) {
                            {
                                IconButton(onClick = { countryExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Region")
                                }
                            }
                        } else null
                    )
                    DropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        regions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region.displayName) },
                                onClick = {
                                    viewModel.setCountryCode(region.countryCode)
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
            }

            // Section 3: Notifications & Reminders
            SettingsSection(
                title = "News & Notifications",
                icon = Icons.Default.Notifications
            ) {
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
                            "Pauses background $newsProviderLabel polling to limit requests. Breaking News Alerts won't be sent while this is on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = newsApiFreeTier,
                        onCheckedChange = { viewModel.setNewsApiFreeTier(it) }
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
                            "Breaking News Alerts",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Notifies you when a new top headline appears. Uses background $newsProviderLabel polling and is paused by the data saver.",
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
                        runCatching { context.startActivity(shareIntent) }
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
                        text = "Gemini News v${BuildConfig.VERSION_NAME} (In Development)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "News: $newsProviderLabel • AI: Gemini",
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

                    if (d.date.isEmpty() && d.requestsByFeature.isEmpty()) {
                        Text(
                            "AI telemetry will appear on your first AI action.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val requestsByFeature = d.requestsByFeature.filterKeys { !it.startsWith("cached_") }
                        val storedResultCount = d.requestsByFeature
                            .filterKeys { it.startsWith("cached_") }
                            .values
                            .sum()
                        val totalRequests = requestsByFeature.values.sum()
                        val topFeature = requestsByFeature.maxByOrNull { it.value }?.key

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
                        Text(
                            "• Stored AI Results: $storedResultCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (topFeature != null && requestsByFeature[topFeature]!! > 0) {
                            Text(
                                "• Top Feature: ${topFeature.replace('_', ' ')} (${requestsByFeature[topFeature]} calls)",
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

