package com.aus.gemini01.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aus.gemini01.data.Article
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
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isChatting by viewModel.isChatting.collectAsState()

    var selectedArticleForWeb by remember { mutableStateOf<Article?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }

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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .padding(6.dp)
                                                    .size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                "Gemini News",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "AI-powered news",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { showChat = true }) {
                                        Icon(Icons.Default.QuestionAnswer, contentDescription = "AI Chat", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.analyzeNewsLocations() }) {
                                        Icon(Icons.Default.Public, contentDescription = "Map", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.analyzeTrendingTopics() }) {
                                        Icon(Icons.Default.LocalFireDepartment, contentDescription = "Trending", tint = MaterialTheme.colorScheme.error)
                                    }
                                    IconButton(onClick = { showSettings = true }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                    IconButton(onClick = { viewModel.fetchNews() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                    }
                                }
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
                                content = readerViewContent!!,
                                isSpeaking = isSpeaking,
                                onBack = { 
                                    viewModel.clearReaderView()
                                    coroutineScope.launch { navigator.navigateBack() }
                                },
                                onOpenInWeb = { 
                                    selectedArticleForWeb = currentDetailArticle
                                    viewModel.clearReaderView()
                                },
                                onToggleReadAloud = { viewModel.toggleReadAloud(readerViewContent!!) }
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
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
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
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.padding(26.dp).fillMaxSize(),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                                Text(
                                    "Ready for your first story?",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Select an article from the list to start reading with Gemini's AI insights.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        "Tip: tap Summarize ✨ on any card",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
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
    content: String,
    onDismiss: () -> Unit,
    confirmText: String = "Got it"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Widen the dialog so AI content (bullet lists, entities) fits comfortably.
        modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Generated by Gemini AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            val scrollState = rememberScrollState()
            SelectionContainer {
                MarkdownText(
                    text = content,
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(scrollState)
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss, shape = MaterialTheme.shapes.large) {
                Text(confirmText)
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
            content = it,
            onDismiss = { viewModel.clearSummary() }
        )
    }

    trendingTopics?.let {
        AIDialog(
            title = "Global News Trends",
            icon = Icons.Default.LocalFireDepartment,
            content = it,
            onDismiss = { viewModel.clearTrendingTopics() }
        )
    }

    readingStats?.let {
        AIDialog(
            title = "Weekly News Insights",
            icon = Icons.Default.Insights,
            content = it,
            onDismiss = { viewModel.clearReadingStats() },
            confirmText = "Done"
        )
    }
}
