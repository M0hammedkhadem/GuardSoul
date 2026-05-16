package com.agon.app.facebook

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.BufferedReader
import java.io.InputStreamReader

class BlockerWebViewClient(
    private val blockerScript: String,
    private val onPageStarted: (String) -> Unit = {},
    private val onPageFinished: (String) -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url != null) onPageStarted(url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null) {
            injectBlocker(view)
            onPageFinished(url)
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val uri = Uri.parse(url)
        val host = uri.host ?: return false

        if (host.contains("facebook.com") || host.contains("fb.com") || host.contains("messenger.com")) {
            return false
        }
        if (host.contains("m.facebook.com")) return false

        view?.loadUrl(url)
        return true
    }

    private fun injectBlocker(webView: WebView) {
        webView.evaluateJavascript(blockerScript, null)
    }

    companion object {
        private const val TAG = "BlockerWebViewClient"

        fun loadScriptFromAsset(webView: WebView): String {
            return try {
                val inputStream = webView.context.assets.open("blocker.js")
                val reader = BufferedReader(InputStreamReader(inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                reader.close()
                sb.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load blocker.js", e)
                ""
            }
        }
    }
}
