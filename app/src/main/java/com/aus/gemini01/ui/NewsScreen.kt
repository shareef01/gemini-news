package com.aus.gemini01.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aus.gemini01.data.Article
import com.aus.gemini01.ui.components.EmptyStateView
import com.aus.gemini01.ui.components.ErrorStateView
import com.aus.gemini01.ui.components.PulseIndicator
import com.aus.gemini01.ui.components.ShimmerList
import com.aus.gemini01.ui.theme.Dimens
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
    val isServingCached by viewModel.isServingCached.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val bookmarkedUrls = remember(bookmarks) { bookmarks.map { it.url }.toSet() }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
    }

    val categories = listOf(
        "general", "technology", "business", "entertainment", "sports", "science", "health", "for_you", "smart", "bookmarks", "history"
    )

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceS),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusXXL),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.searchBarHeight)
                    .padding(horizontal = Dimens.spaceS),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(start = Dimens.spaceS),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchNews(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search global headlines...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) viewModel.startVoiceSearch()
                }

                if (isListening) {
                    IconButton(onClick = { viewModel.stopVoiceSearch() }) {
                        PulseIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startVoiceSearch()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }) {
                        Icon(
                            Icons.Default.MicNone,
                            contentDescription = "Voice search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.searchNews("")
                        focusManager.clearFocus()
                    }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        CategoryChips(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = {
                focusManager.clearFocus()
                viewModel.fetchNews(it)
            }
        )

        if (selectedCategory == "smart" && smartThemes.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceXS),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
            ) {
                items(smartThemes.keys.toList()) { theme ->
                    FilterChip(
                        selected = selectedSmartTheme == theme,
                        onClick = { viewModel.selectSmartTheme(theme) },
                        label = { Text(theme, style = MaterialTheme.typography.labelMedium) },
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        }

        if (selectedCategory == "history") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceXS),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimens.iconSizeM)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spaceM))
                        Column {
                            Text(
                                "Weekly Reading Personality",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Synthesize your reading habits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { viewModel.fetchReadingStats() },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = Dimens.spaceM, vertical = Dimens.spaceXS)
                    ) {
                        Text("Analyze ✨", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (isServingCached && selectedCategory !in setOf("bookmarks", "history")) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceXS),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconSizeS),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(Dimens.spaceM))
                        Text(
                            "Offline — showing cached headlines",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { viewModel.fetchNews() },
                        contentPadding = PaddingValues(horizontal = Dimens.spaceS)
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelMedium)
                    }
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
                        fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                    },
                    label = "NewsContentTransition"
                ) { state ->
                    when (state) {
                        is NewsUiState.Loading -> {
                            ShimmerList()
                        }
                        is NewsUiState.Error -> {
                            ErrorStateView(
                                message = state.message,
                                onRetry = { viewModel.fetchNews() }
                            )
                        }
                        is NewsUiState.Success -> {
                            if (state.articles.isEmpty()) {
                                EmptyStateView(
                                    icon = when (selectedCategory) {
                                        "bookmarks" -> Icons.Default.BookmarkBorder
                                        "history" -> Icons.Default.History
                                        "for_you" -> Icons.Default.PersonSearch
                                        "smart" -> Icons.Default.AutoAwesome
                                        else -> if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.AutoMirrored.Filled.Article
                                    },
                                    title = when (selectedCategory) {
                                        "bookmarks" -> "No bookmarks yet"
                                        "history" -> "No reading history yet"
                                        "for_you" -> "Discover Your Tailored Feed"
                                        "smart" -> "No smart themes found"
                                        else -> if (searchQuery.isNotEmpty()) "No results found" else "No headlines available"
                                    },
                                    message = when (selectedCategory) {
                                        "bookmarks" -> "Tap the bookmark icon on any story to save it for offline reading."
                                        "history" -> "Articles you open in Reader Mode will be saved here."
                                        "for_you" -> "Bookmark or read a few stories so Gemini AI can learn your reading interests."
                                        "smart" -> "Tap the button below to analyze today's news with Gemini."
                                        else -> if (searchQuery.isNotEmpty()) {
                                            "Try different keywords or check your connection."
                                        } else {
                                            "Pull down to refresh or explore other news categories."
                                        }
                                    },
                                    actionLabel = if (selectedCategory == "smart") "Generate Themes ✨" else null,
                                    onAction = if (selectedCategory == "smart") { { viewModel.generateSmartThemes() } } else null
                                )
                            } else {
                                NewsList(
                                    articles = state.articles,
                                    isLoadingMore = isLoadingMore,
                                    bookmarkedUrls = bookmarkedUrls,
                                    onSummarize = { article -> viewModel.summarizeArticle(article) },
                                    onBookmarkToggle = { article -> viewModel.toggleBookmark(article) },
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spaceXXL),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.radiusXXL),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.spaceL, vertical = Dimens.spaceM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceM)
            ) {
                AIOrb()
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onCancel != null) {
                    TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = Dimens.spaceS)) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AIOrb() {
    val transition = rememberInfiniteTransition(label = "aiOrb")
    val pulse by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )
    Icon(
        imageVector = Icons.Default.AutoAwesome,
        contentDescription = null,
        modifier = Modifier
            .size(Dimens.iconSizeM)
            .scale(pulse),
        tint = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Dimens.gutterDefault, vertical = Dimens.spaceXS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)
    ) {
        items(categories) { category ->
            val label = when(category) {
                "general" -> "Top Stories"
                "smart" -> "Smart Themes"
                "for_you" -> "For You"
                "bookmarks" -> "Bookmarks"
                "history" -> "History"
                else -> category.replaceFirstChar { it.uppercase() }
            }
            val icon = when(category) {
                "general" -> Icons.AutoMirrored.Filled.TrendingUp
                "smart" -> Icons.Default.AutoAwesome
                "for_you" -> Icons.Default.Insights
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
                label = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Medium
                    )
                },
                leadingIcon = icon?.let {
                    { Icon(it, contentDescription = null, modifier = Modifier.size(Dimens.iconSizeXS)) }
                },
                shape = MaterialTheme.shapes.large
            )
        }
    }
}

