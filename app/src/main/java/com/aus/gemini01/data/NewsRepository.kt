package com.aus.gemini01.data

import android.os.SystemClock
import com.aus.gemini01.BuildConfig
import com.aus.gemini01.data.local.*
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.installations.FirebaseInstallations
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
private const val GDELT_PAGE_SIZE = 20
private const val GDELT_RESULT_LIMIT = 75
private const val GDELT_CACHE_TTL_MS = 5 * 60 * 1000L
private const val GDELT_MIN_REQUEST_INTERVAL_MS = 5_000L
private const val GDELT_RATE_LIMIT_COOLDOWN_MS = 60_000L

private object GdeltRequestThrottle {
    private val mutex = Mutex()
    private var lastRequestStartedAtMs = 0L
    private var blockedUntilMs = 0L

    suspend fun <T> execute(block: suspend () -> T): T {
        mutex.lock()
        try {
            val now = SystemClock.elapsedRealtime()
            val nextAllowedAt = maxOf(
                lastRequestStartedAtMs + GDELT_MIN_REQUEST_INTERVAL_MS,
                blockedUntilMs
            )
            val waitMs = nextAllowedAt - now
            if (waitMs > 0) delay(waitMs)
            lastRequestStartedAtMs = SystemClock.elapsedRealtime()
            return try {
                block()
            } catch (error: HttpException) {
                if (error.code() == 429) {
                    blockedUntilMs = SystemClock.elapsedRealtime() + GDELT_RATE_LIMIT_COOLDOWN_MS
                }
                throw error
            }
        } finally {
            mutex.unlock()
        }
    }
}

