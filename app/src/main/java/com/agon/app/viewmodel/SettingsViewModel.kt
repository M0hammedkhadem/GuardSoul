package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.guardianApp()!!.repository
    val settings = repo.getAppSettings()

    val strictMode = settings.strictModeFlow
    val deactivationDelay = settings.deactivationDelayFlow

    fun resetAllSettings() = viewModelScope.launch {
        repo.resetAllSettings()
    }

    fun setStrictMode(enabled: Boolean) = viewModelScope.launch {
        settings.setStrictMode(enabled)
    }

    fun setDeactivationDelay(days: Int) = viewModelScope.launch {
        settings.setDeactivationDelay(days)
    }
}
