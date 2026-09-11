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
    val verifiedInSource: Boolean
)

data class ArticleInsight(
    val summary: String,
    val takeaways: List<String>,
    val sentiment: SentimentAnalysis?,
    val entities: List<String>,
    val evidence: List<ArticleEvidence>
)
