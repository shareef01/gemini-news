package com.aus.gemini01

import java.net.URLDecoder

/** Parsed `newsapp://` deep link targets. */
sealed class NewsDeepLink {
    data class Category(val name: String) : NewsDeepLink()
    data class Search(val query: String) : NewsDeepLink()
    data class Article(val url: String) : NewsDeepLink()
}

/**
 * Pure deep-link parsing shared by MainActivity and unit tests.
 * Returns null for unknown hosts, empty paths, or malformed encoding.
 */
fun parseNewsDeepLink(
    scheme: String?,
    host: String?,
    lastPathSegment: String?,
    articleUrl: String? = null
): NewsDeepLink? {
    if (scheme != "newsapp" || host.isNullOrBlank()) return null
    return when (host) {
        "category" -> {
            val category = lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            category.takeIf { it in SUPPORTED_CATEGORIES }?.let(NewsDeepLink::Category)
        }
        "search" -> {
            val raw = lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            val query = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (query.isBlank() || query.length > MAX_SEARCH_LENGTH) null
            else NewsDeepLink.Search(query)
        }
        "article" -> articleUrl?.takeIf(::isSafeArticleLink)?.let(NewsDeepLink::Article)
        else -> null
    }
}

private const val MAX_SEARCH_LENGTH = 200
private val SUPPORTED_CATEGORIES = setOf(
    "general", "technology", "business", "entertainment", "sports", "science", "health",
    "for_you", "smart", "bookmarks", "history"
)

private fun isSafeArticleLink(raw: String): Boolean {
    if (raw.length > 2_048) return false
    val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return false
    return uri.scheme.lowercase() in setOf("http", "https") &&
        !uri.host.isNullOrBlank() &&
        uri.userInfo == null &&
        uri.port <= 65_535
}
