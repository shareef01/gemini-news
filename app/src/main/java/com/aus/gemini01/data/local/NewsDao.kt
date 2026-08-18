package com.aus.gemini01.data.local

import androidx.room.*
import com.aus.gemini01.data.Article
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query("SELECT * FROM articles")
    fun getAllBookmarks(): Flow<List<ArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(article: ArticleEntity)

    @Delete
    suspend fun deleteBookmark(article: ArticleEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM articles WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    // Caching Methods
    @Query("SELECT * FROM cached_articles WHERE category = :category")
    suspend fun getCachedArticles(category: String): List<CachedArticleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedArticles(articles: List<CachedArticleEntity>)

    @Query("DELETE FROM cached_articles WHERE category = :category")
    suspend fun deleteCachedArticlesByCategory(category: String)

    @Query("DELETE FROM cached_articles WHERE publishedAt < :cutoff")
    suspend fun deleteCachedArticlesOlderThan(cutoff: String)

    // History Methods
    @Query("SELECT * FROM history_articles ORDER BY viewedAt DESC")
    fun getAllHistory(): Flow<List<HistoryArticleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(article: HistoryArticleEntity)

    @Query("DELETE FROM history_articles WHERE url NOT IN (SELECT url FROM history_articles ORDER BY viewedAt DESC LIMIT :limit)")
    suspend fun trimHistory(limit: Int)

    @Query("DELETE FROM cached_articles")
    suspend fun clearCache()

    @Query("DELETE FROM history_articles")
    suspend fun clearHistory()
}
