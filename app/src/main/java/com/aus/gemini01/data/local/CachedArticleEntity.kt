package com.aus.gemini01.data.local

import androidx.room.Entity
import com.aus.gemini01.data.Article
import com.aus.gemini01.data.Source

@Entity(tableName = "cached_articles", primaryKeys = ["url", "category"])
data class CachedArticleEntity(
    val url: String,
    val category: String,
    val sourceName: String,
    val author: String?,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?,
    /** Epoch time when this feed row was fetched, independent of publisher time. */
    val cachedAt: Long
)

fun Article.toCachedEntity(category: String, cachedAt: Long = System.currentTimeMillis()) = CachedArticleEntity(
    url = url,
    category = category,
    sourceName = source.name,
    author = author,
    title = title,
    description = description,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content,
    cachedAt = cachedAt
)

fun CachedArticleEntity.toDomain() = Article(
    source = Source(name = sourceName),
    author = author,
    title = title,
    description = description,
    url = url,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content
)
