package com.aus.gemini01.data.ai

import kotlinx.coroutines.TimeoutCancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AiErrorTest {

    private fun exceptionWithMessage(vararg chain: String?): Throwable {
        var root: Throwable? = null
        for (msg in chain.reversed()) {
            root = if (root == null) RuntimeException(msg) else RuntimeException(msg, root)
        }
        return root!!
    }

    @Test
    fun `429 maps to quota exceeded`() {
        assertEquals(AiError.QuotaExceeded, exceptionWithMessage("HTTP 429 Resource Exhausted").toAiError())
    }

    @Test
    fun `resource_exhausted in nested cause maps to quota exceeded`() {
        val nested = exceptionWithMessage("outer wrapper", "RESOURCE_EXHAUSTED: quota exceeded")
        assertEquals(AiError.QuotaExceeded, nested.toAiError())
    }

    @Test
    fun `network messages map to network error`() {
        assertEquals(
            AiError.Network,
            exceptionWithMessage("Unable to resolve host newsapi.org").toAiError()
        )
        assertEquals(
            AiError.Network,
            exceptionWithMessage("failed to connect to endpoint").toAiError()
        )
    }

    @Test
    fun `timeout cancellation maps to timeout`() = kotlinx.coroutines.runBlocking {
        // Produce a genuine TimeoutCancellationException rather than trying to
        // construct one (its constructors are internal in kotlinx.coroutines).
        val ex = runCatching {
            kotlinx.coroutines.withTimeout(10) { kotlinx.coroutines.delay(1_000) }
        }.exceptionOrNull()
        assertTrue(ex is TimeoutCancellationException)
        assertEquals(AiError.Timeout, ex!!.toAiError())
    }

    @Test
    fun `io exception without quota markers maps to unknown`() {
        val result = IOException("connection reset").toAiError()
        assertTrue(result is AiError.Unknown)
    }

    @Test
    fun `unknown error carries detail`() {
        val result = exceptionWithMessage("some weird failure").toAiError()
        assertTrue(result is AiError.Unknown)
        assertEquals("some weird failure", (result as AiError.Unknown).detail)
    }

    @Test
    fun `friendly messages never leak raw exception text`() {
        val messages = listOf(
            AiError.QuotaExceeded,
            AiError.Network,
            AiError.Timeout,
            AiError.Unknown("java.lang.Exception: SECRET internal stack at com.google.Internal")
        ).map { it.friendlyMessage() }

        assertTrue(messages.none { it.contains("SECRET") || it.contains("Exception") })
        assertTrue(messages.none { it.isBlank() })
    }
}
