package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParsersTest {

    private fun article(index: Int, title: String = "Article $index") = Article(
        source = Source(name = "Test"),
        author = null,
        title = title,
        description = null,
        url = "https://example.com/$index",
        urlToImage = null,
        publishedAt = "2026-01-01T00:00:00Z",
        content = null
    )

    @Test
    fun `smart themes - parses multiple themes`() {
        val articles = List(5) { article(it) }
        val response = """
            Tech: 0, 2
            Sports: 1, 3
            Other: 4
        """.trimIndent()

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(0, result.skippedLines)
        assertEquals(3, result.themes.size)
        assertEquals(listOf(articles[0], articles[2]), result.themes["Tech"])
        assertEquals(listOf(articles[1], articles[3]), result.themes["Sports"])
        assertEquals(listOf(articles[4]), result.themes["Other"])
    }

    @Test
    fun `smart themes - theme name with colon is not in spec but parser handles gracefully`() {
        // The spec only allows "Theme Name: index, index" with a single colon.
        // If Gemini emits a name with a colon (e.g. "AI: Regulation"), only the
        // part before the first colon is treated as the name, and the rest is
        // expected to be indices. Since it won't parse, the line is skipped.
        val articles = listOf(article(0))
        val response = "AI: Regulation: 0"

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(1, result.skippedLines)
        assertTrue(result.themes.isEmpty())
    }

    @Test
    fun `smart themes - out of range indices are skipped`() {
        val articles = List(3) { article(it) }
        val response = "Mixed: 0, 5, -1, 2, banana"

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(0, result.skippedLines)
        assertEquals(listOf(articles[0], articles[2]), result.themes["Mixed"])
    }

    @Test
    fun `smart themes - duplicate indices are deduplicated`() {
        val articles = List(3) { article(it) }
        val response = "Dup: 0, 0, 1, 1"

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(2, result.themes["Dup"]?.size)
    }

    @Test
    fun `smart themes - blank lines are ignored not skipped`() {
        val articles = listOf(article(0))
        val response = "\n\nTech: 0\n\n"

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(0, result.skippedLines)
        assertEquals(1, result.themes.size)
    }

    @Test
    fun `smart themes - malformed line without colon is skipped`() {
        val articles = listOf(article(0))
        val response = "Tech 0 1 2"

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(1, result.skippedLines)
        assertTrue(result.themes.isEmpty())
    }

    @Test
    fun `smart themes - empty indices part is skipped`() {
        val articles = listOf(article(0))
        val response = "EmptyTheme: "

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(1, result.skippedLines)
        assertTrue(result.themes.isEmpty())
    }

    @Test
    fun `smart themes - line with no valid indices is skipped`() {
        val articles = listOf(article(0))
        val response = "Bad: a, b, c"

        val result = parseSmartThemesResponse(response, articles)

        assertEquals(1, result.skippedLines)
        assertTrue(result.themes.isEmpty())
    }

    @Test
    fun `locations - parses well-formed lines`() {
        val response = """
            Paris, France | 48.8566 | 2.3522 | Olympics Begin | https://example.com/olympics
            Tokyo, Japan | 35.6762 | 139.6503 | Election News | https://example.com/election
        """.trimIndent()

        val result = parseLocationsResponse(response)

        assertEquals(0, result.skippedLines)
        assertEquals(2, result.locations.size)
        assertEquals("Paris, France", result.locations[0].name)
        assertEquals(48.8566, result.locations[0].latitude, 0.0001)
        assertEquals(2.3522, result.locations[0].longitude, 0.0001)
        assertEquals("https://example.com/olympics", result.locations[0].articleUrl)
    }

    @Test
    fun `locations - too few fields is skipped`() {
        val response = "Paris | 48.8566 | 2.3522"

        val result = parseLocationsResponse(response)

        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - non-numeric coords are skipped`() {
        val response = "Paris, France | not-a-number | 2.3522 | Title | https://example.com"

        val result = parseLocationsResponse(response)

        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - out of range coordinates are rejected`() {
        val response = "Bad | 200.0 | 0.0 | Title | https://example.com"

        val result = parseLocationsResponse(response)

        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - blank name is skipped`() {
        val response = "  | 48.8566 | 2.3522 | Title | https://example.com"

        val result = parseLocationsResponse(response)

        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - blank lines are ignored not skipped`() {
        val response = "\n\nParis, France | 48.8566 | 2.3522 | Title | https://example.com\n\n"

        val result = parseLocationsResponse(response)

        assertEquals(0, result.skippedLines)
        assertEquals(1, result.locations.size)
    }
}