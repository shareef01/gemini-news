package com.aus.gemini01.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure policy tests for feed-cache replacement. Empty successful payloads must
 * not erase a previously good Room cache (GN-AUD-010).
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
}
