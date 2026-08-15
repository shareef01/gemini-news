package com.aus.gemini01.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `parses h1 and h2 headings`() {
        val blocks = parseMarkdown("# Main Title\n## Section\nbody")

        assertEquals(BlockKind.H1, blocks[0].kind)
        assertEquals("Main Title", blocks[0].segments[0].text)
        assertEquals(BlockKind.H2, blocks[1].kind)
        assertEquals("Section", blocks[1].segments[0].text)
        assertEquals(BlockKind.BODY, blocks[2].kind)
    }

    @Test
    fun `parses h3 headings and strips the hashes`() {
        val blocks = parseMarkdown("### 1. Key Takeaways")

        assertEquals(BlockKind.H3, blocks[0].kind)
        assertEquals("1. Key Takeaways", blocks[0].segments[0].text)
    }

    @Test
    fun `parses all supported bullet markers`() {
        val blocks = parseMarkdown("- one\n* two\n\u2022 three")

        assertEquals(3, blocks.size)
        blocks.forEach { block ->
            assertEquals(BlockKind.BULLET, block.kind)
        }
        assertEquals("one", blocks[0].segments[0].text)
        assertEquals("two", blocks[1].segments[0].text)
        assertEquals("three", blocks[2].segments[0].text)
    }

    @Test
    fun `parses bold segments`() {
        val blocks = parseMarkdown("a **bold** word")

        assertEquals(1, blocks.size)
        val segments = blocks[0].segments
        assertEquals(3, segments.size)
        assertEquals("a ", segments[0].text)
        assertFalse(segments[0].bold)
        assertEquals("bold", segments[1].text)
        assertTrue(segments[1].bold)
        assertEquals(" word", segments[2].text)
        assertFalse(segments[2].bold)
    }

    @Test
    fun `bold works inside headings and bullets`() {
        val blocks = parseMarkdown("## **Key** point\n- **Item** one")

        assertEquals(BlockKind.H2, blocks[0].kind)
        assertTrue(blocks[0].segments[1].bold)
        assertEquals("Key", blocks[0].segments[1].text)
        assertEquals(BlockKind.BULLET, blocks[1].kind)
        assertTrue(blocks[1].segments[1].bold)
        assertEquals("Item", blocks[1].segments[1].text)
    }

    @Test
    fun `joins consecutive lines into one paragraph`() {
        val blocks = parseMarkdown("first line\nsecond line")

        assertEquals(1, blocks.size)
        assertEquals(BlockKind.BODY, blocks[0].kind)
        assertEquals("first line second line", blocks[0].segments[0].text)
    }

    @Test
    fun `blank line starts a new paragraph`() {
        val blocks = parseMarkdown("para one\n\npara two")

        assertEquals(2, blocks.size)
        assertEquals("para one", blocks[0].segments[0].text)
        assertEquals("para two", blocks[1].segments[0].text)
    }

    @Test
    fun `plain text yields single body block`() {
        val blocks = parseMarkdown("just some plain text")

        assertEquals(1, blocks.size)
        assertEquals(BlockKind.BODY, blocks[0].kind)
        assertEquals("just some plain text", blocks[0].segments[0].text)
    }

    @Test
    fun `empty and blank input yields no blocks`() {
        assertEquals(0, parseMarkdown("").size)
        assertEquals(0, parseMarkdown("\n\n  \n").size)
    }

    @Test
    fun `unbalanced bold markers keep remaining text bold`() {
        val blocks = parseMarkdown("a **b")

        val segments = blocks[0].segments
        assertEquals(2, segments.size)
        assertFalse(segments[0].bold)
        assertTrue(segments[1].bold)
    }

    @Test
    fun `leading and trailing blank lines are ignored`() {
        val blocks = parseMarkdown("\n\n# Title\n\n")

        assertEquals(1, blocks.size)
        assertEquals(BlockKind.H1, blocks[0].kind)
    }
}
