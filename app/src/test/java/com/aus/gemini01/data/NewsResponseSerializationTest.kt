package com.aus.gemini01.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NewsResponseSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `deserializes response with null or missing source name`() {
        val rawJson = """
            {
                "status": "ok",
                "totalResults": 1,
                "articles": [
                    {
                        "source": { "id": null, "name": null },
                        "author": null,
                        "title": "Entertainment News Headline",
                        "description": "Celebrity description",
                        "url": "https://example.com/entertainment",
                        "urlToImage": null,
                        "publishedAt": "2026-08-20T04:00:00Z",
                        "content": null
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<NewsResponse>(rawJson)
        assertEquals("ok", response.status)
        assertEquals(1, response.articles.size)
        assertEquals("Unknown", response.articles[0].source.name)
        assertEquals("Entertainment News Headline", response.articles[0].title)
    }

    @Test
    fun `deserializes error response without articles array`() {
        val rawJson = """
            {
                "status": "error",
                "code": "apiKeyExhausted",
                "message": "Your API key is exhausted."
            }
        """.trimIndent()

        val response = json.decodeFromString<NewsResponse>(rawJson)
        assertEquals("error", response.status)
        assertEquals("apiKeyExhausted", response.code)
        assertEquals("Your API key is exhausted.", response.message)
        assertEquals(0, response.articles.size)
    }

    @Test
    fun `deserializes article with missing optional fields`() {
        val rawJson = """
            {
                "status": "ok",
                "articles": [
                    {
                        "title": "Minimal Article",
                        "url": "https://example.com/min"
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<NewsResponse>(rawJson)
        assertEquals(1, response.articles.size)
        assertEquals("Minimal Article", response.articles[0].title)
        assertEquals("Unknown", response.articles[0].source.name)
    }
}
