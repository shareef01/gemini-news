package com.aus.gemini01.data.ai

import java.security.MessageDigest

/**
 * Pinned stable Gemini model ID for production. Do not use `-latest` aliases —
 * Firebase AI Logic documents them as unstable for shipped apps.
 */
const val GEMINI_MODEL = "gemini-3.8-flash"

/**
 * Human-readable label shown in Settings; must stay in sync with [GEMINI_MODEL].
 */
const val GEMINI_MODEL_LABEL = "Gemini 3.8 Flash"

/**
 * Bump when a prompt's instructions change in a way that alters the expected
 * output shape - existing cache entries then miss and regenerate once.
 */
const val AI_PROMPT_VERSION = 4

enum class AiFeature(val id: String) {
    SUMMARY("summary"),
    READER("reader"),
    FOR_YOU("for_you"),
    STATS("stats"),
    TRENDS("trends"),
    THEMES("themes"),
    LOCATIONS("locations"),
    CHAT("chat");

    /** Soft TTL for Room-persisted results. Time-sensitive features expire sooner. */
    val cacheTtlMs: Long
        get() = when (this) {
            SUMMARY, READER, STATS -> 7L * 24 * 60 * 60 * 1000
            FOR_YOU, THEMES -> 24L * 60 * 60 * 1000
            TRENDS, LOCATIONS -> 6L * 60 * 60 * 1000
            CHAT -> 0L
        }
}

/**
 * Stable cache keys: hash(model + promptVersion + kind + payload identity).
 * Language-sensitive features fold the language into the payload so switching
 * the preferred language regenerates instead of serving the wrong language.
 */
object AiCacheKeys {

    fun summary(articleUrl: String, language: String): String =
        build("summary", articleUrl, language)

    fun summary(articleUrl: String, language: String, evidenceFingerprint: String): String =
        build("summary", articleUrl, language, evidenceFingerprint)

    fun reader(articleUrl: String, language: String): String =
        build("reader", articleUrl, language)

    fun reader(articleUrl: String, language: String, evidenceFingerprint: String): String =
        build("reader", articleUrl, language, evidenceFingerprint)

    fun trends(articleUrls: List<String>, language: String): String =
        build("trends", articleUrls.joinToString("|"), language)

    fun trends(articleUrls: List<String>, language: String, evidenceFingerprints: List<String>): String =
        build("trends", articleUrls.zip(evidenceFingerprints).joinToString("|") { (url, fingerprint) ->
            "$url:$fingerprint"
        }, language)

    fun themes(articleUrls: List<String>): String =
        build("themes", articleUrls.joinToString("|"))

    fun themes(articleUrls: List<String>, evidenceFingerprints: List<String>): String =
        build("themes", articleUrls.zip(evidenceFingerprints).joinToString("|") { (url, fingerprint) ->
            "$url:$fingerprint"
        })

    fun locations(articleUrls: List<String>): String =
        build("locations", articleUrls.joinToString("|"))

    fun locations(articleUrls: List<String>, evidenceFingerprints: List<String>): String =
        build("locations", articleUrls.zip(evidenceFingerprints).joinToString("|") { (url, fingerprint) ->
            "$url:$fingerprint"
        })

    fun stats(historyUrls: List<String>, language: String): String =
        build("stats", historyUrls.joinToString("|"), language)

    fun forYouKeywords(interactionUrls: List<String>): String =
        build("for_you", interactionUrls.joinToString("|"))

    /** Hashes the actual source fields so a reused URL cannot reuse stale AI prose. */
    fun articleEvidenceFingerprint(title: String, description: String?, content: String?): String =
        digest(listOf(title, description.orEmpty(), content.orEmpty()).joinToString("\u0000"))

    private fun build(
        kind: String,
        payload: String,
        language: String? = null,
        evidenceFingerprint: String = ""
    ): String {
        return digest(listOf(GEMINI_MODEL, AI_PROMPT_VERSION.toString(), kind, payload, language ?: "", evidenceFingerprint)
            .joinToString("\u0000"))
    }

    private fun digest(raw: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
