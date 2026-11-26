package com.aus.gemini01.data

import com.aus.gemini01.data.local.ArticleEntity
import com.aus.gemini01.data.local.CachedArticleEntity
import com.aus.gemini01.data.local.HistoryArticleEntity
import com.aus.gemini01.data.local.NewsDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private class FakeNewsApiService(
    private val responseProvider: suspend () -> NewsResponse
) : NewsApiService {
    var lastQuery: String? = null
    var lastApiKey: String? = null
    var headlineCalls = 0

    constructor(response: NewsResponse) : this({ response })

    override suspend fun getTopHeadlines(
        country: String,
        category: String?,
        page: Int,
        pageSize: Int,
        apiKey: String?
    ): NewsResponse {
        headlineCalls++
        lastApiKey = apiKey
        return responseProvider()
    }

    override suspend fun searchNews(
        query: String,
        page: Int,
        pageSize: Int,
        apiKey: String?
    ): NewsResponse {
        lastQuery = query
        lastApiKey = apiKey
        return responseProvider()
    }
}

private class RecordingNewsDao(
    private val cached: MutableList<CachedArticleEntity> = mutableListOf()
) : NewsDao {
    override fun getAllBookmarks(): Flow<List<ArticleEntity>> = emptyFlow()
    override suspend fun insertBookmark(article: ArticleEntity) = Unit
    override suspend fun deleteBookmark(article: ArticleEntity) = Unit
    override suspend fun getCachedArticles(category: String, country: String): List<CachedArticleEntity> =
        cached.filter { it.category == category && it.country == country }
    override suspend fun insertCachedArticles(articles: List<CachedArticleEntity>) {
        cached.addAll(articles)
    }
    override suspend fun deleteCachedArticlesByCategory(category: String, country: String) {
        cached.removeAll { it.category == category && it.country == country }
    }
    override suspend fun getOldestCachedAt(category: String, country: String): Long? =
        cached.filter { it.category == category && it.country == country }.minOfOrNull { it.cachedAt }
    override suspend fun deleteCachedArticlesOlderThan(cutoffEpochMs: Long) {
        cached.removeAll { it.cachedAt < cutoffEpochMs }
    }
    override suspend fun replaceCachedArticles(
        category: String,
        country: String,
        articles: List<CachedArticleEntity>,
        cutoffEpochMs: Long
    ) {
        cached.removeAll { (it.category == category && it.country == country) || it.cachedAt < cutoffEpochMs }
        cached.addAll(articles)
    }
    override fun getAllHistory(): Flow<List<HistoryArticleEntity>> = emptyFlow()
    override suspend fun insertHistory(article: HistoryArticleEntity) = Unit
    override suspend fun trimHistory(limit: Int) = Unit
    override suspend fun insertAndTrimHistory(article: HistoryArticleEntity, limit: Int) = Unit
    override suspend fun clearCache() {
        cached.clear()
    }
    override suspend fun clearHistory() = Unit
}

