package com.aus.gemini01.ui

/**
 * Normalizes a publisher article URL for WebView loading. HTTPS only —
 * cleartext and non-web schemes are rejected (matches [safeImageUrl] policy).
 */
internal fun safeArticleUrl(raw: String?): String? {
    val candidate = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val upgraded = when {
        candidate.startsWith("http://", ignoreCase = true) ->
            "https://${candidate.substringAfter("://")}"
        candidate.startsWith("https://", ignoreCase = true) -> candidate
        else -> return null
    }

    // URI parsing is intentionally followed by structural checks: parsers may
    // accept strings such as "https://" without throwing.
    val uri = runCatching { java.net.URI(upgraded) }.getOrNull() ?: return null
    return upgraded.takeIf {
        uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.port <= 65535
    }
}
