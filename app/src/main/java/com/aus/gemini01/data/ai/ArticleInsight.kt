package com.aus.gemini01.data.ai

enum class SentimentTone {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}

data class SentimentAnalysis(
    val tone: SentimentTone,
    val explanation: String
)

data class ArticleEvidence(
    val quoteOrFact: String,
    val sourceField: String,
    /** True when a long token overlaps the selected source field; this is not factual verification. */
    val hasTextOverlap: Boolean
) {
    @Deprecated("Text overlap does not verify a claim against its source.")
    val verifiedInSource: Boolean
        get() = hasTextOverlap
}

data class ArticleInsight(
    val summary: String,
    val takeaways: List<String>,
    val sentiment: SentimentAnalysis?,
    val entities: List<String>,
    val evidence: List<ArticleEvidence>
)
