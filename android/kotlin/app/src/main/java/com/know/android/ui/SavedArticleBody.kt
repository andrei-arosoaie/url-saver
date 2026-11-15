package com.know.android.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/** Only the saved article body uses WebView. Navigation, lists and controls are native Compose. */
@Composable
internal fun SavedArticleBody(html: String, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val panel = MaterialTheme.colorScheme.surface
    fun Color.css() = "#%06x".format(toArgb() and 0xffffff)
    val document = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-src 'none'; object-src 'none'">
        <style>body{margin:18px 0;color:${ink.css()};background:${panel.css()};font:16px/1.7 Georgia,serif;overflow-wrap:anywhere}h2{font-size:24px}img,iframe,video,object,embed{display:none}</style>
        </head><body>$html</body></html>
    """.trimIndent()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(panel.toArgb())
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    loadsImagesAutomatically = false
                    setSupportMultipleWindows(false)
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        if (request.isForMainFrame && request.hasGesture() && uri.scheme in listOf("http", "https")) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())).apply { addCategory(Intent.CATEGORY_BROWSABLE) }
                            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                        }
                        return true
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != document) {
                view.tag = document
                view.setBackgroundColor(panel.toArgb())
                view.loadDataWithBaseURL("https://reader.know.invalid/", document, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
