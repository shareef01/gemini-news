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
    fun `locations - maps index to original article identity`() {
        val articles = listOf(article(0, "Olympics Begin"), article(1, "Election News"))
        val response = """
            0|Paris, France|48.8566|2.3522
            1|Tokyo, Japan|35.6762|139.6503
        """.trimIndent()

        val result = parseLocationsResponse(response, articles)

        assertEquals(0, result.skippedLines)
        assertEquals(2, result.locations.size)
        assertEquals("Paris, France", result.locations[0].name)
        assertEquals(48.8566, result.locations[0].latitude, 0.0001)
        assertEquals(2.3522, result.locations[0].longitude, 0.0001)
        assertEquals("Olympics Begin", result.locations[0].articleTitle)
        assertEquals("https://example.com/0", result.locations[0].articleUrl)
        assertEquals(articles[1].url, result.locations[1].articleUrl)
        assertEquals(articles[1].title, result.locations[1].articleTitle)
    }

    @Test
    fun `locations - negative index skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("-1|Paris|48.8|2.3", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - huge index skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("999|Paris|48.8|2.3", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - nonnumeric index skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("abc|Paris|48.8|2.3", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - duplicate index location ignored`() {
        val articles = listOf(article(0))
        val response = """
            0|Paris|48.8|2.3
            0|Paris|48.8|2.3
        """.trimIndent()
        val result = parseLocationsResponse(response, articles)
        assertEquals(1, result.locations.size)
    }

    @Test
    fun `locations - malformed latitude skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("0|Paris|not-a-number|2.3", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - NaN and infinity skipped`() {
        val articles = listOf(article(0))
        assertEquals(1, parseLocationsResponse("0|Paris|NaN|2.3", articles).skippedLines)
        assertEquals(1, parseLocationsResponse("0|Paris|Infinity|2.3", articles).skippedLines)
        assertTrue(parseLocationsResponse("0|Paris|NaN|2.3", articles).locations.isEmpty())
    }

    @Test
    fun `locations - out of range coordinates rejected`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("0|Bad|200.0|0.0", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - model URL or title fields do not become authority`() {
        val articles = listOf(article(0, "Real Title"))
        // Extra trailing fields (URL/title attempts) are ignored; identity stays source.
        val result = parseLocationsResponse(
            "0|Paris|48.8|2.3|Fake Title|https://evil.example/hijack",
            articles
        )
        assertEquals(1, result.locations.size)
        assertEquals("Real Title", result.locations[0].articleTitle)
        assertEquals("https://example.com/0", result.locations[0].articleUrl)
    }

    @Test
    fun `locations - too few fields skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("0|Paris|48.8", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }

    @Test
    fun `locations - blank lines ignored not skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("\n\n0|Paris|48.8|2.3\n\n", articles)
        assertEquals(0, result.skippedLines)
        assertEquals(1, result.locations.size)
    }

    @Test
    fun `locations - unknown garbage lines skipped`() {
        val articles = listOf(article(0))
        val result = parseLocationsResponse("hello world", articles)
        assertEquals(1, result.skippedLines)
        assertTrue(result.locations.isEmpty())
    }
}
