package com.aus.gemini01.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aus.gemini01.data.Article
import com.aus.gemini01.ui.components.ShimmerList
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

@Composable
fun NewsListContent(
    viewModel: NewsViewModel,
    onArticleSelected: (Article) -> Unit,
    onReadingModeSelected: (Article) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val smartThemes by viewModel.smartThemes.collectAsState()
    val selectedSmartTheme by viewModel.selectedSmartTheme.collectAsState()
    val countryCode by viewModel.countryCode.collectAsState()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Compose auto-focuses the first focusable element (the search field) on
    // startup, which pops the keyboard. Clear it until the user taps the field.
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    val categories = listOf(
        "smart", "for_you", "general", "business", "entertainment", "health", "science", "sports", "technology", "bookmarks", "history"
    )

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchNews(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search news...", style = MaterialTheme.typography.bodyLarge) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true
                )

                val isListening by viewModel.isListening.collectAsState()
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) viewModel.startVoiceSearch()
                }

                if (isListening) {
                    IconButton(onClick = { viewModel.stopVoiceSearch() }) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Stop listening",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    IconButton(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startVoiceSearch()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(Icons.Default.MicNone, contentDescription = "Voice search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchNews("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        CategoryChips(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { viewModel.fetchNews(it) }
        )

    if (selectedCategory == "smart" && (smartThemes.isNotEmpty())) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                items(smartThemes.keys.toList()) { theme ->
                    AssistChip(
                        onClick = { viewModel.selectSmartTheme(theme) },
                        label = { Text(theme) },
                        colors = if (selectedSmartTheme == theme) 
                            AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else AssistChipDefaults.assistChipColors()
                    )
                }
            }
        }

        if (selectedCategory == "history") {
            Button(
                onClick = { viewModel.fetchReadingStats() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate AI Reading Stats ✨")
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.fetchNews() },
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
                    },
                    label = "NewsContentTransition"
                ) { state ->
                    when (state) {
                        is NewsUiState.Loading -> {
                            ShimmerList()
                        }
                        is NewsUiState.Error -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        is NewsUiState.Success -> {
                            if (state.articles.isEmpty()) {
                                EmptyState(
                                    icon = when (selectedCategory) {
                                        "bookmarks" -> Icons.Default.BookmarkBorder
                                        "history" -> Icons.Default.History
                                        "for_you" -> Icons.Default.PersonSearch
                                        else -> if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.AutoMirrored.Filled.Article
                                    },
                                    title = when (selectedCategory) {
                                        "bookmarks" -> "No bookmarks yet"
                                        "history" -> "No reading history yet"
                                        "for_you" -> "Get to know your taste"
                                        else -> if (searchQuery.isNotEmpty()) "No results found" else "No headlines for ${countryName(countryCode)}"
                                    },
                                    message = when (selectedCategory) {
                                        "bookmarks" -> "Tap the bookmark icon on any article to save it here."
                                        "history" -> "Articles you read will appear here."
                                        "for_you" -> "Bookmark or read a few articles so Gemini can learn what you like."
                                        else -> if (searchQuery.isNotEmpty()) {
                                            "Try different keywords or check your connection."
                                        } else {
                                            "Pull to refresh or try another category."
                                        }
                                    }
                                )
                            } else {
                                NewsList(
                                    articles = state.articles,
                                    isLoadingMore = isLoadingMore,
                                    onSummarize = { article -> viewModel.summarizeArticle(article) },
                                    onBookmarkToggle = { article -> viewModel.toggleBookmark(article) },
                                    isBookmarked = { url -> viewModel.isBookmarked(url) },
                                    onLoadMore = { viewModel.loadNextPage() },
                                    onReadMore = { article ->
                                        viewModel.addToHistory(article)
                                        onArticleSelected(article)
                                    },
                                    onReadingMode = { article ->
                                        viewModel.addToHistory(article)
                                        onReadingModeSelected(article)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIProgressOverlay(message: String, onCancel: (() -> Unit)? = null) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiAura")
    
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )
    
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing, delayMillis = 500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Layered pulsing auras
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(pulse2),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ) {}
                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .scale(pulse1),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {}
                
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (onCancel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val label = when(category) {
                "smart" -> "Smart"
                "for_you" -> "For You"
                "bookmarks" -> "Bookmarks"
                "history" -> "History"
                else -> category.replaceFirstChar { it.uppercase() }
            }
            val icon = when(category) {
                "smart" -> Icons.Default.AutoAwesome
                "for_you" -> Icons.Default.Insights
                "general" -> Icons.Default.Public
                "business" -> Icons.Default.BusinessCenter
                "entertainment" -> Icons.Default.Movie
                "health" -> Icons.Default.HealthAndSafety
                "science" -> Icons.Default.Science
                "sports" -> Icons.Default.SportsBasketball
                "technology" -> Icons.Default.Memory
                "bookmarks" -> Icons.Default.Bookmark
                "history" -> Icons.Default.History
                else -> null
            }
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(label) },
                leadingIcon = icon?.let {
                    { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
                }
            )
        }
    }
}

@Composable
fun NewsList(
    articles: List<Article>,
    isLoadingMore: Boolean,
    onSummarize: (Article) -> Unit,
    onBookmarkToggle: (Article) -> Unit,
    isBookmarked: (String) -> Flow<Boolean>,
    onLoadMore: () -> Unit,
    onReadMore: (Article) -> Unit,
    onReadingMode: (Article) -> Unit
) {
    val listState = rememberLazyListState()

    // Load more when reaching the end of the list
    LaunchedEffect(listState, articles.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= articles.size - 5) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(articles, key = { it.url }) { article ->
            Box(modifier = Modifier.animateItem()) {
                ArticleCard(
                    article = article,
                    onSummarize = { onSummarize(article) },
                    onBookmarkToggle = { onBookmarkToggle(article) },
                    isBookmarkedFlow = isBookmarked(article.url),
                    onReadMore = { 
                        onReadMore(article)
                    },
                    onReadingMode = {
                        onReadingMode(article)
                    }
                )
            }
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun ArticleCard(
    article: Article,
    onSummarize: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isBookmarkedFlow: Flow<Boolean>,
    onReadMore: () -> Unit,
    onReadingMode: () -> Unit
) {
    val context = LocalContext.current
    val isBookmarked by isBookmarkedFlow.collectAsState(initial = false)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp, pressedElevation = 8.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box {
            Column {
                // Image area: branded placeholder shows while loading, when the
                // image fails, or when the article has no image at all.
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = article.source.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    safeImageUrl(article.urlToImage)?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Scrim behind the floating actions for contrast
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                                )
                            )
                    )
                }
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = article.source.name,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        val timeAgo = formatTimeAgo(article.publishedAt)
                        if (timeAgo.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "•  $timeAgo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    article.description?.let {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row {
                            TextButton(
                                onClick = onReadMore,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Web", style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(
                                onClick = onReadingMode,
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reader", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        FilledTonalButton(
                            onClick = onSummarize,
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Summarize", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Floating share / bookmark actions over the image
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                color = Color.Black.copy(alpha = 0.45f),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nRead more at: ${article.url}")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = onBookmarkToggle, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.tertiary else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimeAgo(publishedAt: String): String {
    return try {
        val published = Instant.parse(publishedAt)
        val minutes = Duration.between(published, Instant.now()).toMinutes()
        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            else -> {
                val hours = minutes / 60
                if (hours < 24) "${hours}h ago" else "${hours / 24}d ago"
            }
        }
    } catch (e: Exception) {
        ""
    }
}

private fun countryName(code: String): String = when (code) {
    "us" -> "United States"
    "gb" -> "United Kingdom"
    "in" -> "India"
    "au" -> "Australia"
    "ca" -> "Canada"
    "de" -> "Germany"
    "fr" -> "France"
    else -> code
}

@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(96.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(28.dp).fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
