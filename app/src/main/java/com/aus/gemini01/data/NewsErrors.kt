package com.aus.gemini01.data

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/** User-facing copy for NewsAPI / feed failures (keeps HTTP detail out of the UI). */
fun newsFeedErrorMessage(error: Throwable): String {
    val http = error as? HttpException
    return when {
        error is MissingNewsApiKeyException ->
            "News API key is not configured. Add NEWS_API_KEY to local.properties and rebuild."
        http?.code() == 429 ->
            "NewsAPI rate limit reached. Your cached stories are unchanged — try again later."
        http != null && http.code() in 400..499 ->
            "Couldn't refresh the news feed. Check the API configuration and try again."
        error is SocketTimeoutException || error is kotlinx.coroutines.TimeoutCancellationException ->
            "The news feed took too long to respond. Check your connection and try again."
        error is IOException ->
            "Couldn't reach the news feed. Check your connection; saved stories may still be available."
        else -> "Couldn't load the news feed. Please try again."
    }
}

/**
 * For mixed AI+feed flows (For You, Smart Themes): classify NewsAPI / network
 * failures with [newsFeedErrorMessage] instead of a generic AI snag copy.
 */
fun feedOrAiErrorMessage(error: Throwable): String = when (error) {
    is MissingNewsApiKeyException, is HttpException, is IOException -> newsFeedErrorMessage(error)
    else -> "This AI feature hit a snag. Please try again."
}
