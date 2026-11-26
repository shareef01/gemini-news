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
    version = 8,
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

        /** v7: adds country dimension to cached_articles composite primary key for multi-region cache isolation. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_articles_temp` (" +
                        "`url` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`country` TEXT NOT NULL DEFAULT 'us', " +
                        "`sourceName` TEXT NOT NULL, " +
                        "`author` TEXT, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT, " +
                        "`urlToImage` TEXT, " +
                        "`publishedAt` TEXT NOT NULL, " +
                        "`content` TEXT, " +
                        "`cachedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`url`, `category`, `country`))"
                )
                db.execSQL(
                    "INSERT INTO `cached_articles_temp` (" +
                        "`url`, `category`, `country`, `sourceName`, `author`, `title`, `description`, `urlToImage`, `publishedAt`, `content`, `cachedAt`" +
                    ") SELECT `url`, `category`, 'us', `sourceName`, `author`, `title`, `description`, `urlToImage`, `publishedAt`, `content`, `cachedAt` FROM `cached_articles`"
                )
                db.execSQL("DROP TABLE `cached_articles`")
                db.execSQL("ALTER TABLE `cached_articles_temp` RENAME TO `cached_articles`")
            }
        }

        /** v8: preserves feed rank on cached_articles, bookmark savedAt order, and indexes ai_results TTL lookups. */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `cached_articles` ADD COLUMN `feedOrder` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `articles` ADD COLUMN `savedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_results_kind_createdAt` ON `ai_results` (`kind`, `createdAt`)")
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
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
