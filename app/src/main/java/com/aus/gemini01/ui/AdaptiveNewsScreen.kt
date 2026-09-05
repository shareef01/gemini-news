package com.aus.gemini01.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aus.gemini01.data.Article
import com.aus.gemini01.data.ai.AiResult
import com.aus.gemini01.data.ai.friendlyMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveNewsScreen(
    viewModel: NewsViewModel = viewModel()
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Article>()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface refresh/mapping errors that were previously swallowed silently.
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    val uiState by viewModel.uiState.collectAsState()
    val readerViewContent by viewModel.readerViewContent.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val newsLocations by viewModel.newsLocations.collectAsState()
    val pendingArticleUrl by viewModel.pendingArticleUrl.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatting by viewModel.isChatting.collectAsState()

    var selectedArticleForWeb by rememberSaveable { mutableStateOf<Article?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showChat by rememberSaveable { mutableStateOf(false) }

    // Notification taps may arrive before the feed has loaded. Resolve the
    // validated URL against loaded NewsAPI/Room data before opening WebView.
    LaunchedEffect(pendingArticleUrl, uiState) {
        val pending = pendingArticleUrl ?: return@LaunchedEffect
        val article = (uiState as? NewsUiState.Success)?.articles?.firstOrNull { it.url == pending }
            ?: return@LaunchedEffect
        viewModel.clearPendingArticleUrl()
        selectedArticleForWeb = article
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, article)
    }

    // Logic to decide what goes in the detail pane
    val currentDetailArticle = navigator.currentDestination?.contentKey

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
    } else if (showChat) {
        BackHandler { showChat = false }
        NewsChatScreen(
            messages = chatMessages,
            isTyping = isChatting,
            onSendMessage = { viewModel.sendChatMessage(it) },
            onBack = { showChat = false }
        )
    } else if (newsLocations.isNotEmpty()) {
        BackHandler { viewModel.clearNewsLocations() }
        NewsMapScreen(
            locations = newsLocations,
            onBack = { viewModel.clearNewsLocations() },
            onArticleClick = { url ->
                val article = (uiState as? NewsUiState.Success)?.articles?.find { it.url == url }
                if (article != null) {
                    viewModel.clearNewsLocations()
                    selectedArticleForWeb = article
                    viewModel.fetchReaderView(article)
                    coroutineScope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, article)
                    }
                }
            }
        )
    } else {
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Gemini News",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                },
                                actions = {
                                    IconButton(onClick = { showChat = true }) {
                                        Icon(
                                            Icons.Default.QuestionAnswer,
                                            contentDescription = "News Expert Chat",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.analyzeNewsLocations() }) {
                                        Icon(
                                            Icons.Default.Public,
                                            contentDescription = "News Map",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.analyzeTrendingTopics() }) {
                                        Icon(
                                            Icons.Default.LocalFireDepartment,
                                            contentDescription = "Trending Topics",
                                            tint = com.aus.gemini01.ui.theme.TrendingFlame
                                        )
                                    }
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(
                                            Icons.Default.Settings,
                                            contentDescription = "Settings",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.fetchNews() }) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Refresh Feed",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    ) { innerPadding ->
                        NewsListContent(
                            viewModel = viewModel,
                            onArticleSelected = { article ->
                                selectedArticleForWeb = article
                                coroutineScope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, article)
                                }
                            },
                            onReadingModeSelected = { article ->
                                selectedArticleForWeb = null
                                viewModel.fetchReaderView(article)
                                coroutineScope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, article)
                                }
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            },
            detailPane = {
                AnimatedPane {
                    if (currentDetailArticle != null) {
                        if (readerViewContent != null) {
                            ReadingModeScreen(
                                result = readerViewContent!!,
                                isSpeaking = isSpeaking,
                                onBack = { 
                                    viewModel.clearReaderView()
                                    coroutineScope.launch { navigator.navigateBack() }
                                },
                                onOpenInWeb = { 
                                    selectedArticleForWeb = currentDetailArticle
                                    viewModel.clearReaderView()
                                },
                                onRetry = {
                                    viewModel.fetchReaderView(currentDetailArticle)
                                },
                                onToggleReadAloud = {
                                    val text = (readerViewContent as? AiResult.Success)?.text
                                    if (text != null) viewModel.toggleReadAloud(text)
                                }
                            )
                        } else if (selectedArticleForWeb != null) {
                            ArticleWebView(
                                url = selectedArticleForWeb!!.url,
                                title = selectedArticleForWeb!!.title,
                                onBack = { 
                                    selectedArticleForWeb = null
                                    coroutineScope.launch { navigator.navigateBack() }
                                }
                            )
                        } else {
                            com.aus.gemini01.ui.components.ReaderViewShimmer()
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .background(
                                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f)
                                                )
                                            ),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier.size(96.dp),
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.padding(26.dp).fillMaxSize(),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "Select a story to begin",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tap any news card to read distraction-free or explore AI insights.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        "Tip: tap Summarize ✨ on any story",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    GlobalOverlays(viewModel)
}

@Composable
fun AIDialog(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    result: AiResult,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    confirmText: String = "Got it"
) {
    val context = LocalContext.current
    val isSuccess = result is AiResult.Success
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(
                            if (isSuccess) icon else Icons.Default.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(22.dp),
                            tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isSuccess) title else "AI Notice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isSuccess) "Generated by Gemini AI" else "Gemini AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (result is AiResult.Success) {
                    IconButton(onClick = {
                        val text = result.text
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Gemini Insights", text)
                        clipboard.setPrimaryClip(clip)
                        copied = true
                    }) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy Insights",
                            tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            val scrollState = rememberScrollState()
            SelectionContainer {
                when (result) {
                    is AiResult.Success -> {
                        MarkdownText(
                            text = result.text,
                            modifier = Modifier
                                .heightIn(max = 480.dp)
                                .verticalScroll(scrollState)
                        )
                    }
                    is AiResult.Failure -> {
                        Text(
                            text = result.error.friendlyMessage(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isSuccess && onRetry != null) {
                    TextButton(onClick = {
                        onDismiss()
                        onRetry()
                    }) {
                        Text("Try Again")
                    }
                }
                FilledTonalButton(onClick = onDismiss, shape = MaterialTheme.shapes.large) {
                    Text(confirmText)
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
    )
}

@Composable
fun GlobalOverlays(viewModel: NewsViewModel) {
    val summary by viewModel.summaryState.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val isAnalysingInterests by viewModel.isAnalysingInterests.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isAnalysingStats by viewModel.isAnalysingStats.collectAsState()
    val isAnalysingTrends by viewModel.isAnalysingTrends.collectAsState()
    val isAnalysingSmartThemes by viewModel.isAnalysingSmartThemes.collectAsState()
    val isAnalysingLocations by viewModel.isAnalysingLocations.collectAsState()
    val readingStats by viewModel.readingStats.collectAsState()
    val trendingTopics by viewModel.trendingTopics.collectAsState()

    AnimatedVisibility(visible = isAnalysingInterests, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Gemini is analyzing your interests...", onCancel = { viewModel.cancelAnalysis() })
    }
    AnimatedVisibility(visible = isSummarizing, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Gemini is summarizing...", onCancel = { viewModel.cancelAnalysis() })
    }
    AnimatedVisibility(visible = isListening, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Listening...")
    }
    AnimatedVisibility(visible = isAnalysingStats, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Gemini is analyzing your week...", onCancel = { viewModel.cancelAnalysis() })
    }
    AnimatedVisibility(visible = isAnalysingTrends, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Gemini is identifying trends...", onCancel = { viewModel.cancelAnalysis() })
    }
    AnimatedVisibility(visible = isAnalysingSmartThemes, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Gemini is building your custom news themes...", onCancel = { viewModel.cancelAnalysis() })
    }
    AnimatedVisibility(visible = isAnalysingLocations, enter = fadeIn(), exit = fadeOut()) {
        AIProgressOverlay(message = "Gemini is mapping the news...", onCancel = { viewModel.cancelAnalysis() })
    }

    // Let the user bail out of a stuck AI request with the system back gesture.
    if (isAnalysingInterests || isSummarizing || isAnalysingStats || isAnalysingTrends ||
        isAnalysingSmartThemes || isAnalysingLocations
    ) {
        BackHandler { viewModel.cancelAnalysis() }
    }

    summary?.let {
        AIDialog(
            title = "Gemini Insights",
            icon = Icons.Default.AutoAwesome,
            result = it,
            onDismiss = { viewModel.clearSummary() },
            onRetry = { viewModel.retrySummarize() }
        )
    }

    trendingTopics?.let {
        AIDialog(
            title = "Global News Trends",
            icon = Icons.Default.LocalFireDepartment,
            result = it,
            onDismiss = { viewModel.clearTrendingTopics() },
            onRetry = { viewModel.analyzeTrendingTopics() }
        )
    }

    readingStats?.let {
        AIDialog(
            title = "Weekly News Insights",
            icon = Icons.Default.Insights,
            result = it,
            onDismiss = { viewModel.clearReadingStats() },
            onRetry = { viewModel.fetchReadingStats() },
            confirmText = "Done"
        )
    }
}
