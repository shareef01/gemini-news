package com.aus.gemini01

import java.net.URLDecoder

/** Parsed `newsapp://` deep link targets. */
sealed class NewsDeepLink {
    data class Category(val name: String) : NewsDeepLink()
    data class Search(val query: String) : NewsDeepLink()
}

/**
 * Pure deep-link parsing shared by MainActivity and unit tests.
 * Returns null for unknown hosts, empty paths, or malformed encoding.
 */
fun parseNewsDeepLink(
    scheme: String?,
    host: String?,
    lastPathSegment: String?
): NewsDeepLink? {
    if (scheme != "newsapp" || host.isNullOrBlank()) return null
    return when (host) {
        "category" -> {
            val category = lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            NewsDeepLink.Category(category)
        }
        "search" -> {
            val raw = lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            val query = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (query.isBlank()) null else NewsDeepLink.Search(query)
        }
        else -> null
    }
}
