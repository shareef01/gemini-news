package com.aus.gemini01.ui

/**
 * Normalizes an article image URL for loading. Returns null when the URL is
 * unsafe or uses a scheme Coil shouldn't touch (data:, file:, content:, etc.).
 * Upgrades http:// to https:// since most hosts serve the same asset over TLS.
 */
internal fun safeImageUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val upgraded = raw.replaceFirst("http://", "https://")
    return if (upgraded.startsWith("https://")) upgraded else null
}