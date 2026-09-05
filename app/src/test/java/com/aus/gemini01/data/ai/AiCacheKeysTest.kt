package com.aus.gemini01.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCacheKeysTest {

    @Test
    fun `same article and language produce same key`() {
        assertEquals(
            AiCacheKeys.summary("https://x.com/a", "English"),
            AiCacheKeys.summary("https://x.com/a", "English")
        )
    }

    @Test
    fun `different language produces different key`() {
        assertNotEquals(
            AiCacheKeys.summary("https://x.com/a", "English"),
            AiCacheKeys.summary("https://x.com/a", "French")
        )
    }

    @Test
    fun `different article produces different key`() {
        assertNotEquals(
            AiCacheKeys.summary("https://x.com/a", "English"),
            AiCacheKeys.summary("https://x.com/b", "English")
        )
    }

    @Test
    fun `summary and reader keys never collide for same article`() {
        assertNotEquals(
            AiCacheKeys.summary("https://x.com/a", "English"),
            AiCacheKeys.reader("https://x.com/a", "English")
        )
    }

    @Test
    fun `article evidence changes invalidate summary and reader keys`() {
        val oldEvidence = AiCacheKeys.articleEvidenceFingerprint("Headline", "Old description", "Old body")
        val newEvidence = AiCacheKeys.articleEvidenceFingerprint("Headline", "New description", "New body")
        assertNotEquals(oldEvidence, newEvidence)
        assertNotEquals(
            AiCacheKeys.summary("https://x.com/a", "English", oldEvidence),
            AiCacheKeys.summary("https://x.com/a", "English", newEvidence)
        )
        assertNotEquals(
            AiCacheKeys.reader("https://x.com/a", "English", oldEvidence),
            AiCacheKeys.reader("https://x.com/a", "English", newEvidence)
        )
    }

    @Test
    fun `article list order matters for feed features`() {
        val a = listOf("u1", "u2")
        val b = listOf("u2", "u1")
        assertNotEquals(AiCacheKeys.trends(a, "English"), AiCacheKeys.trends(b, "English"))
    }

    @Test
    fun `keys are opaque hex digests, no payload leaks into key`() {
        val key = AiCacheKeys.summary("https://secret-url.com/article?token=abc", "English")
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `model constant matches configured model`() {
        assertEquals("gemini-3.8-flash", GEMINI_MODEL)
        assertEquals("Gemini 3.8 Flash", GEMINI_MODEL_LABEL)
        assertTrue(!GEMINI_MODEL.endsWith("-latest"))
    }

    @Test
    fun `prompt version is pinned for cache invalidation`() {
        assertEquals(4, AI_PROMPT_VERSION)
    }

    @Test
    fun `time sensitive features use shorter TTLs than summaries`() {
        assertTrue(AiFeature.TRENDS.cacheTtlMs < AiFeature.SUMMARY.cacheTtlMs)
        assertTrue(AiFeature.LOCATIONS.cacheTtlMs < AiFeature.READER.cacheTtlMs)
        assertEquals(0L, AiFeature.CHAT.cacheTtlMs)
    }
}
