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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single gateway for every Gemini call. Guarantees, per cache key:
 *  1. Room-persisted results are reused across sessions (the biggest quota saver).
 *  2. Concurrent callers share one in-flight request instead of duplicating it.
 *  3. Failures are never cached, never retried automatically (a 429 must not
 *     be hammered), and are surfaced as classified [AiError]s.
 *  4. Explicit [clearCache] advances a generation barrier so late successes from
 *     prior generations cannot repopulate Room.
 *
 * UI cancellation abandons rather than kills the underlying request: a
 * late-arriving success still lands in the cache (unless cleared), so a re-tap
 * is free.
 *
 * Lifetime: [scope] is intentionally application-scoped so single-flight work
 * can outlive a cancelled ViewModel collector. Prefer injecting the process
 * scope from the Application when DI is introduced; see remediation docs.
 */
class AiRepository(
    private val dao: AiResultDao,
    private val telemetry: AiTelemetry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val inFlight = ConcurrentHashMap<String, Deferred<String>>()
    private val cacheGeneration = AtomicLong(0L)
    private val persistMutex = Mutex()

    suspend fun cachedOrFetch(
        cacheKey: String,
        feature: AiFeature,
        articleUrl: String? = null,
        fetch: suspend () -> String
    ): String {
        dao.get(cacheKey)?.let { cached ->
            val ageMs = clock() - cached.createdAt
            val ttl = feature.cacheTtlMs
            if (ttl <= 0L || ageMs <= ttl) {
                telemetry.recordCacheHit(feature)
                return cached.result
            }
            // Expired — fall through to regenerate. Stale row is overwritten on insert.
        }

        val generationAtStart = cacheGeneration.get()
        val deferred = synchronized(inFlight) {
            inFlight[cacheKey] ?: scope.async {
                try {
                    // Another caller may have finished and written the cache while
                    // we were waiting to enter the single-flight map — reuse it.
                    dao.get(cacheKey)?.let { cached ->
                        val ageMs = clock() - cached.createdAt
                        val ttl = feature.cacheTtlMs
                        if (ttl <= 0L || ageMs <= ttl) {
                            telemetry.recordCacheHit(feature)
                            return@async cached.result
                        }
                    }
                    // Only the single-flight owner records miss/request/error and
                    // prunes; waiters must not inflate diagnostics.
                    telemetry.recordCacheMiss(feature)
                    pruneExpired()
                    telemetry.recordRequest(feature)
                    val result = try {
                        fetch()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val aiError = e.toAiError()
                        telemetry.recordError(aiError)
                        throw AiRequestException(aiError, e)
                    }
                    persistIfGenerationCurrent(
                        generation = generationAtStart,
                        entity = AiResultEntity(
                            cacheKey = cacheKey,
                            kind = feature.id,
                            articleUrl = articleUrl,
                            result = result,
                            createdAt = clock()
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
        } catch (e: AiRequestException) {
            // Owner already recorded the classified failure exactly once.
            throw e
        } catch (e: Exception) {
            // Unexpected wrapper failure only — should be rare.
            val aiError = e.toAiError()
            telemetry.recordError(aiError)
            throw AiRequestException(aiError, e)
        }
    }

    /**
     * Prunes every cacheable feature with that feature's own TTL cutoff.
     * A six-hour trends miss must never delete a still-valid seven-day summary.
     * CHAT has TTL 0 and is never persisted, so it is skipped.
     */
    private suspend fun pruneExpired() {
        val now = clock()
        AiFeature.entries.forEach { feature ->
            val ttl = feature.cacheTtlMs
            if (ttl <= 0L) return@forEach
            runCatching {
                dao.deleteOlderThanForKind(feature.id, now - ttl)
            }
        }
    }

    /**
     * Persist only when the cache generation captured at request start still
     * matches. Held briefly under [persistMutex] with [clearCache] so an insert
     * cannot sneak between generation check and Room write.
     */
    private suspend fun persistIfGenerationCurrent(
        generation: Long,
        entity: AiResultEntity
    ) {
        persistMutex.withLock {
            if (cacheGeneration.get() != generation) return
            dao.insert(entity)
        }
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

    /**
     * Establishes an invalidation barrier: advances [cacheGeneration] then clears
     * Room under the same mutex used by persist. In-flight owners from the prior
     * generation may still return live results to their callers, but must not
     * write into Room afterward.
     */
    suspend fun clearCache() {
        persistMutex.withLock {
            cacheGeneration.incrementAndGet()
            dao.clearAll()
        }
    }

    /** Test/diagnostic visibility into the current invalidation generation. */
    internal fun currentCacheGeneration(): Long = cacheGeneration.get()
}
