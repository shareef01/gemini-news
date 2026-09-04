package com.aus.gemini01.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PaginationTest {

    @Test
    fun `next page is current plus one`() {
        assertEquals(2, nextPageToLoad(1))
        assertEquals(3, nextPageToLoad(2))
    }
}
