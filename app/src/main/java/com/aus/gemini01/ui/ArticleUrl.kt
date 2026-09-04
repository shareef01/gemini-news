package com.aus.gemini01.ui

/**
 * Normalizes a publisher article URL for WebView loading. HTTPS only —
 * cleartext and non-web schemes are rejected (matches [safeImageUrl] policy).
 */
internal fun safeArticleUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val upgraded = raw.replaceFirst("http://", "https://")
    return if (upgraded.startsWith("https://")) upgraded else null
}
