package com.aus.gemini01.data.ai

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException

/**
 * User-meaningful taxonomy for AI failures. Raw SDK exceptions never reach the
 * UI; they are classified here so each failure mode can get its own message
 * and recovery path (quota exhaustion is common on the free tier and must not
 * look like a bug).
 */
sealed interface AiError {
    data object QuotaExceeded : AiError
    data object Network : AiError
    data object Timeout : AiError
    data class Unknown(val detail: String?) : AiError
}

class AiRequestException(val error: AiError, cause: Throwable?) : Exception(cause)

/**
 * Classifies a throwable from the Firebase AI SDK. Heuristic on purpose: the
 * SDK wraps transport errors in several layers, so the message/cause chain is
 * inspected rather than relying on one stable exception class. Cancellation is
 * a coroutine concern and must never be classified as an AI error.
 */
fun Throwable.toAiError(): AiError {
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 4) {
        when (t) {
            is TimeoutCancellationException -> return AiError.Timeout
            is CancellationException -> return AiError.Unknown(t.message)
        }
        val msg = (t.message ?: "").lowercase()
        when {
            "429" in msg ||
                "resource_exhausted" in msg ||
                "resource exhausted" in msg ||
                "quota" in msg -> return AiError.QuotaExceeded
            "unavailable" in msg ||
                "unknownhost" in msg ||
                "unable to resolve" in msg ||
                "failed to connect" in msg ||
                "network" in msg -> return AiError.Network
        }
        t = t.cause
        depth++
    }
    return AiError.Unknown(this.message)
}

fun AiError.friendlyMessage(): String = when (this) {
    is AiError.QuotaExceeded ->
        "Gemini's usage limit has been reached. It will reset later — " +
            "your news feed keeps working normally."
    is AiError.Network ->
        "Couldn't reach Gemini. Check your connection and try again."
    is AiError.Timeout ->
        "The AI request timed out. Please try again."
    is AiError.Unknown ->
        "This AI feature hit a snag. Please try again."
}
