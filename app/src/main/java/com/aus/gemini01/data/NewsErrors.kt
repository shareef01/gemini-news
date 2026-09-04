package com.aus.gemini01.data

import retrofit2.HttpException

/** User-facing copy for NewsAPI / feed failures (keeps HTTP detail out of the UI). */
fun newsFeedErrorMessage(error: Throwable): String {
    val http = error as? HttpException
    return when {
        http?.code() == 429 ->
            "NewsAPI rate limit reached. Your cached stories are unchanged — try again later."
        http != null && http.code() in 400..499 ->
            "Couldn't refresh the news feed (HTTP ${http.code()})."
        else -> error.localizedMessage ?: "Couldn't load news."
    }
}
