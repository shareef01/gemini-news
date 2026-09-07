package com.aus.gemini01.ui

import com.aus.gemini01.data.Article

internal data class ThemeParseResult(
    val themes: Map<String, List<Article>>,
    val skippedLines: Int
)

internal fun parseSmartThemesResponse(
    responseText: String,
    articles: List<Article>
): ThemeParseResult {
    val themes = LinkedHashMap<String, List<Article>>()
    var skipped = 0

    responseText.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEach

        val colonIdx = line.indexOf(':')
        if (colonIdx <= 0) {
            skipped++
            return@forEach
        }

        val themeName = line.substring(0, colonIdx).trim()
        if (themeName.isBlank()) {
            skipped++
            return@forEach
        }

        val indices = line.substring(colonIdx + 1)
            .split(",")
            .map { it.trim() }
            .mapNotNull { token ->
                val n = token.toIntOrNull() ?: return@mapNotNull null
                if (n < 0 || n >= articles.size) null else n
            }
            .distinct()

        val themeArticles = indices.mapNotNull { articles.getOrNull(it) }

        if (themeArticles.isNotEmpty()) {
            themes[themeName] = themeArticles
        } else {
            skipped++
        }
    }

    return ThemeParseResult(themes, skipped)
}

internal data class LocationParseResult(
    val locations: List<NewsLocation>,
    val skippedLines: Int
)

/**
 * Parses Gemini location lines of the form:
 * `ARTICLE_INDEX|LOCATION_NAME|LATITUDE|LONGITUDE`
 *
 * Indexes are **zero-based** into [articles]. Title and URL are taken only from
 * the source article — model output never supplies authoritative identity.
 * Malformed rows are skipped; finite coordinates in valid ranges are required.
 */
internal fun parseLocationsResponse(
    responseText: String,
    articles: List<Article>,
    maxLocations: Int = 15
): LocationParseResult {
    val locations = mutableListOf<NewsLocation>()
    var skipped = 0
    val seenKeys = mutableSetOf<String>()

    responseText.lines().forEach { rawLine ->
        if (locations.size >= maxLocations) return@forEach

        val line = rawLine.trim()
        if (line.isBlank()) return@forEach

        val parts = line.split("|").map { it.trim() }
        if (parts.size < 4) {
            skipped++
            return@forEach
        }

        val index = parts[0].toIntOrNull()
        val name = parts[1]
        val lat = parts[2].toDoubleOrNull()
        val lng = parts[3].toDoubleOrNull()

        if (index == null || name.isBlank() || lat == null || lng == null) {
            skipped++
            return@forEach
        }
        if (!lat.isFinite() || !lng.isFinite()) {
            skipped++
            return@forEach
        }
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            skipped++
            return@forEach
        }
        if (index < 0 || index >= articles.size) {
            skipped++
            return@forEach
        }

        val article = articles[index]
        val dedupeKey = "${article.url}|$name|$lat|$lng"
        if (!seenKeys.add(dedupeKey)) {
            return@forEach
        }

        locations.add(
            NewsLocation(
                name = name,
                latitude = lat,
                longitude = lng,
                articleTitle = article.title,
                articleUrl = article.url
            )
        )
    }

    return LocationParseResult(locations, skipped)
}
