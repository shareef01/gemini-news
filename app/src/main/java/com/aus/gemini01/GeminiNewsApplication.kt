package com.aus.gemini01

import android.app.Application
import com.google.firebase.FirebaseApp

/** Installs App Check for both foreground requests and background workers. */
class GeminiNewsApplication : Application() {
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
