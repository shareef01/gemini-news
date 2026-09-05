package com.aus.gemini01.worker

import android.content.Context
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aus.gemini01.data.NewsRepository
import com.aus.gemini01.data.local.AppDatabase
import com.aus.gemini01.ui.NotificationHelper
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
                
                // Reuse existing notification logic but with a specific deep link for bookmarks
                NotificationHelper.showNotification(
                    applicationContext,
                    "Catch up on your reading!",
                    "Don't forget to read: ${randomArticle.title}",
                    notificationId = 1002,
                    deepLink = "newsapp://category/bookmarks".toUri()
                )
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount > 3) Result.failure() else Result.retry()
        }
    }
}
