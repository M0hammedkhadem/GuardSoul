package com.agon.app.facebook

import android.app.Application
import timber.log.Timber
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class FacebookViewModel(application: Application) : AndroidViewModel(application) {
    private val prefsRepository = FacebookPrefsRepository(application)

    val settings: StateFlow<FacebookSettings> = prefsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FacebookSettings()
    )

    private val _fabState = MutableStateFlow(FabState())
    val fabState: StateFlow<FabState> = _fabState.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private var webView: WebView? = null
    private var scriptInjected = false

    fun setWebView(wv: WebView) {
        webView = wv
    }

    fun onPageStarted(url: String) {
        _currentUrl.value = url
        scriptInjected = false
        updateFabBasedOnUrl(url)
    }

    fun onPageFinished(url: String) {
        scriptInjected = true
        updateFabBasedOnUrl(url)
    }

    fun handleReelBlocked(count: Int) {
        val today = LocalDate.now().toString()
        val saved = settings.value.lastResetDate
        val base = if (saved != today) 0 else settings.value.dailyBlockedCount
        val newCount = base + 1
        viewModelScope.launch {
            prefsRepository.updateDailyBlockedCount(newCount)
            prefsRepository.updateTimeSavedMinutes((newCount * 0.5).toInt())
        }
        _fabState.update { it.copy(blockedCount = newCount, isSafeMode = false) }
    }

    fun handlePerfWarning(batchTimeMs: Double) {
        Timber.tag(TAG).w("Blocker performance warning: ${batchTimeMs}ms")
    }

    fun toggleBlocker() {
        viewModelScope.launch {
            prefsRepository.updateBlockerEnabled(!settings.value.blockerEnabled)
        }
    }

    fun setConfidenceThreshold(threshold: Int) {
        viewModelScope.launch {
            prefsRepository.updateConfidenceThreshold(threshold)
        }
    }

    fun setScheduleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.updateScheduleEnabled(enabled)
        }
    }

    fun setScheduleStartHour(hour: Int) {
        viewModelScope.launch {
            prefsRepository.updateScheduleStartHour(hour)
        }
    }

    fun setScheduleEndHour(hour: Int) {
        viewModelScope.launch {
            prefsRepository.updateScheduleEndHour(hour)
        }
    }

    fun setFriendProtection(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.updateFriendProtection(enabled)
        }
    }

    fun shouldBlockNow(): Boolean {
        val s = settings.value
        if (!s.blockerEnabled) return false
        if (s.scheduleEnabled) {
            val now = LocalTime.now()
            val start = LocalTime.of(s.scheduleStartHour, 0)
            val end = LocalTime.of(s.scheduleEndHour, 0)
            if (now.isBefore(start) || now.isAfter(end)) return false
        }
        return true
    }

    fun navigateBack() {
        webView?.goBack()
    }

    fun canGoBack(): Boolean = webView?.canGoBack() ?: false

    fun refresh() {
        webView?.reload()
    }

    fun pauseWebView() {
        webView?.onPause()
        webView?.pauseTimers()
    }

    fun resumeWebView() {
        webView?.onResume()
        webView?.resumeTimers()
    }

    fun destroyWebView() {
        webView?.destroy()
    }

    fun getDebugLog(): MutableList<String> {
        val log = mutableListOf<String>()
        webView?.evaluateJavascript(
            "JSON.stringify(__fbBlockerDebug ? __fbBlockerDebug.getLog() : [])",
            null
        )
        return log
    }

    private fun updateFabBasedOnUrl(url: String) {
        val isCommentPage = url.contains("comment_id=") || url.contains("/comments/")
        _fabState.update { it.copy(isCommentPage = isCommentPage) }
    }

    companion object {
        private const val TAG = "FacebookViewModel"
    }
}

data class FabState(
    val isVisible: Boolean = true,
    val isEnabled: Boolean = true,
    val isSafeMode: Boolean = false,
    val isCommentPage: Boolean = false,
    val blockedCount: Int = 0,
    val timeSavedMinutes: Int = 0
)
