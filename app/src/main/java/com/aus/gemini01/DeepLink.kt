package com.aus.gemini01

/**
 * Parsed deep-link targets used by explicit MainActivity intents
 * (shortcuts, notifications). There is no public BROWSABLE custom-scheme
 * intent filter — external apps cannot launch these routes via URI alone.
 */
sealed class NewsDeepLink {
    data class Category(val name: String) : NewsDeepLink()
    data class Search(val query: String) : NewsDeepLink()
    data class Article(val url: String) : NewsDeepLink()
}

/**
 * Pure deep-link parsing shared by MainActivity and unit tests.
 *
 * [lastPathSegment] and [articleUrl] must already be decoded exactly once
 * (Android [android.net.Uri.getLastPathSegment] / [android.net.Uri.getQueryParameter]).
 * This function does **not** apply a second [java.net.URLDecoder] pass — doing so
 * would turn `%2525` into `%` and treat `+` as a space (form-urlencoded semantics
 * that URI path segments do not use).
 *
 * Returns null for unknown hosts, empty paths, or invalid article URLs.
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
            val query = lastPathSegment?.takeIf { it.isNotBlank() } ?: return null
            if (query.length > MAX_SEARCH_LENGTH) null
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
