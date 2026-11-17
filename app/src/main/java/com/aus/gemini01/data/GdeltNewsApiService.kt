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
    val safeUrl = url.trim()
    val safeTitle = title.trim()
    if (safeTitle.isEmpty() || !(safeUrl.startsWith("https://") || safeUrl.startsWith("http://"))) {
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

internal fun gdeltSearchQuery(query: String, countryCode: String): String {
    val terms = query
        .replace(Regex("[^\\p{L}\\p{N}\\s'-]"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
        .take(200)
        .ifBlank { "news" }
    require(countryCode.equals("us", ignoreCase = true)) { "Only US news is enabled in this build." }
    val sourceCountry = "unitedstates"
    return "\"$terms\" sourcecountry:$sourceCountry"
}
