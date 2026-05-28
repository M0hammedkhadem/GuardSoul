package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository
    private val settings = repo.getAppSettings()

    val profileName: StateFlow<String> = settings.profileNameFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val shieldActive: StateFlow<Boolean> = settings.shieldActiveFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val hasPin: StateFlow<Boolean> = settings.pinHashFlow.map { it.isNotBlank() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val trialMode: StateFlow<Boolean> = settings.trialModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun saveName(name: String) {
        viewModelScope.launch { settings.setProfileName(name) }
    }
}
