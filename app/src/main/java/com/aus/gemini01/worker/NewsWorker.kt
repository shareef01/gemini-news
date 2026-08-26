package com.aus.gemini01.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aus.gemini01.data.NewsRepository
import com.aus.gemini01.data.SettingsRepository
import com.aus.gemini01.data.local.AppDatabase
import com.aus.gemini01.ui.NotificationHelper
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

class NewsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val newsRepository = NewsRepository(database.newsDao())
        val settingsRepository = SettingsRepository(applicationContext)

        return try {
            val countryCode = settingsRepository.countryCode.first()
            // Free tier: 100 req/day cap. Polling every hour burns ~24/day before
            // the user even opens the app. Skip the background fetch.
            if (settingsRepository.newsApiFreeTier.first()) {
                return Result.success()
            }
            // Don't surface cached articles as "breaking news" - that would notify
            // users about stale stories when the network is unreachable.
            val result = newsRepository.getTopHeadlines(
                countryCode = countryCode,
                allowOfflineFallback = false
            )

            if (result.articles.isNotEmpty()) {
                val latestArticle = result.articles[0]
                val lastNotifiedUrl = settingsRepository.lastNotifiedUrl.first()

                if (latestArticle.url != lastNotifiedUrl) {
                    NotificationHelper.showNotification(
                        applicationContext,
                        "Breaking News",
                        latestArticle.title,
                        notificationId = 1001
                    )
                    settingsRepository.setLastNotifiedUrl(latestArticle.url)
                }
            }
            Result.success()
        } catch (e: HttpException) {
            // 4xx (e.g. bad/rotated API key) will never succeed by retrying.
            if (e.code() in 400..499) Result.failure() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