@Composable
fun NewsList(
    articles: List<Article>,
    isLoadingMore: Boolean,
    bookmarkedUrls: Set<String>,
    onSummarize: (Article) -> Unit,
    onBookmarkToggle: (Article) -> Unit,
    onLoadMore: () -> Unit,
    onReadMore: (Article) -> Unit,
    onReadingMode: (Article) -> Unit
) {
    val listState = rememberLazyListState()

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
        contentPadding = PaddingValues(
            start = Dimens.gutterDefault,
            end = Dimens.gutterDefault,
            top = Dimens.spaceXS,
            bottom = Dimens.spaceXXXL
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.cardVerticalSpacing)
    ) {
        items(articles, key = { it.url }) { article ->
            Box(modifier = Modifier.animateItem()) {
                ArticleCard(
                    article = article,
                    onSummarize = { onSummarize(article) },
                    onBookmarkToggle = { onBookmarkToggle(article) },
                    isBookmarked = article.url in bookmarkedUrls,
                    onReadMore = { onReadMore(article) },
                    onReadingMode = { onReadingMode(article) }
                )
            }
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimens.spaceL),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
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
    isBookmarked: Boolean,
    onReadMore: () -> Unit,
    onReadingMode: () -> Unit
) {
    val context = LocalContext.current

    ElevatedCard(
        onClick = onReadingMode,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.5.dp,
            pressedElevation = 4.dp
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(Dimens.articleImageAspectRatio)
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
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(Dimens.spaceXS))
                        Text(
                            text = article.source.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    safeImageUrl(article.urlToImage)?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(Dimens.articleImageAspectRatio),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                                )
                            )
                    )
                }

                Column(modifier = Modifier.padding(Dimens.spaceL)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = article.source.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val timeAgo = formatTimeAgo(article.publishedAt)
                        if (timeAgo.isNotEmpty()) {
                            Text(
                                text = "  •  $timeAgo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceS))

                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    article.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Dimens.spaceS))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.spaceL))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onReadingMode,
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = Dimens.spaceL, vertical = Dimens.spaceS)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(Dimens.iconSizeS),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(Dimens.spaceS))
                            Text("Read", style = MaterialTheme.typography.labelLarge)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXS)) {
                            OutlinedButton(
                                onClick = onSummarize,
                                shape = MaterialTheme.shapes.large,
                                contentPadding = PaddingValues(horizontal = Dimens.spaceM, vertical = Dimens.spaceXS),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimens.iconSizeXS),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(Dimens.spaceXS))
                                Text("Summarize", style = MaterialTheme.typography.labelMedium)
                            }

                            TextButton(
                                onClick = onReadMore,
                                contentPadding = PaddingValues(horizontal = Dimens.spaceS)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimens.iconSizeXS),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(Dimens.spaceXXS))
                                Text("Web", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spaceS),
                color = Color.Black.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nRead more: ${article.url}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Article"))
                        },
                        modifier = Modifier.size(Dimens.touchTarget)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share Article",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.size(Dimens.touchTarget)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Remove Bookmark" else "Save Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primaryContainer else Color.White,
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
