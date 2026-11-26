package com.aus.gemini01.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.aus.gemini01.data.Article
import com.aus.gemini01.data.Source

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val url: String,
    val sourceName: String,
    val author: String?,
    val title: String,
    val description: String?,
    val urlToImage: String?,
    val publishedAt: String,
    val content: String?,
    @ColumnInfo(defaultValue = "0") val savedAt: Long = 0L
)

fun Article.toEntity(savedAt: Long = 0L) = ArticleEntity(
    url = url,
    sourceName = source.name,
    author = author,
    title = title,
    description = description,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content,
    savedAt = savedAt
)

fun ArticleEntity.toDomain() = Article(
    source = Source(name = sourceName),
    author = author,
    title = title,
    description = description,
    url = url,
    urlToImage = urlToImage,
    publishedAt = publishedAt,
    content = content
)
