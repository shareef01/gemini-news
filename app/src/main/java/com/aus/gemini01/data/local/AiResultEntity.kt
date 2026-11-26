package com.aus.gemini01.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_results",
    indices = [Index(value = ["kind", "createdAt"])]
)
data class AiResultEntity(
    @PrimaryKey val cacheKey: String,
    val kind: String,
    val articleUrl: String?,
    val result: String,
    val createdAt: Long
)
