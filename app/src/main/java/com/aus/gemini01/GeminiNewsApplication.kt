package com.aus.gemini01

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aus.gemini01.ui.PublicNetworkDns
import com.google.firebase.FirebaseApp
import okhttp3.OkHttpClient

/** Installs App Check for both foreground requests and background workers. */
class GeminiNewsApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .dns(PublicNetworkDns())
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
