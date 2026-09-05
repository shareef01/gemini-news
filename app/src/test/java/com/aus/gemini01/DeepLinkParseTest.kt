package com.aus.gemini01

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URISyntaxException

/**
 * Exercises the shared [parseNewsDeepLink] helper used by MainActivity.
 * Hostile / malformed inputs must not throw into the caller.
 */
class DeepLinkParseTest {

    private fun parse(uriString: String): NewsDeepLink? {
        val uri = try {
            URI(uriString)
        } catch (_: URISyntaxException) {
            return null
        }
        val lastSegment = uri.path
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }
            ?.substringAfterLast('/')
        return parseNewsDeepLink(uri.scheme, uri.host, lastSegment)
    }

    @Test
    fun `valid category deep link`() {
        val link = parse("newsapp://category/technology")
        assertTrue(link is NewsDeepLink.Category)
        assertEquals("technology", (link as NewsDeepLink.Category).name)
    }

    @Test
    fun `search query is decoded`() {
        val link = parse("newsapp://search/climate%20change")
        assertTrue(link is NewsDeepLink.Search)
        assertEquals("climate change", (link as NewsDeepLink.Search).query)
    }

    @Test
    fun `malformed percent encoding yields null`() {
        // java.net.URI may reject bad encodings at parse time; either outcome
        // (null link) is acceptable as long as we don't throw.
        assertNull(parse("newsapp://search/%ZZ"))
    }

    @Test
    fun `empty scheme path is harmless`() {
        assertNull(parse("newsapp://"))
    }

    @Test
    fun `javascript scheme is ignored`() {
        assertNull(parse("javascript:alert(1)"))
    }

    @Test
    fun `blank search segment is ignored`() {
        assertNull(parseNewsDeepLink("newsapp", "search", "   "))
    }

    @Test
    fun `unknown category is rejected`() {
        assertNull(parseNewsDeepLink("newsapp", "category", "admin"))
    }

    @Test
    fun `oversized search query is rejected`() {
        assertNull(parseNewsDeepLink("newsapp", "search", "a".repeat(201)))
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
    }
}
