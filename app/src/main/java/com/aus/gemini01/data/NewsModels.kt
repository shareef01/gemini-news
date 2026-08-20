package com.aus.gemini01.data

import kotlinx.serialization.Serializable

@Serializable
data class NewsResponse(
    val status: String = "ok",
    val totalResults: Int? = 0,
    val articles: List<Article> = emptyList(),
    val code: String? = null,
    val message: String? = null
)

@Serializable
data class Article(
    val source: Source = Source(),
    val author: String? = null,
    val title: String = "",
    val description: String? = null,
    val url: String = "",
    val urlToImage: String? = null,
    val publishedAt: String = "",
    val content: String? = null
) : java.io.Serializable

@Serializable
data class Source(
    val id: String? = null,
    val name: String = "Unknown"
) : java.io.Serializable
