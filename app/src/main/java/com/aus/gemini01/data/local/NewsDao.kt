package com.aus.gemini01.data.local

import androidx.room.*
import com.aus.gemini01.data.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM articles ORDER BY savedAt DESC")
    fun getAllBookmarks(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(article: ArticleEntity)

    @Delete
    suspend fun deleteBookmark(article: ArticleEntity)

    // Caching Methods
    @Query("SELECT * FROM cached_articles WHERE category = :category AND country = :country ORDER BY feedOrder ASC")
    suspend fun getCachedArticles(category: String, country: String = "us"): List<CachedArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedArticles(articles: List<CachedArticleEntity>)

    @Query("DELETE FROM cached_articles WHERE category = :category AND country = :country")
    suspend fun deleteCachedArticlesByCategory(category: String, country: String = "us")

    @Query("SELECT MIN(cachedAt) FROM cached_articles WHERE category = :category AND country = :country")
    suspend fun getOldestCachedAt(category: String, country: String = "us"): Long?

    @Query("DELETE FROM cached_articles WHERE cachedAt < :cutoffEpochMs")
    suspend fun deleteCachedArticlesOlderThan(cutoffEpochMs: Long)

    @Transaction
    suspend fun replaceCachedArticles(
        category: String,
        country: String = "us",
        articles: List<CachedArticleEntity>,
        cutoffEpochMs: Long
    ) {
        deleteCachedArticlesOlderThan(cutoffEpochMs)
        deleteCachedArticlesByCategory(category, country)
        insertCachedArticles(articles)
    }

    // History Methods
    @Query("SELECT * FROM history_articles ORDER BY viewedAt DESC")
    fun getAllHistory(): Flow<List<HistoryArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(article: HistoryArticleEntity)

    @Query("DELETE FROM history_articles WHERE url NOT IN (SELECT url FROM history_articles ORDER BY viewedAt DESC LIMIT :limit)")
    suspend fun trimHistory(limit: Int)

    @Transaction
    suspend fun insertAndTrimHistory(article: HistoryArticleEntity, limit: Int) {
        insertHistory(article)
        trimHistory(limit)
    }

    @Query("DELETE FROM cached_articles")
    suspend fun clearCache()

    @Query("DELETE FROM history_articles")
    suspend fun clearHistory()
}
