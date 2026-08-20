package com.aus.gemini01.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_results")
data class AiResultEntity(
    @PrimaryKey val cacheKey: String,
    val kind: String,
    val articleUrl: String?,
    val result: String,
    val createdAt: Long
)
