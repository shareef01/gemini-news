package com.aus.gemini01.data.ai

import com.aus.gemini01.data.local.AiKindCount
import com.aus.gemini01.data.local.AiResultDao
import com.aus.gemini01.data.local.AiResultEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class FakeAiResultDao : AiResultDao {
    private val store = java.util.concurrent.ConcurrentHashMap<String, AiResultEntity>()

    override suspend fun get(key: String): AiResultEntity? = store[key]

    override suspend fun insert(entity: AiResultEntity) {
        store[entity.cacheKey] = entity
    }

    override fun countsByKind(): Flow<List<AiKindCount>> =
        flowOf(store.values.groupBy { it.kind }.map { AiKindCount(it.key, it.value.size) })

    override suspend fun deleteOlderThan(cutoffEpochMs: Long): Int {
        val toRemove = store.entries.filter { it.value.createdAt < cutoffEpochMs }.map { it.key }
        toRemove.forEach { store.remove(it) }
        return toRemove.size
    }

    override suspend fun clearAll() {
        store.clear()
    }
}

class FakeAiTelemetry : AiTelemetry {
    val requests = mutableListOf<AiFeature>()
    val cacheHits = mutableListOf<AiFeature>()
    val cacheMisses = mutableListOf<AiFeature>()
    val errors = mutableListOf<AiError>()

    override fun recordRequest(feature: AiFeature) {
        requests.add(feature)
    }

    override fun recordCacheHit(feature: AiFeature) {
        cacheHits.add(feature)
    }

    override fun recordCacheMiss(feature: AiFeature) {
        cacheMisses.add(feature)
    }

    override fun recordError(error: AiError) {
        errors.add(error)
    }

    override val counters: Flow<AiDiagnostics> = flowOf(
        AiDiagnostics(
            date = "2026-08-20",
            requestsByFeature = emptyMap(),
            cacheHits = 0,
            cacheMisses = 0,
            errorsByType = emptyMap(),
            lastRequestAt = null
        )
    )
}

class AiRepositoryTest {

