package com.aus.gemini01.data

import com.aus.gemini01.data.local.ArticleEntity
import com.aus.gemini01.data.local.CachedArticleEntity
import com.aus.gemini01.data.local.HistoryArticleEntity
import com.aus.gemini01.data.local.NewsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

private class FakeNewsApiService(
    private val response: NewsResponse
) : NewsApiService {
    var lastQuery: String? = null
    var lastApiKey: String? = null

    override suspend fun getTopHeadlines(
        country: String,
        category: String?,
        page: Int,
        pageSize: Int,
        apiKey: String
    ): NewsResponse = response

    override suspend fun searchNews(
        query: String,
        page: Int,
        pageSize: Int,
        apiKey: String
    ): NewsResponse {
        lastQuery = query
        lastApiKey = apiKey
        return response
    }
}

private class NoOpNewsDao : NewsDao {
    override fun getAllBookmarks(): Flow<List<ArticleEntity>> = emptyFlow()
    override suspend fun insertBookmark(article: ArticleEntity) = Unit
    override suspend fun deleteBookmark(article: ArticleEntity) = Unit
    override suspend fun getCachedArticles(category: String): List<CachedArticleEntity> = emptyList()
    override suspend fun insertCachedArticles(articles: List<CachedArticleEntity>) = Unit
    override suspend fun deleteCachedArticlesByCategory(category: String) = Unit
    override suspend fun getOldestCachedAt(category: String): Long? = null
    override suspend fun deleteCachedArticlesOlderThan(cutoffEpochMs: Long) = Unit
    override suspend fun replaceCachedArticles(
        category: String,
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

class NewsRepositoryTest {

    @Test
    fun `search injects service and filters unusable NewsAPI rows`() = kotlinx.coroutines.runBlocking {
        val valid = Article(title = "Valid headline", url = "https://example.com/valid")
        val service = FakeNewsApiService(
            NewsResponse(
                articles = listOf(
                    valid,
                    Article(title = "[Removed]", url = "https://removed.com"),
                    Article(title = "Missing URL"),
                    Article(url = "https://example.com/missing-title")
                )
            )
        )
        val repository = NewsRepository(NoOpNewsDao(), service) { "test-key" }

        assertEquals(listOf(valid), repository.searchNews("climate"))
        assertEquals("climate", service.lastQuery)
        assertEquals("test-key", service.lastApiKey)
    }

    @Test
    fun `search surfaces API error without treating it as a successful payload`() = kotlinx.coroutines.runBlocking {
        val service = FakeNewsApiService(
            NewsResponse(status = "error", code = "apiKeyInvalid", message = "sensitive provider detail")
        )
        val repository = NewsRepository(NoOpNewsDao(), service) { "test-key" }

        try {
            repository.searchNews("climate")
            fail("Expected provider error")
        } catch (error: java.io.IOException) {
            assertFalse(newsFeedErrorMessage(error).contains("sensitive provider detail"))
        }
    }
}
