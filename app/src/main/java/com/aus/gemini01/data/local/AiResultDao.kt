package com.aus.gemini01.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class AiKindCount(val kind: String, val count: Int)

@Dao
interface AiResultDao {
    @Query("SELECT * FROM ai_results WHERE cacheKey = :key")
    suspend fun get(key: String): AiResultEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AiResultEntity)

    @Query("SELECT kind, COUNT(*) as count FROM ai_results GROUP BY kind")
    fun countsByKind(): Flow<List<AiKindCount>>

    @Query("DELETE FROM ai_results WHERE createdAt < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query("DELETE FROM ai_results")
    suspend fun clearAll()
}
