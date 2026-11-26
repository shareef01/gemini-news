package com.aus.gemini01

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aus.gemini01.data.NewsRepository
import com.aus.gemini01.data.SettingsRepository
import com.aus.gemini01.data.ai.AiRepository
import com.aus.gemini01.data.ai.DataStoreAiTelemetry
import com.aus.gemini01.data.local.AppDatabase
import com.aus.gemini01.ui.PublicNetworkDns
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Installs App Check and provides process-scoped repositories for UI and background workers. */
class GeminiNewsApplication : Application(), SingletonImageLoader.Factory {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val newsRepository: NewsRepository by lazy { NewsRepository(database.newsDao()) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val aiRepository: AiRepository by lazy {
        AiRepository(
            dao = database.aiResultDao(),
            telemetry = DataStoreAiTelemetry(this, applicationScope),
            scope = applicationScope
        )
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .dns(PublicNetworkDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        // FirebaseInitProvider normally creates the default app before this
        // callback. Explicit initialization also covers test and custom process
        // setups; skip App Check when no Firebase configuration is packaged.
        if (FirebaseApp.initializeApp(this) != null) {
            AppCheckInstallerImpl().install()
        }
    }
}
