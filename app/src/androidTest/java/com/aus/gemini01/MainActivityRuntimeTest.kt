package com.aus.gemini01

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device smoke coverage for launcher resilience and deep-link input handling. */
@RunWith(AndroidJUnit4::class)
class MainActivityRuntimeTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun launchActivity_doesNotCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(scenario.state != Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun hostileArticleDeepLink_doesNotCrashLauncher() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("newsapp://article?url=javascript%3Aalert(1)")
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertTrue(scenario.state != Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun validArticleDeepLink_doesNotCrashLauncher() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("newsapp://article?url=https%3A%2F%2Fexample.com%2Fstory")
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            assertTrue(scenario.state != Lifecycle.State.DESTROYED)
        }
    }
}
