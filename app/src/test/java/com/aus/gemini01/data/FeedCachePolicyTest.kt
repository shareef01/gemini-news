package com.aus.gemini01.data

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Pure policy tests for feed-cache replacement and HTTP error fallback (GN-AUD-010/017).
 */
class FeedCachePolicyTest {

    @Test
    fun `page1 non-empty articles replace cache`() {
        assertTrue(shouldReplaceFeedCache(page = 1, category = "general", articleCount = 5))
    }

    @Test
    fun `empty success does not replace cache`() {
        assertFalse(shouldReplaceFeedCache(page = 1, category = "general", articleCount = 0))
    }

    @Test
    fun `page2 never writes cache`() {
        assertFalse(shouldReplaceFeedCache(page = 2, category = "general", articleCount = 10))
    }

    @Test
    fun `bookmarks category never writes cache`() {
        assertFalse(shouldReplaceFeedCache(page = 1, category = "bookmarks", articleCount = 3))
    }

    @Test
    fun `http_429_does_not_use_offline_fallback`() {
        val error = HttpException(Response.error<Unit>(429, "".toResponseBody(null)))
        assertFalse(shouldOfflineFallbackOnError(error))
    }

    @Test
    fun `http_500_may_use_offline_fallback`() {
        val error = HttpException(Response.error<Unit>(500, "".toResponseBody(null)))
        assertTrue(shouldOfflineFallbackOnError(error))
    }

    @Test
    fun `io_exception_may_use_offline_fallback`() {
        assertTrue(shouldOfflineFallbackOnError(java.io.IOException("offline")))
    }

    @Test
    fun `rate_limit_message_is_explicit`() {
        val error = HttpException(Response.error<Unit>(429, "".toResponseBody(null)))
        val msg = newsFeedErrorMessage(error)
        assertTrue(msg.contains("rate limit", ignoreCase = true))
        assertFalse(msg.contains("UnknownHost", ignoreCase = true))
    }

    @Test
    fun `missing_api_key_does_not_use_offline_fallback`() {
        assertFalse(shouldOfflineFallbackOnError(MissingNewsApiKeyException()))
    }

    @Test
    fun `missing_api_key_message_is_actionable`() {
        val msg = newsFeedErrorMessage(MissingNewsApiKeyException())
        assertTrue(msg.contains("NEWS_API_KEY", ignoreCase = true))
        assertTrue(msg.contains("local.properties", ignoreCase = true))
    }
}