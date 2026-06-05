package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.AppBlockerService
import com.agon.app.BlocklistItem

import com.agon.app.guardianApp
import com.agon.app.GuardianApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application.guardianApp()!!).repository

    private val _selectedListType = MutableStateFlow("blacklist")
    val selectedListType: StateFlow<String> = _selectedListType.asStateFlow()

    private val _selectedCategory = MutableStateFlow("keywords")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val currentListType: String get() = _selectedListType.value
    val currentCategory: String get() = _selectedCategory.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<BlocklistItem>> = combine(
        _selectedListType, _selectedCategory
    ) { type, cat -> type to cat }
        .flatMapLatest { (type, cat) -> repo.getBlocklistFlow(type, cat) }
        .map { entities -> entities.map { BlocklistItem.fromEntity(it) }.distinctBy { it.id } }
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

    fun addItem(value: String, label: String? = null) {
        viewModelScope.launch {
            val item = BlocklistItem.create(
                listType = _selectedListType.value,
                category = _selectedCategory.value,
                value = value,
                label = label,
            )
            repo.addBlocklistItem(item.toEntity())
            notifyAppBlocker(item.listType, item.category)
        }
    }

    fun addItem(item: BlocklistItem) {
        viewModelScope.launch {
            repo.addBlocklistItem(item.toEntity())
            notifyAppBlocker(item.listType, item.category)
        }
    }

    fun removeItem(id: Long) {
        viewModelScope.launch {
            repo.removeBlocklistItemById(id)
            notifyAppBlocker(_selectedListType.value, _selectedCategory.value)
        }
    }

    fun toggleItem(item: BlocklistItem) {
        viewModelScope.launch {
            val entity = repo.getBlocklistItemById(item.id) ?: return@launch
            repo.addBlocklistItem(entity.copy(enabled = !entity.enabled))
            notifyAppBlocker(item.listType, item.category)
        }
    }

    fun updateItem(item: BlocklistItem) {
        viewModelScope.launch {
            repo.addBlocklistItem(item.toEntity().copy(id = item.id))
            notifyAppBlocker(item.listType, item.category)
        }
    }

    private fun notifyAppBlocker(listType: String, @Suppress("UNUSED_PARAMETER") category: String) {
        val ctx = getApplication<GuardianApp>()
        AppBlockerService.reloadBlocklist(ctx)
    }
}
