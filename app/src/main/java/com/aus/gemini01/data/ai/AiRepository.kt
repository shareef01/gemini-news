package com.aus.gemini01.data.ai

import com.aus.gemini01.data.local.AiResultDao
import com.aus.gemini01.data.local.AiResultEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.ConcurrentHashMap

/**
 * Single gateway for every Gemini call. Guarantees, per cache key:
 *  1. Room-persisted results are reused across sessions (the biggest quota saver).
 *  2. Concurrent callers share one in-flight request instead of duplicating it.
 *  3. Failures are never cached, never retried automatically (a 429 must not
 *     be hammered), and are surfaced as classified [AiError]s.
 *
 * UI cancellation abandons rather than kills the underlying request: a
 * late-arriving success still lands in the cache, so a re-tap is free.
 */
class AiRepository(
    private val dao: AiResultDao,
    private val telemetry: AiTelemetry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val inFlight = ConcurrentHashMap<String, Deferred<String>>()

    suspend fun cachedOrFetch(
        cacheKey: String,
        feature: AiFeature,
        articleUrl: String? = null,
        fetch: suspend () -> String
    ): String {
        dao.get(cacheKey)?.let { cached ->
            val ageMs = System.currentTimeMillis() - cached.createdAt
            val ttl = feature.cacheTtlMs
            if (ttl <= 0L || ageMs <= ttl) {
                telemetry.recordCacheHit(feature)
                return cached.result
            }
            // Expired — fall through to regenerate. Stale row is overwritten on insert.
        }
        val deferred = synchronized(inFlight) {
            inFlight[cacheKey] ?: scope.async {
                try {
                    // Another caller may have finished and written the cache while
                    // we were waiting to enter the single-flight map — reuse it.
                    dao.get(cacheKey)?.let { cached ->
                        val ageMs = System.currentTimeMillis() - cached.createdAt
                        val ttl = feature.cacheTtlMs
                        if (ttl <= 0L || ageMs <= ttl) {
                            telemetry.recordCacheHit(feature)
                            return@async cached.result
                        }
                    }
                    // Only the single-flight owner records a miss and prunes;
                    // concurrent waiters must not inflate diagnostics or race
                    // on a non-thread-safe telemetry implementation.
                    telemetry.recordCacheMiss(feature)
                    pruneExpired(feature)
                    telemetry.recordRequest(feature)
                    val result = fetch()
                    dao.insert(
                        AiResultEntity(
                            cacheKey = cacheKey,
                            kind = feature.id,
                            articleUrl = articleUrl,
                            result = result,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    result
                } finally {
                    synchronized(inFlight) { inFlight.remove(cacheKey) }
                }
            }.also { inFlight[cacheKey] = it }
        }

        return try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val aiError = e.toAiError()
            telemetry.recordError(aiError)
            throw AiRequestException(aiError, e)
        }
    }

    private suspend fun pruneExpired(feature: AiFeature) {
        val ttl = feature.cacheTtlMs
        if (ttl <= 0L) return
        // Use the shortest TTL among time-sensitive kinds so trends/locations
        // don't linger when a longer-lived feature triggers a miss.
        val cutoff = System.currentTimeMillis() - minOf(
            AiFeature.TRENDS.cacheTtlMs,
            AiFeature.LOCATIONS.cacheTtlMs,
            AiFeature.FOR_YOU.cacheTtlMs
        )
        runCatching { dao.deleteOlderThan(cutoff) }
    }

    /**
     * Chat is user-specific and conversational - it must never be cached.
     * Counted here anyway so diagnostics cover 100% of Gemini usage.
     */
    suspend fun recordUncachedRequest(feature: AiFeature, block: suspend () -> String): String {
        telemetry.recordRequest(feature)
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val aiError = e.toAiError()
            telemetry.recordError(aiError)
            throw AiRequestException(aiError, e)
        }
    }

    /** Cached-result counts per feature, joined with request/error counters. */
    val diagnostics: Flow<AiDiagnostics> =
        combine(telemetry.counters, dao.countsByKind()) { counters, cachedCounts ->
            counters.copy(
                // Keep AiDiagnostics single-source; cached counts ride along via
                // the same map shape under a synthetic key prefix.
                requestsByFeature = counters.requestsByFeature +
                    cachedCounts.associate { "cached_${it.kind}" to it.count }
            )
        }

    suspend fun clearCache() {
        dao.clearAll()
    }
}
