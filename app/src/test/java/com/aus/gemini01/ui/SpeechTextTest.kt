package com.aus.gemini01.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeechTextTest {

    @Test
    fun `strips headings bold and bullets for speech`() {
        val input = """
            # Title
            ## Section
            - point one
            1. numbered
            **bold** and __also__
            > quote
        """.trimIndent()

        val spoken = stripMarkdownForSpeech(input)
        assertFalse(spoken.contains("#"))
        assertFalse(spoken.contains("**"))
        assertFalse(spoken.contains("__"))
        assertEquals(true, spoken.contains("Title"))
        assertEquals(true, spoken.contains("point one"))
        assertEquals(true, spoken.contains("bold"))
    }
}
