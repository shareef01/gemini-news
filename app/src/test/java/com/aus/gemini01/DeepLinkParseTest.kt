package com.aus.gemini01

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the shared [parseNewsDeepLink] helper used by MainActivity.
 * Inputs are treated as already decoded once (Android Uri semantics).
 */
class DeepLinkParseTest {

    @Test
    fun `valid category deep link`() {
        val link = parseNewsDeepLink("newsapp", "category", "technology")
        assertTrue(link is NewsDeepLink.Category)
        assertEquals("technology", (link as NewsDeepLink.Category).name)
    }

    @Test
    fun `search query keeps spaces from decoded segment`() {
        val link = parseNewsDeepLink("newsapp", "search", "climate change")
        assertTrue(link is NewsDeepLink.Search)
        assertEquals("climate change", (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `literal plus is preserved not treated as space`() {
        val link = parseNewsDeepLink("newsapp", "search", "C++")
        assertTrue(link is NewsDeepLink.Search)
        assertEquals("C++", (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `percent25 remains percent after single decode`() {
        // Android Uri would decode %2525 -> %25 once; parser must not decode again.
        val link = parseNewsDeepLink("newsapp", "search", "%25")
        assertTrue(link is NewsDeepLink.Search)
        assertEquals("%25", (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `unicode and emoji accepted`() {
        val link = parseNewsDeepLink("newsapp", "search", "東京 📰")
        assertTrue(link is NewsDeepLink.Search)
        assertEquals("東京 📰", (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `encoded path separator style segment is accepted as literal text`() {
        val link = parseNewsDeepLink("newsapp", "search", "a/b")
        assertTrue(link is NewsDeepLink.Search)
        assertEquals("a/b", (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `empty query rejected`() {
        assertNull(parseNewsDeepLink("newsapp", "search", ""))
        assertNull(parseNewsDeepLink("newsapp", "search", "   "))
    }

    @Test
    fun `max accepted length allowed`() {
        val query = "a".repeat(200)
        val link = parseNewsDeepLink("newsapp", "search", query)
        assertTrue(link is NewsDeepLink.Search)
        assertEquals(query, (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `oversized search query is rejected`() {
        assertNull(parseNewsDeepLink("newsapp", "search", "a".repeat(201)))
    }

    @Test
    fun `unknown host fails closed`() {
        assertNull(parseNewsDeepLink("newsapp", "admin", "x"))
    }

    @Test
    fun `unknown category is rejected`() {
        assertNull(parseNewsDeepLink("newsapp", "category", "admin"))
    }

    @Test
    fun `javascript scheme is ignored`() {
        assertNull(parseNewsDeepLink("javascript", "alert", "1"))
    }

    @Test
    fun `empty host is ignored`() {
        assertNull(parseNewsDeepLink("newsapp", "", "technology"))
        assertNull(parseNewsDeepLink("newsapp", null, "technology"))
    }

    @Test
    fun `article deep link accepts only a valid web url`() {
        assertTrue(
            parseNewsDeepLink(
                "newsapp",
                "article",
                null,
                "https://news.example/story"
            ) is NewsDeepLink.Article
        )
        assertNull(parseNewsDeepLink("newsapp", "article", null, "javascript:alert(1)"))
        assertNull(parseNewsDeepLink("newsapp", "article", null, "https://"))
        assertNull(parseNewsDeepLink("newsapp", "article", null, "file:///etc/passwd"))
        assertNull(parseNewsDeepLink("newsapp", "article", null, "http://user:pass@evil.test/"))
    }
}
