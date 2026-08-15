package com.agon.app.domain.usecase

/**
 * Add rule, moved verbatim from MainViewModel.addToList:
 *
 * Adding to a blacklist strengthens (always allowed); adding to a whitelist
 * weakens (rejected while the shield is active). Blank input is a silent
 * success WITHOUT persisting — exactly like the legacy early-return.
 */
class AddToListUseCase {

    operator fun invoke(
        list: MutableList<String>,
        black: Boolean,
        value: String,
        shieldActive: Boolean,
        persist: () -> Unit,
    ): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return true
        if (!black && shieldActive) return false
        if (!list.contains(v)) list.add(0, v)
        persist()
        return true
    }
}

/**
 * Remove rule, moved verbatim from MainViewModel.removeFromList:
 *
 * Removing from a blacklist weakens (rejected while the shield is active);
 * removing from a whitelist strengthens (always allowed).
 */
class RemoveFromListUseCase {

    operator fun invoke(
        list: MutableList<String>,
        black: Boolean,
        value: String,
        shieldActive: Boolean,
        persist: () -> Unit,
    ): Boolean {
        if (black && shieldActive) return false
        list.remove(value)
        persist()
        return true
    }
}
