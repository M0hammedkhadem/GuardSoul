package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.data.local.entity.BlocklistItemEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ListsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository

    private val _selectedListType = MutableStateFlow("blacklist")
    val selectedListType: StateFlow<String> = _selectedListType.asStateFlow()

    private val _selectedCategory = MutableStateFlow("keywords")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<BlocklistItemEntity>> = combine(_selectedListType, _selectedCategory) { type, cat ->
        type to cat
    }.flatMapLatest { (type, cat) ->
        repo.getBlocklistFlow(type, cat)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val keywordsCount: StateFlow<Int> = combine(_selectedListType) { type ->
        type[0]
    }.flatMapLatest { type ->
        repo.getBlocklistFlow(type, "keywords").map { it.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val websitesCount: StateFlow<Int> = combine(_selectedListType) { type ->
        type[0]
    }.flatMapLatest { type ->
        repo.getBlocklistFlow(type, "websites").map { it.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val appsCount: StateFlow<Int> = combine(_selectedListType) { type ->
        type[0]
    }.flatMapLatest { type ->
        repo.getBlocklistFlow(type, "apps").map { it.size }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setListType(type: String) { _selectedListType.value = type }
    fun setCategory(cat: String) { _selectedCategory.value = cat }

    fun addItem(value: String) {
        viewModelScope.launch {
            repo.addBlocklistItem(_selectedListType.value, _selectedCategory.value, value)
        }
    }

    fun removeItem(item: BlocklistItemEntity) {
        viewModelScope.launch {
            repo.removeBlocklistItem(item.listType, item.category, item.value)
        }
    }

    fun addApp(value: String) {
        viewModelScope.launch {
            repo.addBlocklistItem(_selectedListType.value, "apps", value)
        }
    }
}
