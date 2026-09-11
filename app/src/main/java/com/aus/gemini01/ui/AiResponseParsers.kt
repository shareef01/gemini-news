package com.aus.gemini01.ui

import com.aus.gemini01.data.Article
import com.aus.gemini01.data.ai.ArticleEvidence
import com.aus.gemini01.data.ai.ArticleInsight
import com.aus.gemini01.data.ai.SentimentAnalysis
import com.aus.gemini01.data.ai.SentimentTone

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

/**
 * Parses Gemini-generated article summaries into typed [ArticleInsight] structures.
 *
 * Validates evidence attribution against actual article text fields to catch
 * model hallucinations or fabricated quotes.
 */
internal fun parseArticleInsight(
    responseText: String,
    article: Article
): ArticleInsight {
    val lines = responseText.lines().map { it.trim() }
    val takeaways = mutableListOf<String>()
    val entities = mutableListOf<String>()
    var sentiment: SentimentAnalysis? = null
    var currentSection = ""
    val summaryLines = mutableListOf<String>()

    val sentimentRegex = Regex(
        """^(?:-|\*|•)?\s*(?:\*\*)?Sentiment(?:\*\*)?[:\s]+(\w+)(?:[\s—–-]+(.*))?""",
        RegexOption.IGNORE_CASE
    )

    for (line in lines) {
        if (line.isBlank()) continue

        val lower = line.lowercase()
        if (lower.contains("key takeaway") || lower.contains("takeaway")) {
            currentSection = "takeaways"
            continue
        } else if (lower.contains("sentiment") || lower.contains("tone")) {
            currentSection = "sentiment"
            sentimentRegex.find(line)?.let { match ->
                val toneStr = match.groupValues[1].uppercase()
                val tone = when {
                    toneStr.contains("POS") -> SentimentTone.POSITIVE
                    toneStr.contains("NEG") -> SentimentTone.NEGATIVE
                    else -> SentimentTone.NEUTRAL
                }
                val explanation = match.groupValues[2].trim()
                sentiment = SentimentAnalysis(tone, explanation)
            }
            continue
        } else if (lower.contains("key entit") || lower.contains("entities") || lower.contains("context")) {
            currentSection = "entities"
            continue
        } else if (line.startsWith("#")) {
            currentSection = "other"
            continue
        }

        // Clean bullet marker
        val content = line.removePrefix("-").removePrefix("*").removePrefix("•").trim()

        when (currentSection) {
            "takeaways" -> {
                if (content.isNotBlank()) {
                    takeaways.add(content)
                }
            }
            "sentiment" -> {
                if (sentiment == null) {
                    val match = sentimentRegex.find(line)
                    if (match != null) {
                        val toneStr = match.groupValues[1].uppercase()
                        val tone = when {
                            toneStr.contains("POS") -> SentimentTone.POSITIVE
                            toneStr.contains("NEG") -> SentimentTone.NEGATIVE
                            else -> SentimentTone.NEUTRAL
                        }
                        val explanation = match.groupValues[2].trim()
                        sentiment = SentimentAnalysis(tone, explanation)
                    } else if (content.isNotBlank()) {
                        val tone = when {
                            content.uppercase().contains("POSITIVE") -> SentimentTone.POSITIVE
                            content.uppercase().contains("NEGATIVE") -> SentimentTone.NEGATIVE
                            else -> SentimentTone.NEUTRAL
                        }
                        sentiment = SentimentAnalysis(tone, content)
                    }
                }
            }
            "entities" -> {
                if (content.isNotBlank()) {
                    entities.add(content)
                }
            }
            else -> {
                summaryLines.add(line)
            }
        }
    }

    // Evidence attribution validation
    val evidenceList = mutableListOf<ArticleEvidence>()
    val title = article.title.lowercase()
    val desc = (article.description ?: "").lowercase()
    val body = (article.content ?: "").lowercase()

    for (takeaway in takeaways) {
        val words = takeaway.lowercase()
            .split(Regex("""[\s,.;:!?()"]+"""))
            .filter { it.length > 4 }
        val inTitle = words.any { it in title }
        val inDesc = words.any { it in desc }
        val inBody = words.any { it in body }

        val sourceField = when {
            inDesc -> "description"
            inBody -> "content"
            inTitle -> "title"
            else -> "unverified"
        }
        evidenceList.add(
            ArticleEvidence(
                quoteOrFact = takeaway,
                sourceField = sourceField,
                verifiedInSource = sourceField != "unverified"
            )
        )
    }

    val finalSummary = if (summaryLines.isNotEmpty()) {
        summaryLines.joinToString("\n")
    } else {
        takeaways.firstOrNull() ?: article.description ?: article.title
    }

    return ArticleInsight(
        summary = finalSummary,
        takeaways = takeaways,
        sentiment = sentiment,
        entities = entities,
        evidence = evidenceList
    )
}
