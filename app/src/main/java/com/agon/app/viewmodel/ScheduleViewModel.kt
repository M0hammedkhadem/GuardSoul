package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.guardianApp
import com.agon.app.GuardianApp
import com.agon.app.data.local.entity.ScheduleRuleEntity
import com.agon.app.utils.ScheduleEnforcer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application.guardianApp()!!).repository

    val rules: StateFlow<List<ScheduleRuleEntity>> = repo.getAllScheduleRules()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            ScheduleEnforcer.rescheduleAll(getApplication(), repo)
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repo.toggleScheduleRule(id, enabled)
            ScheduleEnforcer.rescheduleAll(getApplication(), repo)
        }
    }

    fun deleteRule(rule: ScheduleRuleEntity) {
        viewModelScope.launch {
            repo.deleteScheduleRule(rule)
            ScheduleEnforcer.rescheduleAll(getApplication(), repo)
        }
    }
}
