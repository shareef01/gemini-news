package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.NewsApiService
import com.aus.gemini01.data.NewsRepository
import com.aus.gemini01.data.NewsResponse
import com.aus.gemini01.data.local.ArticleEntity
import com.aus.gemini01.data.local.CachedArticleEntity
import com.aus.gemini01.data.local.HistoryArticleEntity
import com.aus.gemini01.data.local.NewsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private class StubFeedDao : NewsDao {
    override fun getAllBookmarks(): Flow<List<ArticleEntity>> = emptyFlow()
    override suspend fun insertBookmark(article: ArticleEntity) = Unit
    override suspend fun deleteBookmark(article: ArticleEntity) = Unit
    override suspend fun getCachedArticles(category: String, country: String): List<CachedArticleEntity> = emptyList()
    override suspend fun insertCachedArticles(articles: List<CachedArticleEntity>) = Unit
    override suspend fun deleteCachedArticlesByCategory(category: String, country: String) = Unit
    override suspend fun getOldestCachedAt(category: String, country: String): Long? = null
    override suspend fun deleteCachedArticlesOlderThan(cutoffEpochMs: Long) = Unit
    override suspend fun replaceCachedArticles(
        category: String,
        country: String,
        articles: List<CachedArticleEntity>,
        cutoffEpochMs: Long
    ) = Unit
    override fun getAllHistory(): Flow<List<HistoryArticleEntity>> = emptyFlow()
    override suspend fun insertHistory(article: HistoryArticleEntity) = Unit
    override suspend fun trimHistory(limit: Int) = Unit
    override suspend fun insertAndTrimHistory(article: HistoryArticleEntity, limit: Int) = Unit
    override suspend fun clearCache() = Unit
    override suspend fun clearHistory() = Unit
}

class FeedManagerTest {

    @Test
    fun `loadMore skips smart themes category and partial first page`() = runBlocking {
        val calls = AtomicInteger(0)
        val service = object : NewsApiService {
            override suspend fun getTopHeadlines(
                country: String,
                category: String?,
                page: Int,
                pageSize: Int,
                apiKey: String?
            ): NewsResponse {
                calls.incrementAndGet()
                return NewsResponse(
                    articles = listOf(Article(title = "Only one", url = "https://example.com/1"))
                )
            }

            override suspend fun searchNews(
                query: String,
                page: Int,
                pageSize: Int,
                apiKey: String?
            ): NewsResponse = NewsResponse(articles = emptyList())
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = NewsRepository(StubFeedDao(), service) { "test-key" }
        val manager = FeedManager(repo, scope, MutableSharedFlow(extraBufferCapacity = 4))

        // Select smart category and populate a small theme bucket (< 20 items).
        manager.selectCategory("smart", "us")
        delay(50)
        val callsAfterInitial = calls.get()
        manager.setSuccessArticles(listOf(Article(title = "Theme Story", url = "https://example.com/t1")))

        // Attempting to paginate smart themes must never hit the network or append general stories.
        manager.loadMore("us")
        delay(50)
        assertEquals(callsAfterInitial, calls.get())
        val currentArticles = (manager.uiState.value as NewsUiState.Success).articles
        assertEquals(1, currentArticles.size)
        assertEquals("Theme Story", currentArticles.first().title)

        scope.cancel()
    }
}
