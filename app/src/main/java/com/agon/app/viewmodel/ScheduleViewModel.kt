package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.data.local.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as GuardianApp).repository

    val rules: StateFlow<List<ScheduleRuleEntity>> = repo.getAllScheduleRules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addRule(
        daysOfWeek: String,
        startHour: Int, startMinute: Int,
        endHour: Int, endMinute: Int
    ) {
        viewModelScope.launch {
            repo.addScheduleRule(
                ScheduleRuleEntity(
                    daysOfWeek = daysOfWeek,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    enabled = true
                )
            )
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repo.toggleScheduleRule(id, enabled) }
    }

    fun deleteRule(rule: ScheduleRuleEntity) {
        viewModelScope.launch { repo.deleteScheduleRule(rule) }
    }
}
