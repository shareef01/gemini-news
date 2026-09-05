package com.aus.gemini01.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ArticleEntity::class,
        CachedArticleEntity::class,
        HistoryArticleEntity::class,
        AiResultEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
    abstract fun aiResultDao(): AiResultDao

    companion object {
        // v5: adds ai_results for persistent Gemini caching (summaries, reader
        // views, trend/theme analyses) keyed by url+kind+model+prompt version.
        // Exposed for migration tests — do not add destructive fallbacks here;
        // missing migrations must fail loudly rather than wipe bookmarks/history.
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_results` (" +
                        "`cacheKey` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`articleUrl` TEXT, " +
                        "`result` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`cacheKey`))"
                )
            }
        }

        /** v6: records cache fetch time separately from publisher publication time. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cached_articles` ADD COLUMN `cachedAt` INTEGER NOT NULL DEFAULT 0")
                // Existing rows have unknown age. Preserve them as usable cache and
                // establish a conservative baseline until the next successful fetch.
                db.execSQL("UPDATE `cached_articles` SET `cachedAt` = ${System.currentTimeMillis()} WHERE `cachedAt` = 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "news_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
