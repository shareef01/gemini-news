package com.aus.gemini01

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aus.gemini01.ui.AdaptiveNewsScreen
import com.aus.gemini01.ui.NewsViewModel
import com.aus.gemini01.ui.NotificationHelper
import com.aus.gemini01.ui.theme.Gemini01Theme
import com.aus.gemini01.worker.NewsWorker
import com.aus.gemini01.worker.ReminderWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel: NewsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        AppCheckInstallerImpl().install(this)
        handleIntent(intent)
        NotificationHelper.createNotificationChannel(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.notificationsEnabled.collect { enabled ->
                    if (enabled) {
                        scheduleNewsWork()
                    } else {
                        cancelNewsWork()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.remindersEnabled.collect { enabled ->
                    if (enabled) {
                        scheduleReminderWork()
                    } else {
                        cancelReminderWork()
                    }
                }
            }
        }

        setContent {
            Gemini01Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AdaptiveNewsScreen(viewModel = viewModel)
                }
            }
        }
    }

    private fun scheduleNewsWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val newsWorkRequest = PeriodicWorkRequestBuilder<NewsWorker>(
            1, TimeUnit.HOURS // News API free tier has limits, so 1 hour is safer
        )
        .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "breaking_news_work",
            ExistingPeriodicWorkPolicy.KEEP,
            newsWorkRequest
        )
    }

    private fun cancelNewsWork() {
        WorkManager.getInstance(this).cancelUniqueWork("breaking_news_work")
    }

    private fun scheduleReminderWork() {
        val reminderRequest = PeriodicWorkRequestBuilder<ReminderWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reading_reminder_work",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )
    }

    private fun cancelReminderWork() {
        WorkManager.getInstance(this).cancelUniqueWork("reading_reminder_work")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        try {
            intent?.data?.let { uri ->
                when (val link = parseNewsDeepLink(
                    uri.scheme,
                    uri.host,
                    uri.lastPathSegment,
                    uri.getQueryParameter("url")
                )) {
                    is NewsDeepLink.Category -> viewModel.fetchNews(link.name)
                    is NewsDeepLink.Search -> viewModel.searchNews(link.query)
                    is NewsDeepLink.Article -> viewModel.openArticleFromDeepLink(link.url)
                    null -> Unit
                }
            }
        } catch (_: Exception) {
            // Hostile or framework-odd deep links must never crash the launcher.
        }
    }
}
