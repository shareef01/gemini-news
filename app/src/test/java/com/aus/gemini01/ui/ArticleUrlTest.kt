package com.aus.gemini01.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleUrlTest {

    @Test
    fun `null returns null`() {
        assertNull(safeArticleUrl(null))
    }

    @Test
    fun `https url passes through`() {
        assertEquals(
            "https://news.example/a",
            safeArticleUrl("https://news.example/a")
        )
    }

    @Test
    fun `http url is upgraded to https`() {
        assertEquals(
            "https://news.example/a",
            safeArticleUrl("http://news.example/a")
        )
    }

    @Test
    fun `non-web schemes are rejected`() {
        assertNull(safeArticleUrl("javascript:alert(1)"))
        assertNull(safeArticleUrl("file:///sdcard/x.html"))
        assertNull(safeArticleUrl("intent://scan/#Intent;end"))
    }
}
