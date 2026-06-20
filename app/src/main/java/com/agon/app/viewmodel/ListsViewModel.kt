package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application.guardianApp()!!).repository
    private val settings = repo.getAppSettings()

    private val _isBlacklist = MutableStateFlow(true)
    val isBlacklist: StateFlow<Boolean> = _isBlacklist.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ListCategory.KEYWORDS)
    val selectedCategory: StateFlow<ListCategory> = _selectedCategory.asStateFlow()

    val blockedApps: StateFlow<Set<String>> = settings.blockedAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedWebsites: StateFlow<Set<String>> = settings.blockedWebsitesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Keywords now from Room DB via repository
    val blockedKeywords: StateFlow<Set<String>> = repo.getBlacklistKeywords()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setBlacklist(value: Boolean) {
        _isBlacklist.value = value
    }

    fun setCategory(category: ListCategory) {
        _selectedCategory.value = category
    }

    fun addItem(item: String) {
        if (item.isBlank()) return
        viewModelScope.launch {
            when (_selectedCategory.value) {
                ListCategory.APPS -> {
                    val current = settings.blockedAppsFlow.first()
                    settings.setBlockedApps(current + item)
                }
                ListCategory.WEBSITES -> {
                    val current = settings.blockedWebsitesFlow.first()
                    settings.setBlockedWebsites(current + item)
                }
                ListCategory.KEYWORDS -> {
                    repo.addKeyword(item, isWhitelist = false)
                }
            }
        }
    }

    fun removeItem(item: String) {
        viewModelScope.launch {
            when (_selectedCategory.value) {
                ListCategory.APPS -> {
                    val current = settings.blockedAppsFlow.first()
                    settings.setBlockedApps(current - item)
                }
                ListCategory.WEBSITES -> {
                    val current = settings.blockedWebsitesFlow.first()
                    settings.setBlockedWebsites(current - item)
                }
                ListCategory.KEYWORDS -> {
                    repo.removeKeyword(item, isWhitelist = false)
                }
            }
        }
    }
}

enum class ListCategory {
    APPS, WEBSITES, KEYWORDS
}
