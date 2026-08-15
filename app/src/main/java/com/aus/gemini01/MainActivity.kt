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
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val viewModel: NewsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        installAppCheckProvider()
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

    /**
     * Attests requests to Vertex AI in Firebase (gemini-flash-latest). Release builds
     * use Play Integrity; debug builds use the debug provider (its auto-generated
     * secret is logged to Logcat and must be registered as a debug token when
     * App Check enforcement is enabled).
     */
    private fun installAppCheckProvider() {
        val factory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
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
        intent?.data?.let { uri ->
            if (uri.scheme == "newsapp") {
                when (uri.host) {
                    "category" -> {
                        val category = uri.lastPathSegment
                        if (category != null) {
                            viewModel.fetchNews(category)
                        }
                    }
                    "search" -> {
                        val query = uri.lastPathSegment?.let {
                            try {
                                URLDecoder.decode(it, "UTF-8")
                            } catch (e: IllegalArgumentException) {
                                // Malformed percent-encoding must not crash the app.
                                null
                            }
                        }
                        if (query != null) {
                            viewModel.searchNews(query)
                        }
                    }
                }
            }
        }
    }
}