private fun cachedArticle(
    url: String = "https://example.com/cached",
    title: String = "Cached",
    category: String = "general",
    country: String = "us",
    cachedAt: Long = System.currentTimeMillis()
) = CachedArticleEntity(
    url = url,
    category = category,
    country = country,
    sourceName = "Test",
    author = null,
    title = title,
    description = null,
    urlToImage = null,
    publishedAt = "2026-09-01T00:00:00Z",
    content = null,
    cachedAt = cachedAt
)

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
        val repository = NewsRepository(RecordingNewsDao(), service) { "test-key" }

        assertEquals(listOf(valid), repository.searchNews("climate"))
        assertEquals("climate", service.lastQuery)
        assertEquals("test-key", service.lastApiKey)
    }

    @Test
    fun `search surfaces API error without treating it as a successful payload`() = kotlinx.coroutines.runBlocking {
        val service = FakeNewsApiService(
            NewsResponse(status = "error", code = "apiKeyInvalid", message = "sensitive provider detail")
        )
        val repository = NewsRepository(RecordingNewsDao(), service) { "test-key" }

        try {
            repository.searchNews("climate")
            fail("Expected provider error")
        } catch (error: NewsProviderException) {
            assertFalse(newsFeedErrorMessage(error).contains("sensitive provider detail"))
        }
    }

    @Test
    fun `http_4xx_does_not_return_stale_cache_as_success`() = kotlinx.coroutines.runBlocking {
        val dao = RecordingNewsDao(mutableListOf(cachedArticle()))
        val service = FakeNewsApiService {
            throw HttpException(Response.error<Unit>(429, "".toResponseBody(null)))
        }
        val repository = NewsRepository(dao, service) { "test-key" }

        try {
            repository.getTopHeadlines(allowOfflineFallback = true)
            fail("Expected HttpException")
        } catch (_: HttpException) {
        }
    }

    @Test
    fun `http_5xx_may_return_stale_cache`() = kotlinx.coroutines.runBlocking {
        val dao = RecordingNewsDao(mutableListOf(cachedArticle(cachedAt = 123L)))
        val service = FakeNewsApiService {
            throw HttpException(Response.error<Unit>(503, "".toResponseBody(null)))
        }
        val repository = NewsRepository(dao, service) { "test-key" }

        val result = repository.getTopHeadlines(allowOfflineFallback = true)
        assertTrue(result.fromCache)
        assertEquals(1, result.articles.size)
        assertEquals("https://example.com/cached", result.articles[0].url)
        assertEquals(123L, result.cachedAt)
    }

    @Test
    fun `network_io_may_return_stale_cache`() = kotlinx.coroutines.runBlocking {
        val dao = RecordingNewsDao(mutableListOf(cachedArticle(cachedAt = 1L)))
        val service = FakeNewsApiService { throw java.io.IOException("offline") }
        val repository = NewsRepository(dao, service) { "test-key" }

        val result = repository.getTopHeadlines(allowOfflineFallback = true)
        assertTrue(result.fromCache)
    }

    @Test
    fun `local_runtime_failure_is_not_mislabeled_offline`() = kotlinx.coroutines.runBlocking {
        val dao = object : NewsDao by RecordingNewsDao() {
            override suspend fun getCachedArticles(category: String, country: String): List<CachedArticleEntity> {
                throw IllegalStateException("room exploded")
            }
        }
        val service = FakeNewsApiService { throw IllegalStateException("unexpected local") }
        val repository = NewsRepository(dao, service) { "test-key" }

        try {
            repository.getTopHeadlines(allowOfflineFallback = true)
            fail("Expected IllegalStateException")
        } catch (error: IllegalStateException) {
            assertEquals("unexpected local", error.message)
        }
    }

    @Test
    fun `provider_status_error_does_not_use_stale_cache`() = kotlinx.coroutines.runBlocking {
        val dao = RecordingNewsDao(mutableListOf(cachedArticle(cachedAt = 1L)))
        val service = FakeNewsApiService(
            NewsResponse(status = "error", code = "rateLimited", message = "nope")
        )
        val repository = NewsRepository(dao, service) { "test-key" }

        try {
            repository.getTopHeadlines(allowOfflineFallback = true)
            fail("Expected NewsProviderException")
        } catch (_: NewsProviderException) {
        }
    }

    @Test
    fun `multi_region_cache_isolation_prevents_cross_country_leakage`() = kotlinx.coroutines.runBlocking {
        // US cache is populated
        val usArticle = cachedArticle(url = "https://example.com/us-news", title = "US Story", country = "us", cachedAt = 100L)
        val dao = RecordingNewsDao(mutableListOf(usArticle))
        val service = FakeNewsApiService { throw java.io.IOException("No internet connection") }
        val repository = NewsRepository(dao, service) { "test-key" }

        // Requesting US feed offline returns the cached US article
        val usResult = repository.getTopHeadlines(countryCode = "us", allowOfflineFallback = true)
        assertTrue(usResult.fromCache)
        assertEquals("US Story", usResult.articles.first().title)

        // Requesting GB feed offline does not leak US articles - it fails because GB has no cache
        try {
            repository.getTopHeadlines(countryCode = "gb", allowOfflineFallback = true)
            fail("Expected IOException because GB has no cached articles")
        } catch (e: java.io.IOException) {
            assertEquals("No internet connection", e.message)
        }
    }

    @Test
    fun `proxy_mode_omits_api_key_and_allows_blank_client_credential`() = kotlinx.coroutines.runBlocking {
        val dao = RecordingNewsDao()
        val service = FakeNewsApiService(
            NewsResponse(status = "ok", totalResults = 1, articles = listOf(
                Article(
                    source = Source("ProxySource"),
                    author = "Author",
                    title = "Story from Proxy",
                    description = "Description",
                    url = "https://example.com/proxy-story",
                    urlToImage = null,
                    publishedAt = "2026-09-01T00:00:00Z",
                    content = null
                )
            ))
        )
        // In proxy mode, client has no api key (blank) and specifies proxy URL
        val repository = NewsRepository(
            newsDao = dao,
            apiService = service,
            apiKeyProvider = { "" },
            proxyUrlProvider = { "https://news-proxy.example.com/" }
        )

        val result = repository.getTopHeadlines(allowOfflineFallback = false)
        assertFalse(result.fromCache)
        assertEquals("Story from Proxy", result.articles.first().title)
        // Verify api key was not sent
        assertEquals(null, service.lastApiKey)
    }

    @Test
    fun `duplicate_article_urls_are_deduplicated_in_headlines_and_search`() = kotlinx.coroutines.runBlocking {
        val first = Article(title = "First Copy", url = "https://example.com/dup")
        val second = Article(title = "Second Copy", url = "https://example.com/dup")
        val distinct = Article(title = "Distinct", url = "https://example.com/unique")
        val service = FakeNewsApiService(NewsResponse(articles = listOf(first, second, distinct)))
        val repository = NewsRepository(RecordingNewsDao(), service) { "test-key" }

        val headlineArticles = repository.getTopHeadlines(allowOfflineFallback = false).articles
        assertEquals(listOf(first, distinct), headlineArticles)

        val searchArticles = repository.searchNews("dup")
        assertEquals(listOf(first, distinct), searchArticles)
    }

    @Test
    fun `searchForYouResult_formats_comma_topics_into_OR_query_for_newsapi`() = kotlinx.coroutines.runBlocking {
        val service = FakeNewsApiService(NewsResponse(articles = emptyList()))
        val repository = NewsRepository(RecordingNewsDao(), service) { "test-key" }

        repository.searchForYouResult("Space Exploration, AI, Electric Vehicles")
        assertEquals("\"Space Exploration\" OR AI OR \"Electric Vehicles\"", service.lastQuery)
    }

    @Test
    fun `gdeltSearchQuery_supports_short_terms_and_comma_separated_OR_clauses`() {
        assertEquals("AI sourcecountry:unitedstates", gdeltSearchQuery("AI", "us"))
        assertEquals(
            "(\"Space Exploration\" OR AI OR \"Electric Vehicles\") sourcecountry:unitedstates",
            gdeltSearchQuery("Space Exploration, AI, Electric Vehicles", "us")
        )
        assertEquals("climate policy sourcecountry:unitedstates", gdeltSearchQuery("climate OR policy", "us"))
    }

    @Test
    fun `gdeltArticle_upgrades_http_and_rejects_unsafe_urls`() {
        val upgraded = GdeltArticle(url = "http://example.com/story", title = "Headline").toDomainArticle()
        assertEquals("https://example.com/story", upgraded?.url)

        val rejected = GdeltArticle(url = "javascript:alert(1)", title = "Bad").toDomainArticle()
        assertEquals(null, rejected)
    }
}
