package com.aus.gemini01.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

object ArticleNavigator {

    /**
     * Opens a publisher article URL in a secure, sandboxed Android Custom Tab.
     *
     * Enforces:
     * 1. Safe HTTPS article URL validation via [safeArticleUrl].
     * 2. Sandboxed browser process isolation (no shared cookies/storage with the host app).
     * 3. Visible origin and SSL certificate padlock in the browser address bar.
     * 4. Graceful fallback to [Intent.ACTION_VIEW] if Custom Tabs is unavailable.
     *
     * @return true if the article was successfully launched; false if the URL was rejected or unopenable.
     */
    fun openArticle(context: Context, rawUrl: String?): Boolean {
        val safeUrl = safeArticleUrl(rawUrl) ?: return false
        val uri = safeUrl.toUri()

        return try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()

            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabsIntent.launchUrl(context, uri)
            true
        } catch (e: Exception) {
            // Fallback to standard external browser intent
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                true
            } catch (e2: ActivityNotFoundException) {
                false
            }
        }
    }
}