private fun createGdeltApiService(): GdeltNewsApiService {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl("https://api.gdeltproject.org/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GdeltNewsApiService::class.java)
}

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
 * Feed payload plus its origin. `fromCache = true` means a saved result replaced
 * a live response: either Room served a feed after a network failure or GDELT
 * search served a stale in-memory result after HTTP 429.
 */
data class FeedResult(
    val articles: List<Article>,
    val fromCache: Boolean,
    val cachedAt: Long? = null
)

/** Search results can fall back to an expired in-memory GDELT result after HTTP 429. */
private data class GdeltArticlesResult(
    val response: NewsResponse,
    val fromStaleCache: Boolean = false,
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
        // The proxy consumes each token to reject replayed requests, so obtain a
        // fresh limited-use token for every Retrofit request.
        httpClientBuilder.addInterceptor { chain ->
            val token = Tasks.await(
                FirebaseAppCheck.getInstance().getLimitedUseAppCheckToken(),
                30,
                TimeUnit.SECONDS
            ).token
            val installationId = Tasks.await(
                FirebaseInstallations.getInstance().id,
                30,
                TimeUnit.SECONDS
            )
            val request = chain.request().newBuilder()
                .header("X-Firebase-AppCheck", token)
                .header("X-Firebase-Installation-Id", installationId)
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
    // The default Spark path uses keyless GDELT metadata. Tests that inject the
    // NewsAPI service retain the original NewsAPI behavior.
    private val selectedNewsProvider = if (apiService != null) "newsapi" else BuildConfig.NEWS_PROVIDER
    private val apiService: NewsApiService by lazy {
        apiService ?: run {
            val proxyUrl = proxyUrlProvider()
            val baseUrl = if (proxyUrl.isNotBlank()) proxyUrl else "https://newsapi.org/"
            createNewsApiService(baseUrl, useAppCheck = proxyUrl.isNotBlank())
        }
    }
    private val gdeltApiService: GdeltNewsApiService by lazy { createGdeltApiService() }
    private val gdeltCacheMutex = Mutex()
    private val gdeltCache = mutableMapOf<String, Pair<Long, List<Article>>>()
    private val feedCacheMutex = Mutex()
    private var feedCacheGeneration = 0L

    private suspend fun getGdeltArticles(
        query: String,
        page: Int,
        allowStaleMemoryCacheOnRateLimit: Boolean = false
    ): GdeltArticlesResult {
        val generationAtStart = feedCacheMutex.withLock { feedCacheGeneration }
        val cachedSnapshot = gdeltCacheMutex.withLock {
            val now = System.currentTimeMillis()
            val cached = gdeltCache[query]
            if (cached != null && now - cached.first < GDELT_CACHE_TTL_MS) {
                return paginateGdeltArticles(cached.second, page)
            }
            cached
        }

        var fromStaleCache = false
        var staleCachedAt: Long? = null
        val allArticles = try {
            GdeltRequestThrottle.execute {
                gdeltApiService.articles(query = query, maxRecords = GDELT_RESULT_LIMIT)
                    .articles
                    .mapNotNull(GdeltArticle::toDomainArticle)
                    .distinctBy { it.url }
            }
        } catch (error: HttpException) {
            if (
                !allowStaleMemoryCacheOnRateLimit ||
                error.code() != 429 ||
                cachedSnapshot == null ||
                cachedSnapshot.second.isEmpty()
            ) {
                throw error
            }
            fromStaleCache = true
            staleCachedAt = cachedSnapshot.first
            cachedSnapshot.second
        }

        // A 429 fallback must keep its original timestamp, and an in-flight
        // fetch from before clearCache() must not repopulate memory cache.
        if (!fromStaleCache) {
            feedCacheMutex.withLock {
                if (feedCacheGeneration == generationAtStart) {
                    gdeltCacheMutex.withLock {
                        if (gdeltCache.size >= 32 && !gdeltCache.containsKey(query)) {
                            val oldestKey = gdeltCache.minByOrNull { it.value.first }?.key
                            if (oldestKey != null) gdeltCache.remove(oldestKey)
                        }
                        gdeltCache[query] = System.currentTimeMillis() to allArticles
                    }
                }
            }
        }

        return paginateGdeltArticles(
            allArticles = allArticles,
            page = page,
            fromStaleCache = fromStaleCache,
            cachedAt = staleCachedAt
        )
    }

    private fun paginateGdeltArticles(
        allArticles: List<Article>,
        page: Int,
        fromStaleCache: Boolean = false,
        cachedAt: Long? = null
    ): GdeltArticlesResult {
        val pageArticles = allArticles
            .drop((page - 1).coerceAtLeast(0) * GDELT_PAGE_SIZE)
            .take(GDELT_PAGE_SIZE)
        return GdeltArticlesResult(
            response = NewsResponse(totalResults = allArticles.size, articles = pageArticles),
            fromStaleCache = fromStaleCache,
            cachedAt = cachedAt
        )
    }

    suspend fun getTopHeadlines(
        category: String? = null,
        page: Int = 1,
        countryCode: String = "us",
        allowOfflineFallback: Boolean = true
    ): FeedResult {
        val effectiveCategory = category ?: "general"
        var isGdeltRequest = false
        val cacheGeneration = feedCacheMutex.withLock { feedCacheGeneration }

        return try {
            val proxyUrl = proxyUrlProvider()
            val isProxy = proxyUrl.isNotBlank()
            val isGdelt = !isProxy && selectedNewsProvider.equals("gdelt", ignoreCase = true)
            isGdeltRequest = isGdelt
            val apiKey = if (isProxy || isGdelt) null else apiKeyProvider()
            if (!isProxy && !isGdelt) {
                requireNewsApiKey(apiKey.orEmpty())
            }
            val response = if (isGdelt) {
                getGdeltArticles(
                    query = gdeltTopHeadlinesQuery(effectiveCategory, countryCode),
                    page = page
                ).response
            } else {
                apiService.getTopHeadlines(
                    country = countryCode,
                    category = effectiveCategory,
                    page = page,
                    apiKey = apiKey
                )
            }

            if (response.status == "error") {
                throw NewsProviderException(response.message ?: "News API error: ${response.code}")
            }

            val articles = response.articles.filter {
                it.title.isNotBlank() &&
                it.title != "[Removed]" &&
                it.url.isNotBlank() &&
                it.url != "https://removed.com"
            }.distinctBy { it.url }

            // Only cache the first page, and never replace a good cache with an
            // empty success payload (NewsAPI can return zero usable articles
            // after filtering [Removed] rows).
            if (shouldReplaceFeedCache(page, category, articles.size)) {
                val fetchedAt = System.currentTimeMillis()
                val cutoff = fetchedAt - CACHE_TTL_DAYS * 24 * 60 * 60 * 1000
                feedCacheMutex.withLock {
                    if (feedCacheGeneration == cacheGeneration) {
                        newsDao.replaceCachedArticles(
                            category = effectiveCategory,
                            country = countryCode,
                            articles = articles.mapIndexed { index, article ->
                                article.toCachedEntity(
                                    category = effectiveCategory,
                                    country = countryCode,
                                    cachedAt = fetchedAt,
                                    feedOrder = index
                                )
                            },
                            cutoffEpochMs = cutoff
                        )
                    }
                }
            }

            FeedResult(articles, fromCache = false)
        } catch (e: CancellationException) {
            // A cancelled request must not fall back to cache - it would let a stale
            // category overwrite the one the user actually navigated to.
            throw e
        } catch (e: Exception) {
            val isGdeltRateLimit = isGdeltRequest && e is HttpException && e.code() == 429
            if (!allowOfflineFallback || (!isGdeltRateLimit && !shouldOfflineFallbackOnError(e))) throw e
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

    suspend fun searchNews(query: String, page: Int = 1): List<Article> =
        searchNewsResult(query, page).articles

    /** Searches across multiple comma-separated interest topics using boolean OR semantics. */
    suspend fun searchForYouResult(rawKeywords: String, page: Int = 1): FeedResult {
        val proxyUrl = proxyUrlProvider()
        val isProxy = proxyUrl.isNotBlank()
        val isGdelt = !isProxy && selectedNewsProvider.equals("gdelt", ignoreCase = true)
        val effectiveQuery = if (isGdelt) rawKeywords else formatMultiTopicSearchQuery(rawKeywords)
        return searchNewsResult(effectiveQuery, page)
    }

    suspend fun searchNewsResult(query: String, page: Int = 1): FeedResult {
        val proxyUrl = proxyUrlProvider()
        val isProxy = proxyUrl.isNotBlank()
        val isGdelt = !isProxy && selectedNewsProvider.equals("gdelt", ignoreCase = true)
        val apiKey = if (isProxy || isGdelt) null else apiKeyProvider()
        if (!isProxy && !isGdelt) {
            requireNewsApiKey(apiKey.orEmpty())
        }
        var gdeltResult: GdeltArticlesResult? = null
        val response = if (isGdelt) {
            getGdeltArticles(
                query = gdeltSearchQuery(query, countryCode = "us"),
                page = page,
                allowStaleMemoryCacheOnRateLimit = true
            ).also { gdeltResult = it }.response
        } else {
            apiService.searchNews(query = query, page = page, apiKey = apiKey)
        }
        if (response.status == "error") {
            throw NewsProviderException(response.message ?: "News API error: ${response.code}")
        }
        val articles = response.articles.filter {
            it.title.isNotBlank() &&
            it.title != "[Removed]" &&
            it.url.isNotBlank() &&
            it.url != "https://removed.com"
        }.distinctBy { it.url }
        return FeedResult(
            articles = articles,
            fromCache = gdeltResult?.fromStaleCache == true,
            cachedAt = gdeltResult?.cachedAt
        )
    }

    // Local Bookmark Methods
    fun getAllBookmarks(): Flow<List<Article>> {
        return newsDao.getAllBookmarks().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveBookmark(article: Article) {
        newsDao.insertBookmark(article.toEntity(savedAt = System.currentTimeMillis()))
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
        feedCacheMutex.withLock {
            feedCacheGeneration++
            gdeltCacheMutex.withLock { gdeltCache.clear() }
            newsDao.clearCache()
        }
    }

    suspend fun clearHistory() {
        newsDao.clearHistory()
    }
}
