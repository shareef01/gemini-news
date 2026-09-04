package com.aus.gemini01

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Mirrors MainActivity deep-link host/path parsing without Android framework APIs
 * (so it runs on the JVM unit-test classpath). Hostile / malformed inputs must
 * not throw into the caller.
 */
class DeepLinkParseTest {

    private fun parseNewsApp(uriString: String): Pair<String?, String?> {
        val uri = try {
            URI(uriString)
        } catch (_: URISyntaxException) {
            return null to null
        }
        if (uri.scheme != "newsapp") return null to null
        val host = uri.host
        val rawPath = uri.path?.trim('/')?.takeIf { it.isNotEmpty() }
        val segment = rawPath?.substringAfterLast('/')?.let { last ->
            try {
                URLDecoder.decode(last, "UTF-8")
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return host to segment
    }

    @Test
    fun `valid category deep link`() {
        val (host, path) = parseNewsApp("newsapp://category/technology")
        assertEquals("category", host)
        assertEquals("technology", path)
    }

    @Test
    fun `search query is decoded`() {
        val (host, path) = parseNewsApp("newsapp://search/climate%20change")
        assertEquals("search", host)
        assertEquals("climate change", path)
    }

    @Test
    fun `malformed percent encoding yields null query`() {
        // java.net.URI rejects some bad encodings at parse time; either outcome
        // (null host or null segment) is acceptable as long as we don't throw.
        val (host, path) = parseNewsApp("newsapp://search/%ZZ")
        if (host == null) {
            assertNull(path)
        } else {
            assertEquals("search", host)
            assertNull(path)
        }
    }

    @Test
    fun `empty scheme path is harmless`() {
        val (host, path) = parseNewsApp("newsapp://")
        assertNull(host)
        assertNull(path)
    }

    @Test
    fun `javascript scheme is ignored`() {
        val (host, path) = parseNewsApp("javascript:alert(1)")
        assertNull(host)
        assertNull(path)
    }
}