    @Test
    fun `cachedOrFetch returns cached result on cache hit without calling fetch`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)

        dao.insert(
            AiResultEntity(
                cacheKey = "key_1",
                kind = AiFeature.SUMMARY.id,
                articleUrl = "https://example.com/1",
                result = "Cached Summary",
                createdAt = System.currentTimeMillis()
            )
        )

        var fetchCalled = false
        val result = repository.cachedOrFetch("key_1", AiFeature.SUMMARY) {
            fetchCalled = true
            "Fresh Summary"
        }

        assertEquals("Cached Summary", result)
        assertEquals(false, fetchCalled)
        assertEquals(listOf(AiFeature.SUMMARY), telemetry.cacheHits)
        assertEquals(0, telemetry.cacheMisses.size)
        assertEquals(0, telemetry.requests.size)
    }

    @Test
    fun `cachedOrFetch calls fetch, inserts into dao, and returns result on cache miss`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)

        var fetchCalled = false
        val result = repository.cachedOrFetch("key_2", AiFeature.READER, "https://example.com/2") {
            fetchCalled = true
            "Generated Reader View"
        }

        assertEquals("Generated Reader View", result)
        assertEquals(true, fetchCalled)
        assertEquals(listOf(AiFeature.READER), telemetry.cacheMisses)
        assertEquals(listOf(AiFeature.READER), telemetry.requests)
        assertEquals("Generated Reader View", dao.get("key_2")?.result)
    }

    @Test
    fun `cachedOrFetch classifies quota error and does not cache failure`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)

        try {
            repository.cachedOrFetch("key_quota", AiFeature.SUMMARY) {
                throw RuntimeException("RESOURCE_EXHAUSTED: 429 Quota exceeded")
            }
            fail("Expected AiRequestException")
        } catch (e: AiRequestException) {
            assertTrue(e.error is AiError.QuotaExceeded)
        }

        assertEquals(null, dao.get("key_quota"))
        assertEquals(listOf(AiError.QuotaExceeded), telemetry.errors)
    }

    @Test
    fun `cachedOrFetch classifies network error`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)

        try {
            repository.cachedOrFetch("key_network", AiFeature.SUMMARY) {
                throw IOException("Unable to resolve host: UnknownHostException")
            }
            fail("Expected AiRequestException")
        } catch (e: AiRequestException) {
            assertTrue(e.error is AiError.Network)
        }

        assertEquals(listOf(AiError.Network), telemetry.errors)
    }

    @Test
    fun `cachedOrFetch deduplicates concurrent calls for same cache key`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = AiRepository(dao, telemetry, testScope)

        val fetchCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        val call1 = testScope.async {
            repository.cachedOrFetch("key_dedup", AiFeature.TRENDS) {
                fetchCount.incrementAndGet()
                gate.await()
                "Trends Result"
            }
        }

        val call2 = testScope.async {
            repository.cachedOrFetch("key_dedup", AiFeature.TRENDS) {
                fetchCount.incrementAndGet()
                gate.await()
                "Trends Result"
            }
        }

        gate.complete(Unit)
        val res1 = call1.await()
        val res2 = call2.await()

        assertEquals("Trends Result", res1)
        assertEquals("Trends Result", res2)
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun `recordUncachedRequest executes block and records telemetry`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)

        val result = repository.recordUncachedRequest(AiFeature.CHAT) {
            "Chat reply"
        }

        assertEquals("Chat reply", result)
        assertEquals(listOf(AiFeature.CHAT), telemetry.requests)
    }

    @Test
    fun `AiCacheKeys are deterministic and differentiate language`() {
        val key1 = AiCacheKeys.summary("https://example.com/news", "English")
        val key2 = AiCacheKeys.summary("https://example.com/news", "English")
        val keySpanish = AiCacheKeys.summary("https://example.com/news", "Spanish")

        assertEquals(key1, key2)
        assertNotEquals(key1, keySpanish)
    }

    @Test
    fun `cachedOrFetch regenerates when cached result exceeds feature TTL`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)
        val now = System.currentTimeMillis()

        dao.insert(
            AiResultEntity(
                cacheKey = "key_ttl",
                kind = AiFeature.TRENDS.id,
                articleUrl = null,
                result = "Stale trends",
                createdAt = now - AiFeature.TRENDS.cacheTtlMs - 1_000
            )
        )

        val result = repository.cachedOrFetch("key_ttl", AiFeature.TRENDS) { "Fresh trends" }

        assertEquals("Fresh trends", result)
        assertEquals("Fresh trends", dao.get("key_ttl")?.result)
        assertEquals(1, telemetry.cacheMisses.size)
        assertEquals(0, telemetry.cacheHits.size)
    }

    @Test
    fun `sameRequest_50ConcurrentCallers_onlyOneRemoteInvocation`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = AiRepository(dao, telemetry, testScope)
        val fetchCount = AtomicInteger(0)
        val readyCount = AtomicInteger(0)
        val start = CompletableDeferred<Unit>()
        val hold = CompletableDeferred<Unit>()

        val jobs = List(50) {
            testScope.async {
                readyCount.incrementAndGet()
                start.await()
                repository.cachedOrFetch("key_50", AiFeature.SUMMARY) {
                    fetchCount.incrementAndGet()
                    hold.await()
                    "shared"
                }
            }
        }
        // Suspend-friendly barrier (never block Default pool threads with a Latch).
        while (readyCount.get() < 50) {
            kotlinx.coroutines.delay(1)
        }
        start.complete(Unit)
        // Give callers a moment to join the single-flight Deferred before release.
        kotlinx.coroutines.delay(50)
        hold.complete(Unit)
        val results = jobs.map { it.await() }

        assertEquals(50, results.size)
        assertTrue(results.all { it == "shared" })
        assertEquals(1, fetchCount.get())
        assertEquals(1, telemetry.cacheMisses.size)
    }

    @Test
    fun `failedSharedRequest_isRemovedFromInflightMap`() = runBlocking {
        val dao = FakeAiResultDao()
        val telemetry = FakeAiTelemetry()
        val repository = AiRepository(dao, telemetry)

        try {
            repository.cachedOrFetch("key_fail", AiFeature.SUMMARY) {
                throw RuntimeException("429 quota")
            }
            fail("Expected AiRequestException")
        } catch (_: AiRequestException) {
        }

        var secondFetch = 0
        try {
            repository.cachedOrFetch("key_fail", AiFeature.SUMMARY) {
                secondFetch++
                throw RuntimeException("429 quota again")
            }
            fail("Expected AiRequestException")
        } catch (_: AiRequestException) {
        }

        assertEquals(1, secondFetch)
    }
}
