package com.aus.gemini01.ui

import java.net.InetAddress
import java.net.URI
import java.util.Locale

/**
 * Normalizes an article image URL for loading. Rejects malformed URLs,
 * credentials, non-web schemes, and obvious local-network destinations.
 * Upgrades http:// to https:// since most hosts serve the same asset over TLS.
 */
internal fun safeImageUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val safeUrl = safeArticleUrl(raw) ?: return null
    val uri = runCatching { URI(safeUrl) }.getOrNull() ?: return null
    val host = uri.host?.trim('[', ']')?.substringBefore('%')?.lowercase(Locale.ROOT) ?: return null

    if (
        host == "localhost" ||
        host.endsWith(".localhost") ||
        host.endsWith(".local") ||
        host.endsWith(".internal") ||
        host.endsWith(".lan") ||
        host == "home.arpa"
    ) {
        return null
    }

    // Parse only IP-literal-shaped values, so ordinary hostnames never trigger
    // DNS resolution on the UI thread. This blocks direct loopback/private IPs.
    val looksLikeIpLiteral = host.contains(':') || host.matches(Regex("[0-9.]+"))
    if (looksLikeIpLiteral) {
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
        val isIpv6UniqueLocal = address.address.size == 16 &&
            (address.address[0].toInt() and 0xfe) == 0xfc
        if (
            address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            isIpv6UniqueLocal
        ) {
            return null
        }
    }

    return safeUrl
}
