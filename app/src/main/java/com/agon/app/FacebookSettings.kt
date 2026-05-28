package com.agon.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.facebookDataStore by preferencesDataStore(name = "facebook_settings")

object FacebookSettings {
    private val FACEBOOK_MODE_KEY = stringPreferencesKey("facebook_mode")
    private val VIDEO_BLOCK_KEY = booleanPreferencesKey("video_block_enabled")
    private val VIDEO_BLOCK_COUNT_KEY = intPreferencesKey("video_block_count")

    private val _cachedVideoBlock = MutableStateFlow(false)
    private var observeJob: Job? = null

    fun startObserving(context: Context) {
        observeJob?.cancel()
        observeJob = CoroutineScope(Dispatchers.IO).launch {
            videoBlockFlow(context).collect { _cachedVideoBlock.value = it }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun isVideoBlockEnabled(): Boolean = _cachedVideoBlock.value

    fun facebookModeFlow(context: Context): Flow<String> =
        context.facebookDataStore.data.map { it[FACEBOOK_MODE_KEY] ?: "off" }

    fun videoBlockFlow(context: Context): Flow<Boolean> =
        context.facebookDataStore.data.map { it[VIDEO_BLOCK_KEY] ?: false }

    fun videoBlockCountFlow(context: Context): Flow<Int> =
        context.facebookDataStore.data.map { it[VIDEO_BLOCK_COUNT_KEY] ?: 0 }

    suspend fun getFacebookMode(context: Context): String =
        context.facebookDataStore.data.first()[FACEBOOK_MODE_KEY] ?: "off"

    suspend fun setFacebookMode(context: Context, mode: String) {
        context.facebookDataStore.edit { it[FACEBOOK_MODE_KEY] = mode }
    }

    suspend fun setVideoBlockEnabled(context: Context, enabled: Boolean) {
        context.facebookDataStore.edit { it[VIDEO_BLOCK_KEY] = enabled }
    }

    suspend fun incrementVideoBlockCount(context: Context) {
        context.facebookDataStore.edit { prefs ->
            val current = prefs[VIDEO_BLOCK_COUNT_KEY] ?: 0
            prefs[VIDEO_BLOCK_COUNT_KEY] = current + 1
        }
    }

    suspend fun resetVideoBlockCount(context: Context) {
        context.facebookDataStore.edit { it[VIDEO_BLOCK_COUNT_KEY] = 0 }
    }
}
