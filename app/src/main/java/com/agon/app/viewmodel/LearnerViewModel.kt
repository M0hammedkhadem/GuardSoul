package com.agon.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.GuardianApp
import com.agon.app.blocking.SignatureLearner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Auto-Learned Signatures screen.
 *
 * Exposes:
 *  - a hot [uiState] combining the [SignatureLearner.signatures]
 *    flow with the user's `learnerEnabled` toggle;
 *  - a [forget] action that drops a single package;
 *  - a [forgetAll] action that clears every learned signature.
 *
 * All state mutation is funneled through the learner, which
 * already mutex-serializes its writes; the ViewModel just
 * forwards intents.
 */
class LearnerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: GuardianApp = application as GuardianApp
    private val settings = app.repository.getAppSettings()
    private val learner: SignatureLearner = SignatureLearner(settings)

    val enabledFlow: StateFlow<Boolean> = settings.learnerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Hydrate from DataStore then bridge the in-memory flow
            // into the UI state.
            learner.load()
            learner.signatures.collect { snapshot ->
                _uiState.value = UiState(
                    entries = snapshot.values
                        .sortedByDescending { it.lastSeen }
                        .toList(),
                )
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLearnerEnabled(enabled) }
    }

    fun forget(pkg: String) {
        viewModelScope.launch { learner.demote(pkg) }
    }

    fun forgetAll() {
        viewModelScope.launch { learner.clearAll() }
    }

    data class UiState(
        val entries: List<SignatureLearner.LearnedSignature> = emptyList(),
    )
}
