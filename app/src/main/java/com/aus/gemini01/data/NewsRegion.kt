package com.aus.gemini01.data

/**
 * Represents a supported news region/country for headline curation.
 */
data class NewsRegion(
    val countryCode: String,
    val languageCode: String?,
    val displayName: String
) {
    companion object {
        val SUPPORTED_REGIONS = listOf(
            NewsRegion("us", "en", "United States"),
            NewsRegion("gb", "en", "United Kingdom"),
            NewsRegion("ca", "en", "Canada"),
            NewsRegion("au", "en", "Australia"),
            NewsRegion("in", "en", "India"),
            NewsRegion("de", "de", "Germany"),
            NewsRegion("fr", "fr", "France")
        )

        val DEFAULT = SUPPORTED_REGIONS.first()

        fun fromCountryCode(code: String): NewsRegion =
            SUPPORTED_REGIONS.firstOrNull { it.countryCode.equals(code, ignoreCase = true) }
                ?: DEFAULT
    }
}
