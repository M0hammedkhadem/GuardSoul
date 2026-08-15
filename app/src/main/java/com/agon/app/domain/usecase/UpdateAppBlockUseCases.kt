package com.agon.app.domain.usecase

import com.agon.app.data.AppBlockState

/**
 * Full-block toggle, moved verbatim from MainViewModel.updateAppFull:
 *
 * Mutual exclusivity: enabling full block auto-disables shorts-only.
 * Shield lock: full block can never be weakened while the shield is on.
 * Returns false (and leaves state untouched) when the mutation is rejected.
 */
class UpdateAppFullBlockUseCase {

    operator fun invoke(
        appBlocks: MutableMap<String, AppBlockState>,
        id: String,
        enabled: Boolean,
        shieldActive: Boolean,
        persist: () -> Unit,
    ): Boolean {
        val cur = appBlocks[id] ?: AppBlockState()
        if (enabled) {
            // Strengthening: always allowed. Auto-switch off shorts (exclusive).
            appBlocks[id] = AppBlockState(fullBlock = true, shortsBlock = false)
        } else {
            if (shieldActive) return false // weakening
            appBlocks[id] = cur.copy(fullBlock = false)
        }
        persist()
        return true
    }
}

/**
 * Shorts-only toggle, moved verbatim from MainViewModel.updateAppShorts:
 *
 * Mutual exclusivity: enabling shorts-only auto-disables full block — but
 * that downgrade (full -> shorts) is forbidden while the shield is on.
 * Returns false (and leaves state untouched) when the mutation is rejected.
 */
class UpdateAppShortsBlockUseCase {

    operator fun invoke(
        appBlocks: MutableMap<String, AppBlockState>,
        id: String,
        enabled: Boolean,
        shieldActive: Boolean,
        persist: () -> Unit,
    ): Boolean {
        val cur = appBlocks[id] ?: AppBlockState()
        if (enabled) {
            if (cur.fullBlock && shieldActive) return false // downgrade
            appBlocks[id] = AppBlockState(fullBlock = false, shortsBlock = true)
        } else {
            if (shieldActive) return false // weakening
            appBlocks[id] = cur.copy(shortsBlock = false)
        }
        persist()
        return true
    }
}
