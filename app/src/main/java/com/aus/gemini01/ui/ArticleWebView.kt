package com.aus.gemini01.ui

import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleWebView(
    url: String,
    title: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Publisher page",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    IconButton(onClick = { ArticleNavigator.openArticle(context, url) }) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open in browser"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            // HTTPS only; block http, file://, content://, javascript:,
                            // intent:// and other non-web schemes.
                            return request?.url?.scheme != "https"
                        }

                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            // Don't blindly trust invalid certs - that's how MITM attacks
                            // get a free pass. The default handler shows a confirm dialog
                            // and continues; we cancel instead.
                            handler?.cancel()
                        }
                    }
                    settings.apply {
                        // Publisher pages do not need JavaScript for this reader
                        // surface. Keeping it off removes an unnecessary execution
                        // surface for untrusted third-party pages.
                        javaScriptEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        domStorageEnabled = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        safeBrowsingEnabled = true
                    }
                    safeArticleUrl(url)?.let { loadUrl(it) }
                }
            },
            update = { webView ->
                val targetUrl = safeArticleUrl(url)
                if (targetUrl != null && webView.url != targetUrl) {
                    webView.loadUrl(targetUrl)
                }
            },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            }
        )
    }
}
