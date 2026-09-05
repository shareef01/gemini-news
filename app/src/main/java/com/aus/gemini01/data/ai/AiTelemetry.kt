package com.aus.gemini01.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

data class AiDiagnostics(
    val date: String,
    val requestsByFeature: Map<String, Int>,
    val cacheHits: Int,
    val cacheMisses: Int,
    val errorsByType: Map<String, Int>,
    val lastRequestAt: Long?
)

interface AiTelemetry {
    fun recordRequest(feature: AiFeature)
    fun recordCacheHit(feature: AiFeature)
    fun recordCacheMiss(feature: AiFeature)
    fun recordError(error: AiError)
    val counters: Flow<AiDiagnostics>
}

private val Context.aiStore: DataStore<Preferences> by preferencesDataStore(name = "ai_telemetry")

/**
 * Counts AI requests, cache hits and classified errors per calendar day so
 * "most Gemini usage comes from X" is answerable from real data. Counters
 * reset on the first write after a date change; nothing sensitive is stored.
 */
class DataStoreAiTelemetry(context: Context) : AiTelemetry {

    private val store = context.applicationContext.aiStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dateKey = stringPreferencesKey("date")
    private val lastRequestKey = longPreferencesKey("last_request_at")
    private val hitsKey = intPreferencesKey("cache_hits")
    private val missesKey = intPreferencesKey("cache_misses")

    private fun requestKey(feature: AiFeature) = intPreferencesKey("req_${feature.id}")
    private fun errorKey(type: String) = intPreferencesKey("err_$type")

    /**
     * Single atomic transform: date rollover (clear + stamp today) happens
     * before the increment so the first write of a new day starts from zero.
     */
    private fun increment(key: Preferences.Key<Int>, extra: (MutablePreferences) -> Unit = {}) {
        scope.launch {
            store.edit { prefs ->
                val today = LocalDate.now().toString()
                if (prefs[dateKey] != today) {
                    prefs.clear()
                    prefs[dateKey] = today
                }
                prefs[key] = (prefs[key] ?: 0) + 1
                extra(prefs)
            }
        }
    }

    override fun recordRequest(feature: AiFeature) {
        increment(requestKey(feature)) { it[lastRequestKey] = System.currentTimeMillis() }
    }

    override fun recordCacheHit(feature: AiFeature) = increment(hitsKey)

    override fun recordCacheMiss(feature: AiFeature) = increment(missesKey)

    override fun recordError(error: AiError) {
        val type = when (error) {
            is AiError.QuotaExceeded -> "quota"
            is AiError.Network -> "network"
            is AiError.Timeout -> "timeout"
            is AiError.Unknown -> "other"
        }
        increment(errorKey(type))
    }

    override val counters: Flow<AiDiagnostics> = store.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
        val isCurrent = prefs[dateKey] == LocalDate.now().toString()
        fun current(key: Preferences.Key<Int>) = if (isCurrent) prefs[key] ?: 0 else 0
        AiDiagnostics(
            date = LocalDate.now().toString(),
            requestsByFeature = AiFeature.entries.associate { f -> f.id to current(requestKey(f)) },
            cacheHits = current(hitsKey),
            cacheMisses = current(missesKey),
            errorsByType = listOf("quota", "network", "timeout", "other")
                .associate { t -> t to current(errorKey(t)) },
            lastRequestAt = if (isCurrent) prefs[lastRequestKey] else null
        )
    }
}
