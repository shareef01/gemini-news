package com.aus.gemini01.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlTest {

    @Test
    fun `null returns null`() {
        assertNull(safeImageUrl(null))
    }

    @Test
    fun `blank returns null`() {
        assertNull(safeImageUrl(""))
        assertNull(safeImageUrl("   "))
    }

    @Test
    fun `https url passes through unchanged`() {
        assertEquals(
            "https://example.com/image.jpg",
            safeImageUrl("https://example.com/image.jpg")
        )
    }

    @Test
    fun `http url is upgraded to https`() {
        assertEquals(
            "https://example.com/image.jpg",
            safeImageUrl("http://example.com/image.jpg")
        )
    }

    @Test
    fun `non-web scheme is rejected`() {
        assertNull(safeImageUrl("file:///sdcard/image.png"))
        assertNull(safeImageUrl("data:image/png;base64,abcd"))
        assertNull(safeImageUrl("content://media/123"))
        assertNull(safeImageUrl("javascript:alert(1)"))
    }

    @Test
    fun `scheme-less url is rejected`() {
        assertNull(safeImageUrl("example.com/image.jpg"))
    }
}