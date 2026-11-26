package com.aus.gemini01.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Small DOC API response model. The API supplies metadata and publisher links, not article text. */
@Serializable
internal data class GdeltResponse(
    val articles: List<GdeltArticle> = emptyList()
)

@Serializable
internal data class GdeltArticle(
    val url: String = "",
    val title: String = "",
    @SerialName("seendate") val seenDate: String = "",
    val domain: String = "",
    @SerialName("sourcecountry") val sourceCountry: String = ""
)

internal interface GdeltNewsApiService {
    @GET("api/v2/doc/doc")
    suspend fun articles(
        @Query("query") query: String,
        @Query("mode") mode: String = "ArtList",
        @Query("format") format: String = "json",
        @Query("maxrecords") maxRecords: Int = 75,
        @Query("timespan") timeSpan: String = "24h",
        @Query("sort") sort: String = "DateDesc"
    ): GdeltResponse
}

internal fun GdeltArticle.toDomainArticle(): Article? {
    val safeUrl = com.aus.gemini01.ui.safeArticleUrl(url) ?: return null
    val safeTitle = title.trim()
    if (safeTitle.isEmpty()) {
        return null
    }

    val publishedAt = runCatching {
        OffsetDateTime.parse(seenDate, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX", Locale.US))
            .toInstant()
            .toString()
    }.getOrDefault("")

    return Article(
        source = Source(name = domain.ifBlank { sourceCountry.ifBlank { "GDELT" } }),
        title = safeTitle,
        url = safeUrl,
        publishedAt = publishedAt
    )
}

internal fun gdeltTopHeadlinesQuery(category: String?, countryCode: String): String {
    val categoryTerms = when (category?.lowercase(Locale.ROOT)) {
        "business" -> "(business OR economy OR finance)"
        "entertainment" -> "(film OR music OR television OR entertainment)"
        "health" -> "(health OR medicine OR hospital)"
        "science" -> "(science OR research OR space)"
        "sports" -> "(sports OR football OR soccer OR basketball)"
        "technology" -> "(technology OR software OR cybersecurity)"
        else -> "(politics OR economy OR health OR science OR technology OR sports)"
    }
    require(countryCode.equals("us", ignoreCase = true)) { "Only US news is enabled in this build." }
    val sourceCountry = "unitedstates"
    return "$categoryTerms sourcecountry:$sourceCountry"
}

private val GDELT_BOOLEAN_KEYWORDS = setOf("or", "and", "not")

private fun sanitizeGdeltClause(raw: String): String {
    return raw
        .replace(Regex("[^\\p{L}\\p{N}\\s'-]"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() && it.lowercase(Locale.ROOT) !in GDELT_BOOLEAN_KEYWORDS }
        .joinToString(" ")
}

/**
 * Formats a user or multi-topic search string into a valid GDELT DOC 2.0 query.
 * Comma-separated topic lists (such as "For You" interests) become parenthesized
 * boolean OR clauses; single searches use unquoted keyword matching so short
 * terms like "AI" or multi-word non-adjacent queries return results cleanly.
 */
internal fun gdeltSearchQuery(query: String, countryCode: String): String {
    require(countryCode.equals("us", ignoreCase = true)) { "Only US news is enabled in this build." }
    val sourceCountry = "unitedstates"

    val rawClauses = query.take(250).split(",")
    val cleanedClauses = rawClauses
        .map(::sanitizeGdeltClause)
        .filter { it.isNotBlank() }
        .take(5)

    val expression = when {
        cleanedClauses.isEmpty() -> "news"
        cleanedClauses.size == 1 -> cleanedClauses.first().take(200)
        else -> {
            val formatted = cleanedClauses.map { clause ->
                if (' ' in clause && clause.length >= 5) "\"$clause\"" else clause
            }
            "(${formatted.joinToString(" OR ")})"
        }
    }
    return "$expression sourcecountry:$sourceCountry"
}

/** Formats comma-separated AI interest topics into an OR query for NewsAPI / Proxy search. */
internal fun formatMultiTopicSearchQuery(rawKeywords: String): String {
    val topics = rawKeywords
        .split(",")
        .map { it.replace(Regex("[^\\p{L}\\p{N}\\s'-]"), " ").trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() }
        .take(5)
    return when {
        topics.isEmpty() -> "news"
        topics.size == 1 -> topics.first()
        else -> topics.joinToString(" OR ") { topic ->
            if (' ' in topic) "\"$topic\"" else topic
        }
    }
}
