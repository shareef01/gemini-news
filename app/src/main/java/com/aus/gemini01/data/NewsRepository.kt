package com.aus.gemini01.data

import com.aus.gemini01.BuildConfig
import com.aus.gemini01.data.local.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import java.io.IOException
import retrofit2.HttpException

// Cached articles older than this are pruned on every fresh fetch, so the cache
// doesn't accumulate stale news indefinitely.
private const val CACHE_TTL_DAYS = 7L

/** Thrown when BuildConfig was built without NEWS_API_KEY (empty local.properties / env). */
class MissingNewsApiKeyException :
    IOException("NEWS_API_KEY is not configured. Add it to local.properties (or the environment) and rebuild.")

/**
 * NewsAPI application-level failure (`status: "error"`). Not a transport outage —
 * must not be disguised as a successful offline-cache hit.
 */
class NewsProviderException(message: String) : Exception(message)

internal fun requireNewsApiKey(apiKey: String = BuildConfig.NEWS_API_KEY) {
    if (apiKey.isBlank()) throw MissingNewsApiKeyException()
}

/** Whether a successful network response should replace the Room feed cache. */
internal fun shouldReplaceFeedCache(
    page: Int,
    category: String?,
    articleCount: Int
): Boolean = page == 1 && category != "bookmarks" && articleCount > 0

/**
 * Explicit taxonomy for when page-1 Room cache may be returned after a failed
 * live fetch. Cancellation is never handled here — callers rethrow it first.
 *
 * Eligible for stale fallback:
 * - HTTP 5xx
 * - Genuine network [IOException] (timeouts, DNS, socket) except missing API key
 *
 * Not eligible:
 * - HTTP 4xx (including 429)
 * - Missing / blank NewsAPI credential
 * - Provider application-level `status: "error"`
 * - Local persistence / programmer / serialization failures
 */
internal fun shouldOfflineFallbackOnError(error: Throwable): Boolean = when (error) {
    is CancellationException -> false
    is MissingNewsApiKeyException -> false
    is NewsProviderException -> false
    is SerializationException -> false
    is HttpException -> error.code() !in 400..499
    is IOException -> true
    else -> false
}

/**
 * Feed payload plus its origin. `fromCache = true` means the network failed and
 * Room served the last successful fetch, so the UI can tell the user they are
 * reading saved (possibly stale) stories instead of failing silently.
 */
data class FeedResult(
    val articles: List<Article>,
    val fromCache: Boolean,
    val cachedAt: Long? = null
)

private fun createNewsApiService(
    baseUrl: String = "https://newsapi.org/",
    useAppCheck: Boolean = false
): NewsApiService {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    val httpClientBuilder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // Never forward an attestation token through an HTTP redirect to a
        // different origin. A proxy redirect is treated as a failed response.
        .followRedirects(!useAppCheck)
        .followSslRedirects(!useAppCheck)
    if (useAppCheck) {
        // Retrofit's OkHttp calls run on its dispatcher threads. Obtain a valid
        // cached/refreshed App Check token there and fail closed if attestation
        // cannot be produced; the proxy requires this header.
        httpClientBuilder.addInterceptor { chain ->
            val token = Tasks.await(
                FirebaseAppCheck.getInstance().getAppCheckToken(false),
                30,
                TimeUnit.SECONDS
            ).token
            val request = chain.request().newBuilder()
                .header("X-Firebase-AppCheck", token)
                .build()
            chain.proceed(request)
        }
    }
    val httpClient = httpClientBuilder.build()
    val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return Retrofit.Builder()
        .baseUrl(normalizedUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NewsApiService::class.java)
}

class NewsRepository(
    private val newsDao: NewsDao,
    apiService: NewsApiService? = null,
    private val proxyUrlProvider: () -> String = { BuildConfig.NEWS_PROXY_BASE_URL },
    private val apiKeyProvider: () -> String = { BuildConfig.NEWS_API_KEY }
) {
    // Production can route through a secure backend proxy or direct Retrofit;
    // tests can provide a deterministic service and key without network calls or credential state.
    private val apiService: NewsApiService by lazy {
        apiService ?: run {
            val proxyUrl = proxyUrlProvider()
            val baseUrl = if (proxyUrl.isNotBlank()) proxyUrl else "https://newsapi.org/"
            createNewsApiService(baseUrl, useAppCheck = proxyUrl.isNotBlank())
        }
    }

    suspend fun getTopHeadlines(
        category: String? = null,
        page: Int = 1,
        countryCode: String = "us",
        allowOfflineFallback: Boolean = true
    ): FeedResult {
        val effectiveCategory = category ?: "general"

        return try {
            val proxyUrl = proxyUrlProvider()
            val isProxy = proxyUrl.isNotBlank()
            val apiKey = if (isProxy) null else apiKeyProvider()
            if (!isProxy) {
                requireNewsApiKey(apiKey.orEmpty())
            }
            val response = apiService.getTopHeadlines(
                country = countryCode,
                category = effectiveCategory,
                page = page,
                apiKey = apiKey
            )

            if (response.status == "error") {
                throw NewsProviderException(response.message ?: "News API error: ${response.code}")
            }

            val articles = response.articles.filter {
                it.title.isNotBlank() &&
                it.title != "[Removed]" &&
                it.url.isNotBlank() &&
                it.url != "https://removed.com"
            }

            // Only cache the first page, and never replace a good cache with an
            // empty success payload (NewsAPI can return zero usable articles
            // after filtering [Removed] rows).
            if (shouldReplaceFeedCache(page, category, articles.size)) {
                val fetchedAt = System.currentTimeMillis()
                val cutoff = fetchedAt - CACHE_TTL_DAYS * 24 * 60 * 60 * 1000
                newsDao.replaceCachedArticles(
                    category = effectiveCategory,
                    country = countryCode,
                    articles = articles.map {
                        it.toCachedEntity(
                            category = effectiveCategory,
                            country = countryCode,
                            cachedAt = fetchedAt
                        )
                    },
                    cutoffEpochMs = cutoff
                )
            }

            FeedResult(articles, fromCache = false)
        } catch (e: CancellationException) {
            // A cancelled request must not fall back to cache - it would let a stale
            // category overwrite the one the user actually navigated to.
            throw e
        } catch (e: Exception) {
            if (!allowOfflineFallback || !shouldOfflineFallbackOnError(e)) throw e
            // Offline / 5xx fallback: serve the cached feed for the requested region and category.
            // If there is nothing cached for this region, surface the real error instead of fabricating articles.
            if (page == 1) {
                val cached = newsDao.getCachedArticles(effectiveCategory, countryCode)
                if (cached.isNotEmpty()) {
                    FeedResult(
                        articles = cached.map { it.toDomain() },
                        fromCache = true,
                        cachedAt = newsDao.getOldestCachedAt(effectiveCategory, countryCode)
                    )
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun searchNews(query: String, page: Int = 1): List<Article> {
        val proxyUrl = proxyUrlProvider()
        val isProxy = proxyUrl.isNotBlank()
        val apiKey = if (isProxy) null else apiKeyProvider()
        if (!isProxy) {
            requireNewsApiKey(apiKey.orEmpty())
        }
        val response = apiService.searchNews(query = query, page = page, apiKey = apiKey)
        if (response.status == "error") {
            throw NewsProviderException(response.message ?: "News API error: ${response.code}")
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
        newsDao.insertAndTrimHistory(article.toHistoryEntity(timestamp), 50)
    }

    suspend fun clearCache() {
        newsDao.clearCache()
    }

    suspend fun clearHistory() {
        newsDao.clearHistory()
    }
}
