package com.agon.app.facebook

import timber.log.Timber
import android.webkit.JavascriptInterface

class FacebookBridge(
    private val onBlocked: (Int) -> Unit,
    private val onPerfWarning: (Double) -> Unit
) {
    @JavascriptInterface
    fun onReelBlocked(count: Int) {
        Timber.tag(TAG).d("Reel blocked. Total count: $count")
        onBlocked(count)
    }

    @JavascriptInterface
    fun onPerformanceWarning(batchTimeMs: Double) {
        if (batchTimeMs > 16.0) {
            Timber.tag(TAG).w("Performance warning: batch took ${batchTimeMs}ms")
            onPerfWarning(batchTimeMs)
        }
    }

    companion object {
        private const val TAG = "FacebookBridge"
    }
}
