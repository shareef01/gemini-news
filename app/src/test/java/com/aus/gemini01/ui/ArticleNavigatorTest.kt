package com.aus.gemini01.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleNavigatorTest {

    @Test
    fun `rejects unsafe or non-https article urls`() {
        assertNull(safeArticleUrl("javascript:alert(1)"))
        assertNull(safeArticleUrl("file:///android_asset/secret.txt"))
        assertNull(safeArticleUrl("intent://example.com/#Intent;scheme=https;end"))
        assertNull(safeArticleUrl("data:text/html,<html>"))
        assertNull(safeArticleUrl(""))
        assertNull(safeArticleUrl(null))
    }

    @Test
    fun `upgrades and validates clean web urls`() {
        org.junit.Assert.assertEquals("https://example.com/story", safeArticleUrl("https://example.com/story"))
        org.junit.Assert.assertEquals("https://example.com/story", safeArticleUrl("http://example.com/story"))
    }
}
