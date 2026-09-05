package com.aus.gemini01.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented 4→5 migration: bookmarks/history/cache tables survive and
 * `ai_results` is created. Requires device/emulator (not run in unit-test CI).
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration4To5Test {

    private val testDb = "migration-test-news"
    private val cacheMigrationDb = "migration-test-news-cache"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_preservesBookmarksAndCreatesAiResults() {
        helper.createDatabase(testDb, 4).apply {
            execSQL(
                """
                INSERT INTO articles (url, sourceName, author, title, description, urlToImage, publishedAt, content)
                VALUES ('https://example.com/a', 'Src', NULL, 'Saved', NULL, NULL, '2026-01-01T00:00:00Z', NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO history_articles (url, viewedAt, sourceName, author, title, description, urlToImage, publishedAt, content)
                VALUES ('https://example.com/h', 1, 'Src', NULL, 'Read', NULL, NULL, '2026-01-01T00:00:00Z', NULL)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(testDb, 5, true, AppDatabase.MIGRATION_4_5).apply {
            query("SELECT title FROM articles WHERE url = 'https://example.com/a'").use { c ->
                assert(c.moveToFirst())
                assert(c.getString(0) == "Saved")
            }
            query("SELECT title FROM history_articles WHERE url = 'https://example.com/h'").use { c ->
                assert(c.moveToFirst())
                assert(c.getString(0) == "Read")
            }
            query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='ai_results'"
            ).use { c ->
                assert(c.moveToFirst())
            }
            close()
        }
    }

    @Test
    fun migrate5To6_addsAndBackfillsCacheTimestamp() {
        helper.createDatabase(cacheMigrationDb, 5).apply {
            execSQL(
                """
                INSERT INTO cached_articles (
                    url, category, sourceName, author, title, description,
                    urlToImage, publishedAt, content
                ) VALUES (
                    'https://example.com/cached', 'general', 'Src', NULL, 'Cached',
                    NULL, NULL, '2026-01-01T00:00:00Z', NULL
                )
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            cacheMigrationDb,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        ).apply {
            query("SELECT cachedAt FROM cached_articles WHERE url = 'https://example.com/cached'").use { c ->
                assert(c.moveToFirst())
                assert(c.getLong(0) > 0L)
            }
            close()
        }
    }
}
