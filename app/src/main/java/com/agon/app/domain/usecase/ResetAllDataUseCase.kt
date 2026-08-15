package com.agon.app.domain.usecase

/**
 * Reset rule, moved verbatim from MainViewModel.resetAllData:
 * a full reset is a weakening move, so it is rejected while the shield is
 * active. On success the provided [clearStorage] action wipes persistence.
 */
class ResetAllDataUseCase {

    operator fun invoke(shieldActive: Boolean, clearStorage: () -> Unit): Boolean {
        if (shieldActive) return false
        clearStorage()
        return true
    }
}
