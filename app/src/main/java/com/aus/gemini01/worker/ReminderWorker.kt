package com.aus.gemini01.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aus.gemini01.data.NewsRepository
import com.aus.gemini01.data.local.AppDatabase
import com.aus.gemini01.ui.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val newsRepository = NewsRepository(database.newsDao())

        return try {
            val bookmarks = newsRepository.getAllBookmarks().first()

            if (bookmarks.isNotEmpty()) {
                val randomArticle = bookmarks.random()

                // Explicit MainActivity intent with URI data — not a public BROWSABLE route.
                NotificationHelper.showNotification(
                    applicationContext,
                    "Catch up on your reading!",
                    "Don't forget to read: ${randomArticle.title}",
                    notificationId = 1002,
                    deepLink = "newsapp://category/bookmarks".toUri()
                )
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount > 3) Result.failure() else Result.retry()
        }
    }
}
