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

internal fun parseLocationsResponse(responseText: String): LocationParseResult {
    val locations = mutableListOf<NewsLocation>()
    var skipped = 0

    responseText.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) return@forEach

        val parts = line.split("|").map { it.trim() }
        if (parts.size < 5) {
            skipped++
            return@forEach
        }

        val name = parts[0]
        val lat = parts[1].toDoubleOrNull()
        val lng = parts[2].toDoubleOrNull()

        if (name.isBlank() || lat == null || lng == null) {
            skipped++
            return@forEach
        }

        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            skipped++
            return@forEach
        }

        locations.add(NewsLocation(name, lat, lng, parts[3], parts[4]))
    }

    return LocationParseResult(locations, skipped)
}