package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.BlocklistItem
import com.agon.app.DnsVpnService
import com.agon.app.GuardianApp
import com.agon.app.toEntity
import com.agon.app.toUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository

    private val _selectedListType = MutableStateFlow("blacklist")
    val selectedListType: StateFlow<String> = _selectedListType.asStateFlow()

    private val _selectedCategory = MutableStateFlow("keywords")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val currentListType: String get() = _selectedListType.value
    val currentCategory: String get() = _selectedCategory.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<BlocklistItem>> = combine(
        _selectedListType, _selectedCategory, _searchQuery
    ) { type, cat, query -> Triple(type, cat, query) }
        .flatMapLatest { (type, cat, query) ->
            val flow = if (query.isBlank()) {
                repo.getBlocklistFlow(type, cat)
            } else {
                repo.searchBlocklist(type, cat, query)
            }
            flow
        }
        .map { entities -> entities.map { it.toUiModel() }.distinctBy { it.id } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun countFor(category: String): StateFlow<Int> =
        _selectedListType.flatMapLatest { type ->
            repo.getBlocklistFlow(type, category).map { it.size }
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val keywordsCount = countFor("keywords")
    val websitesCount = countFor("websites")
    val appsCount = countFor("apps")

    fun setListType(type: String) { _selectedListType.value = type }
    fun setCategory(cat: String) { _selectedCategory.value = cat }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun searchItems(query: String) { _searchQuery.value = query }

    fun addItem(value: String, label: String? = null, regexEnabled: Boolean = false, sensitivityLevel: String = "medium", urlCategory: String? = null) {
        viewModelScope.launch {
            repo.addBlocklistItem(
                listType = _selectedListType.value,
                category = _selectedCategory.value,
                value = value
            )
            notifyServices(_selectedListType.value, _selectedCategory.value)
        }
    }

    fun addItem(item: BlocklistItem) {
        viewModelScope.launch {
            repo.addBlocklistItem(item.toEntity())
            notifyServices(item.listType, item.category)
        }
    }

    fun removeItem(id: Long) {
        viewModelScope.launch {
            repo.removeBlocklistItemById(id)
            notifyServices(_selectedListType.value, _selectedCategory.value)
        }
    }

    fun toggleItem(item: BlocklistItem) {
        viewModelScope.launch {
            val entity = repo.getBlocklistItemById(item.id) ?: return@launch
            repo.addBlocklistItem(entity.copy(enabled = !entity.enabled))
            notifyServices(item.listType, item.category)
        }
    }

    fun updateItem(item: BlocklistItem) {
        viewModelScope.launch {
            repo.addBlocklistItem(item.toEntity().copy(id = item.id))
            notifyServices(item.listType, item.category)
        }
    }

    private fun notifyServices(listType: String, category: String) {
        val ctx = getApplication<GuardianApp>()
        AppBlockerService.reloadBlocklist(ctx)
        if (category == "websites") {
            DnsVpnService.reloadWebsites(ctx)
        }
    }
}
