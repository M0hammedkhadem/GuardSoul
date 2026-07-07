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

    // Blacklist flows
    val blockedApps: StateFlow<Set<String>> = settings.blockedAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedWebsites: StateFlow<Set<String>> = settings.blockedWebsitesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val blockedKeywords: StateFlow<Set<String>> = repo.getBlacklistKeywords()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Whitelist flows
    val whitelistApps: StateFlow<Set<String>> = settings.whitelistAppsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val whitelistWebsites: StateFlow<Set<String>> = settings.whitelistWebsitesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val whitelistKeywords: StateFlow<Set<String>> = repo.getWhitelistKeywords()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Current list based on blacklist/whitelist mode and category.
    // Built with a typed nested combine so Kotlin can resolve each element type
    // (the >5-arg array overload loses type inference for mixed StateFlow types).
    private val selectedList: Flow<Set<String>> = combine(
        isBlacklist, selectedCategory,
        combine(blockedApps, whitelistApps) { b, w -> b to w },
        combine(blockedWebsites, whitelistWebsites) { b, w -> b to w },
        combine(blockedKeywords, whitelistKeywords) { b, w -> b to w }
    ) { isBlack, category, apps, websites, keywords ->
        val (bApps, wApps) = apps
        val (bWebsites, wWebsites) = websites
        val (bKeywords, wKeywords) = keywords
        when (category) {
            ListCategory.APPS -> if (isBlack) bApps else wApps
            ListCategory.WEBSITES -> if (isBlack) bWebsites else wWebsites
            ListCategory.KEYWORDS -> if (isBlack) bKeywords else wKeywords
        }
    }

    val currentList: StateFlow<List<String>> = selectedList
        .map { it.toList().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setBlacklist(value: Boolean) {
        _isBlacklist.value = value
    }

    fun setCategory(category: ListCategory) {
        _selectedCategory.value = category
    }

    fun addItem(item: String) {
        if (item.isBlank()) return
        viewModelScope.launch {
            val isBlacklistMode = _isBlacklist.value
            val category = _selectedCategory.value
            when (category) {
                ListCategory.APPS -> {
                    if (isBlacklistMode) {
                        val current = settings.blockedAppsFlow.first()
                        settings.setBlockedApps(current + item)
                    } else {
                        val current = settings.whitelistAppsFlow.first()
                        settings.setWhitelistApps(current + item)
                    }
                }
                ListCategory.WEBSITES -> {
                    if (isBlacklistMode) {
                        val current = settings.blockedWebsitesFlow.first()
                        settings.setBlockedWebsites(current + item)
                    } else {
                        val current = settings.whitelistWebsitesFlow.first()
                        settings.setWhitelistWebsites(current + item)
                    }
                }
                ListCategory.KEYWORDS -> {
                    repo.addKeyword(item, isWhitelist = !isBlacklistMode)
                }
            }
        }
    }

    fun removeItem(item: String) {
        viewModelScope.launch {
            val isBlacklistMode = _isBlacklist.value
            val category = _selectedCategory.value
            when (category) {
                ListCategory.APPS -> {
                    if (isBlacklistMode) {
                        val current = settings.blockedAppsFlow.first()
                        settings.setBlockedApps(current - item)
                    } else {
                        val current = settings.whitelistAppsFlow.first()
                        settings.setWhitelistApps(current - item)
                    }
                }
                ListCategory.WEBSITES -> {
                    if (isBlacklistMode) {
                        val current = settings.blockedWebsitesFlow.first()
                        settings.setBlockedWebsites(current - item)
                    } else {
                        val current = settings.whitelistWebsitesFlow.first()
                        settings.setWhitelistWebsites(current - item)
                    }
                }
                ListCategory.KEYWORDS -> {
                    repo.removeKeyword(item, isWhitelist = !isBlacklistMode)
                }
            }
        }
    }
}

enum class ListCategory {
    APPS, WEBSITES, KEYWORDS
}
