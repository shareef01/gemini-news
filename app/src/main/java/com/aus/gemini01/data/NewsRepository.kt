package com.aus.gemini01.data

import com.aus.gemini01.BuildConfig
import com.aus.gemini01.data.local.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import java.io.IOException

// Cached articles older than this are pruned on every fresh fetch, so the cache
// doesn't accumulate stale news indefinitely.
private const val CACHE_TTL_DAYS = 7L

class NewsRepository(private val newsDao: NewsDao) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val apiService: NewsApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://newsapi.org/")
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NewsApiService::class.java)
    }

    suspend fun getTopHeadlines(
        category: String? = null,
        page: Int = 1,
        countryCode: String = "us",
        allowOfflineFallback: Boolean = true
    ): List<Article> {
        val effectiveCategory = category ?: "general"

        return try {
            val response = apiService.getTopHeadlines(
                country = countryCode,
                category = effectiveCategory,
                page = page,
                apiKey = BuildConfig.NEWS_API_KEY
            )

            if (response.status == "error") {
                throw IOException(response.message ?: "News API error: ${response.code}")
            }

            val articles = response.articles.filter {
                it.title.isNotBlank() &&
                it.title != "[Removed]" &&
                it.url.isNotBlank() &&
                it.url != "https://removed.com"
            }

            // Only cache the first page
            if (page == 1 && category != "bookmarks") {
                val cutoff = Instant.now().minusSeconds(CACHE_TTL_DAYS * 24 * 3600).toString()
                newsDao.deleteCachedArticlesOlderThan(cutoff)
                newsDao.deleteCachedArticlesByCategory(effectiveCategory)
                newsDao.insertCachedArticles(articles.map { it.toCachedEntity(effectiveCategory) })
            }

            articles
        } catch (e: CancellationException) {
            // A cancelled request must not fall back to cache - it would let a stale
            // category overwrite the one the user actually navigated to.
            throw e
        } catch (e: Exception) {
            if (!allowOfflineFallback) throw e
            // Offline fallback: serve the cached feed. If there is nothing cached,
            // surface the real error instead of fabricating articles.
            if (page == 1) {
                val cached = newsDao.getCachedArticles(effectiveCategory)
                if (cached.isNotEmpty()) {
                    cached.map { it.toDomain() }
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun searchNews(query: String, page: Int = 1): List<Article> {
        val response = apiService.searchNews(query = query, page = page, apiKey = BuildConfig.NEWS_API_KEY)
        if (response.status == "error") {
            throw IOException(response.message ?: "News API error: ${response.code}")
        }
        return response.articles.filter {
            it.title.isNotBlank() &&
            it.title != "[Removed]" &&
            it.url.isNotBlank() &&
            it.url != "https://removed.com"
        }
    }

    // Local Bookmark Methods
    fun getAllBookmarks(): Flow<List<Article>> {
        return newsDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveBookmark(article: Article) {
        newsDao.insertBookmark(article.toEntity())
    }

    suspend fun deleteBookmark(article: Article) {
        newsDao.deleteBookmark(article.toEntity())
    }

    // History Methods
    fun getAllHistory(): Flow<List<Article>> {
        return newsDao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun addToHistory(article: Article) {
        val timestamp = System.currentTimeMillis()
        newsDao.insertHistory(article.toHistoryEntity(timestamp))
        newsDao.trimHistory(50) // Keep only the last 50 entries
    }

    suspend fun clearCache() {
        newsDao.clearCache()
    }

    suspend fun clearHistory() {
        newsDao.clearHistory()
    }
}
