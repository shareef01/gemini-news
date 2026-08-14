package com.aus.gemini01.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aus.gemini01.data.Article
import com.aus.gemini01.data.Source

@Entity(tableName = "history_articles")
data class HistoryArticleEntity(
    @PrimaryKey val url: String,
    val viewedAt: Long,
    val sourceName: String,
    val author: String?,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?
)

fun Article.toHistoryEntity(timestamp: Long) = HistoryArticleEntity(
    url = url,
    viewedAt = timestamp,
    sourceName = source.name,
    author = author,
    title = title,
    description = description,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content
)

fun HistoryArticleEntity.toDomain() = Article(
    source = Source(name = sourceName),
    author = author,
    title = title,
    description = description,
    url = url,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content
)
