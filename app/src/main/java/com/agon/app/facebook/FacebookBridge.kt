package com.agon.app.facebook

import android.util.Log
import android.webkit.JavascriptInterface

class FacebookBridge(
    private val onBlocked: (Int) -> Unit,
    private val onPerfWarning: (Double) -> Unit
) {
    @JavascriptInterface
    fun onReelBlocked(count: Int) {
        Log.d(TAG, "Reel blocked. Total count: $count")
        onBlocked(count)
    }

    @JavascriptInterface
    fun onPerformanceWarning(batchTimeMs: Double) {
        if (batchTimeMs > 16.0) {
            Log.w(TAG, "Performance warning: batch took ${batchTimeMs}ms")
            onPerfWarning(batchTimeMs)
        }
    }

    companion object {
        private const val TAG = "FacebookBridge"
    }
}
