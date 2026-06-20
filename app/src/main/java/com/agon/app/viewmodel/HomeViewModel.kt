package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.settings.AppSettings
import com.agon.app.guardianApp
import com.agon.app.services.SelfHealingMonitor
import com.agon.app.utils.ServiceManager
import com.agon.app.utils.ShieldPermissionValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import timber.log.Timber

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application.guardianApp()!!).repository
    private val settings = repo.getAppSettings()

    val shieldActive: StateFlow<Boolean> = settings.shieldActiveFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val deactivationDelay: StateFlow<Int> = settings.deactivationDelayFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val trialMode: StateFlow<Boolean> = settings.trialModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val testMode: StateFlow<Boolean> = settings.testModeFlow
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalBlocks: StateFlow<Int> = repo.totalBlocksFlow()
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val daysActive: StateFlow<Int> = combine(
        settings.shieldActiveFlow,
        settings.shieldActivatedAtFlow,
        timeTickFlow(60_000L)
    ) { active, activatedAt, _ ->
        if (!active || activatedAt <= 0L) 0
        else AppSettings.calculateDaysActive(activatedAt)
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hasPin: StateFlow<Boolean> = settings.pinHashFlow.map { it.isNotBlank() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _countdownActive = MutableStateFlow(false)
    val countdownActive: StateFlow<Boolean> = _countdownActive.asStateFlow()

    private val _countdownEndAt = MutableStateFlow(0L)

    val remainingSeconds: StateFlow<Int> = flow {
        while (true) {
            val end = _countdownEndAt.value
            val remaining = if (end <= 0L) 0
                            else ((end - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
            emit(remaining)
            delay(1_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _showPinDialog = MutableStateFlow(false)
    val showPinDialog: StateFlow<Boolean> = _showPinDialog.asStateFlow()

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    private var countdownJob: Job? = null
    private val _permissionError = MutableStateFlow<String>("")
    val permissionError: StateFlow<String> = _permissionError.asStateFlow()
    
    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()
    
    private val _missingPermissions = MutableStateFlow<List<ShieldPermissionValidator.MissingPermission>>(emptyList())
    val missingPermissions: StateFlow<List<ShieldPermissionValidator.MissingPermission>> = _missingPermissions.asStateFlow()

    fun toggleShield() {
        viewModelScope.launch {
            val current = shieldActive.value
            if (!current) {
                // Check all required permissions before enabling shield
                val result = ShieldPermissionValidator.checkAllPermissions(getApplication())
                if (result.allGranted) {
                    activateShield()
                } else {
                    _missingPermissions.value = result.missingPermissions
                    _permissionError.value = "يجب منح جميع الأذونات المطلوبة للميزات المفعلة"
                    _showPermissionDialog.value = true
                }
            } else {
                startDeactivation()
            }
        }
    }
    
    private fun activateShield() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            settings.setLastActiveDate(now)
            settings.setShieldActivatedAt(now)
            settings.setShieldActive(true)
            ServiceManager.setShieldActive(getApplication(), true)
            // Start self-healing monitor
            try {
                SelfHealingMonitor.start(getApplication())
            } catch (e: Exception) {
                Timber.w(e, "Failed to start SelfHealingMonitor")
            }
        }
    }
    
    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
        _permissionError.value = ""
        _missingPermissions.value = emptyList()
    }
    
    fun openPermissionAction(actionIndex: Int) {
        val permissions = _missingPermissions.value
        if (actionIndex < permissions.size) {
            val missing = permissions[actionIndex]
            try {
                missing.action(getApplication())
            } catch (e: Exception) {
                Timber.e(e, "Failed to open permission action")
            }
        }
    }

    fun startDeactivation() {
        if (trialMode.value || deactivationDelay.value <= 0) {
            if (hasPin.value) {
                _showPinDialog.value = true
                return
            }
            completeDeactivation()
            return
        }

        countdownJob?.cancel()
        _countdownActive.value = true
        val totalSeconds = deactivationDelay.value.toLong() * 24L * 60L * 60L
        _countdownEndAt.value = System.currentTimeMillis() + totalSeconds * 1_000L
        countdownJob = viewModelScope.launch {
            val endAt = _countdownEndAt.value
            while (System.currentTimeMillis() < endAt) {
                val remainingMs = endAt - System.currentTimeMillis()
                val tickChunk = if (remainingMs > 60_000L) 60_000L else 1_000L
                delay(tickChunk)
            }
            _countdownEndAt.value = 0L
            _countdownActive.value = false
            if (hasPin.value) {
                _showPinDialog.value = true
            } else {
                completeDeactivation()
            }
        }
    }

    fun verifyPin(input: String) {
        viewModelScope.launch {
            val storedHash = settings.getPinHash()
            val digest = MessageDigest.getInstance("SHA-256")
            val hashed = digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
            if (hashed == storedHash) {
                _showPinDialog.value = false
                completeDeactivation()
            } else {
                _pinError.value = true
            }
        }
    }

    fun cancelDeactivation() {
        countdownJob?.cancel()
        _countdownActive.value = false
        _countdownEndAt.value = 0L
    }

    fun dismissPinDialog() {
        _showPinDialog.value = false
        _pinError.value = false
    }

    fun dismissPinError() {
        _pinError.value = false
    }

    private fun completeDeactivation() {
        viewModelScope.launch {
            settings.setShieldActive(false)
            settings.setShieldActivatedAt(0L)
            _countdownActive.value = false
            _countdownEndAt.value = 0L
            ServiceManager.setShieldActive(getApplication(), false)
            // Stop self-healing monitor
            try {
                SelfHealingMonitor.stop(getApplication())
            } catch (e: Exception) {
                Timber.w(e, "Failed to stop SelfHealingMonitor")
            }
        }
    }

    private fun timeTickFlow(intervalMs: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }

    fun toggleTrialMode() {
        viewModelScope.launch { settings.setTrialMode(!trialMode.value) }
    }

    fun toggleTestMode() {
        viewModelScope.launch { settings.setTestMode(!testMode.value) }
    }

    fun setDeactivationDelay(days: Int) {
        viewModelScope.launch { settings.setDeactivationDelay(days) }
    }
}
