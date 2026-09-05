package com.aus.gemini01.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * JVM (Robolectric) proof that MIGRATION_4_5 is additive: bookmarks/history
 * rows survive and `ai_results` is created. Does not depend on packaged
 * schema JSON assets (those remain for device MigrationTestHelper).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class AppDatabaseMigration4To5RobolectricTest {

    @Test
    fun migrate4To5_preservesBookmarksAndCreatesAiResults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-robolectric-news")
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createV4Schema(db)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        openHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO articles (url, sourceName, author, title, description, urlToImage, publishedAt, content)
                VALUES ('https://example.com/a', 'Src', NULL, 'Saved', NULL, NULL, '2026-01-01T00:00:00Z', NULL)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO history_articles (url, viewedAt, sourceName, author, title, description, urlToImage, publishedAt, content)
                VALUES ('https://example.com/h', 1, 'Src', NULL, 'Read', NULL, NULL, '2026-01-01T00:00:00Z', NULL)
                """.trimIndent()
            )

            AppDatabase.MIGRATION_4_5.migrate(db)

            db.query("SELECT title FROM articles WHERE url = 'https://example.com/a'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Saved", c.getString(0))
            }
            db.query("SELECT title FROM history_articles WHERE url = 'https://example.com/h'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Read", c.getString(0))
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='ai_results'"
            ).use { c ->
                assertTrue(c.moveToFirst())
            }
        }
    }

    @Test
    fun migrate5To6_addsAndBackfillsCacheTimestamp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-robolectric-news-v6")
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    createV4Schema(db)
                    AppDatabase.MIGRATION_4_5.migrate(db)
                }

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        openHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO cached_articles (url, category, sourceName, author, title, description, urlToImage, publishedAt, content)
                VALUES ('https://example.com/c', 'general', 'Src', NULL, 'Cached', NULL, NULL, 'not-a-date', NULL)
                """.trimIndent()
            )

            AppDatabase.MIGRATION_5_6.migrate(db)

            db.query("SELECT cachedAt FROM cached_articles WHERE url = 'https://example.com/c'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.getLong(0) > 0L)
            }
        }
    }

    private fun createV4Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `articles` (" +
                "`url` TEXT NOT NULL, `sourceName` TEXT NOT NULL, `author` TEXT, " +
                "`title` TEXT NOT NULL, `description` TEXT, `urlToImage` TEXT, " +
                "`publishedAt` TEXT NOT NULL, `content` TEXT, PRIMARY KEY(`url`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_articles` (" +
                "`url` TEXT NOT NULL, `category` TEXT NOT NULL, `sourceName` TEXT NOT NULL, " +
                "`author` TEXT, `title` TEXT NOT NULL, `description` TEXT, `urlToImage` TEXT, " +
                "`publishedAt` TEXT NOT NULL, `content` TEXT, PRIMARY KEY(`url`, `category`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `history_articles` (" +
                "`url` TEXT NOT NULL, `viewedAt` INTEGER NOT NULL, `sourceName` TEXT NOT NULL, " +
                "`author` TEXT, `title` TEXT NOT NULL, `description` TEXT, `urlToImage` TEXT, " +
                "`publishedAt` TEXT NOT NULL, `content` TEXT, PRIMARY KEY(`url`))"
        )
    }
}
