package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.NewsRepository
import com.aus.gemini01.data.newsFeedErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FeedManager(
    private val repository: NewsRepository,
    private val scope: CoroutineScope,
    private val errorEvents: MutableSharedFlow<String>
) {
    private val _uiState = MutableStateFlow<NewsUiState>(NewsUiState.Loading)
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    private val _selectedCategory = MutableStateFlow("general")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isServingCached = MutableStateFlow(false)
    val isServingCached: StateFlow<Boolean> = _isServingCached.asStateFlow()

    private val _cachedAt = MutableStateFlow<Long?>(null)
    val cachedAt: StateFlow<Long?> = _cachedAt.asStateFlow()

    private val _pendingArticleUrl = MutableStateFlow<String?>(null)
    val pendingArticleUrl: StateFlow<String?> = _pendingArticleUrl.asStateFlow()

    private var currentPage = 1
    private var isLastPage = false
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val bookmarkedArticles: StateFlow<List<Article>> = repository.getAllBookmarks()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyArticles: StateFlow<List<Article>> = repository.getAllHistory()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var fetchJob: Job? = null
    private var searchJob: Job? = null
    private var pageJob: Job? = null
    private var fetchGeneration = 0L
    private var searchGeneration = 0L
    var forYouKeywords: String = ""

    fun selectCategory(category: String, countryCode: String) {
        if (_selectedCategory.value != category || _searchQuery.value.isNotBlank()) {
            _selectedCategory.value = category
            _searchQuery.value = ""
            fetchNews(category = category, countryCode = countryCode)
        }
    }

    fun fetchNews(
        category: String = _selectedCategory.value,
        countryCode: String = "us",
        isRefresh: Boolean = false
    ) {
        val generation = ++fetchGeneration
        searchGeneration++
        _isRefreshing.value = isRefresh
        if (!isRefresh) {
            _uiState.value = NewsUiState.Loading
        }

        currentPage = 1
        isLastPage = false

        fetchJob?.cancel()
        searchJob?.cancel()
        pageJob?.cancel()

        fetchJob = scope.launch(Dispatchers.IO) {
            try {
                if (category == "bookmarks") {
                    val bookmarks = bookmarkedArticles.value
                    _uiState.value = NewsUiState.Success(bookmarks)
                    _isServingCached.value = false
                    _cachedAt.value = null
                    return@launch
                }

                if (category == "history") {
                    val history = historyArticles.value
                    _uiState.value = NewsUiState.Success(history)
                    _isServingCached.value = false
                    _cachedAt.value = null
                    return@launch
                }

                if (category == "for_you" && forYouKeywords.isNotBlank()) {
                    val searchResult = repository.searchNewsResult(query = forYouKeywords, page = 1)
                    _uiState.value = NewsUiState.Success(searchResult.articles)
                    _isServingCached.value = searchResult.fromCache
                    _cachedAt.value = searchResult.cachedAt
                    return@launch
                }

                val result = repository.getTopHeadlines(category, currentPage, countryCode)
                _isServingCached.value = result.fromCache
                _cachedAt.value = result.cachedAt
                _uiState.value = NewsUiState.Success(result.articles)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isServingCached.value = false
                _cachedAt.value = null
                _uiState.value = NewsUiState.Error(newsFeedErrorMessage(e))
            } finally {
                if (generation == fetchGeneration) _isRefreshing.value = false
            }
        }
    }

    fun loadMore(countryCode: String) {
        if (_isLoadingMore.value || isLastPage) return

        val categoryAtStart = _selectedCategory.value
        if (categoryAtStart == "bookmarks" || categoryAtStart == "history") return

        val pageToLoad = currentPage + 1
        _isLoadingMore.value = true

        pageJob?.cancel()
        pageJob = scope.launch(Dispatchers.IO) {
            try {
                val result = if (_searchQuery.value.isNotBlank()) {
                    repository.searchNewsResult(_searchQuery.value, pageToLoad)
                } else if (categoryAtStart == "for_you" && forYouKeywords.isNotBlank()) {
                    repository.searchNewsResult(forYouKeywords, pageToLoad)
                } else {
                    repository.getTopHeadlines(categoryAtStart, pageToLoad, countryCode)
                }
                val newArticles = result.articles
                _isServingCached.value = _isServingCached.value || result.fromCache
                if (result.cachedAt != null) _cachedAt.value = result.cachedAt

                if (newArticles.isEmpty()) {
                    isLastPage = true
                } else {
                    currentPage = pageToLoad
                    val currentList = (_uiState.value as? NewsUiState.Success)?.articles ?: emptyList()
                    val existingUrls = currentList.map { it.url }.toSet()
                    val filteredNew = newArticles.filter { !existingUrls.contains(it.url) }
                    _uiState.value = NewsUiState.Success(currentList + filteredNew)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorEvents.emit("Could not load more articles.")
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun searchNews(query: String, isRefresh: Boolean = false) {
        val generation = ++searchGeneration
        fetchGeneration++
        _searchQuery.value = query
        searchJob?.cancel()
        fetchJob?.cancel()
        pageJob?.cancel()

        if (query.isBlank()) {
            fetchNews(isRefresh = isRefresh)
            return
        }

        currentPage = 1
        isLastPage = false
        _isRefreshing.value = isRefresh
        if (!isRefresh) _uiState.value = NewsUiState.Loading

        searchJob = scope.launch(Dispatchers.IO) {
            delay(400)
            try {
                val result = repository.searchNewsResult(query, currentPage)
                _isServingCached.value = result.fromCache
                _cachedAt.value = result.cachedAt
                _uiState.value = NewsUiState.Success(result.articles)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _isServingCached.value = false
                _cachedAt.value = null
                _uiState.value = NewsUiState.Error(newsFeedErrorMessage(e))
            } finally {
                if (generation == searchGeneration) _isRefreshing.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleBookmark(article: Article) {
        scope.launch {
            if (isBookmarked(article)) {
                repository.deleteBookmark(article)
            } else {
                repository.saveBookmark(article)
            }
        }
    }

    fun isBookmarked(article: Article): Boolean {
        return bookmarkedArticles.value.any { it.url == article.url }
    }

    fun addToHistory(article: Article) {
        scope.launch {
            repository.addToHistory(article)
        }
    }

    fun openArticleFromDeepLink(url: String, countryCode: String) {
        _pendingArticleUrl.value = url
        val visible = (_uiState.value as? NewsUiState.Success)?.articles.orEmpty()
        if (visible.none { it.url == url }) {
            _selectedCategory.value = "general"
            _searchQuery.value = ""
            fetchNews(category = "general", countryCode = countryCode)
        }
    }

    fun clearPendingArticleUrl() {
        _pendingArticleUrl.value = null
    }

    fun clearCache() {
        scope.launch {
            repository.clearCache()
            _isServingCached.value = false
            _cachedAt.value = null
        }
    }

    fun clearHistory() {
        scope.launch {
            repository.clearHistory()
        }
    }

    fun setSuccessArticles(
        articles: List<Article>,
        fromCache: Boolean = false,
        cachedAt: Long? = null
    ) {
        _uiState.value = NewsUiState.Success(articles)
        _isServingCached.value = fromCache
        _cachedAt.value = cachedAt
    }

    fun setLoading() {
        _uiState.value = NewsUiState.Loading
    }

    fun cancelActiveJobs() {
        fetchJob?.cancel()
        searchJob?.cancel()
        pageJob?.cancel()
    }
}
