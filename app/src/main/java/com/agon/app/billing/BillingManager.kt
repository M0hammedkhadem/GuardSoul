package com.agon.app.billing

import android.content.Context
import com.agon.app.data.settings.AppSettings
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * High-level entitlement gateway. Translates the low-level billing state
 * into "is this feature usable by the current user" answers that the rest
 * of the app can read.
 *
 * - Free  → base feature set, 3 social apps max, no cloud sync
 * - Pro   → unlimited social apps, cloud sync, full statistics
 * - Premium → everything + AI NSFW scanner + accountability partner
 *
 * The cached tier is persisted in [AppSettings] so the gate can answer
 * quickly at boot before the Play client reconnects.
 */
class BillingManager(
    private val context: Context,
    private val settings: AppSettings,
    private val wrapper: BillingClientWrapper
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _tier = MutableStateFlow(SubscriptionTier.FREE)
    val tier: StateFlow<SubscriptionTier> = _tier.asStateFlow()

    val products: StateFlow<Map<String, ProductInfo>> = wrapper.products
    val entitlement: StateFlow<EntitlementState> = wrapper.entitlements

    fun start() {
        scope.launch {
            _tier.value = settings.getSubscriptionTierCached()
            wrapper.connect()
        }
        scope.launch {
            wrapper.entitlements.collect { state ->
                val newTier = when (state) {
                    is EntitlementState.Paid -> state.tier
                    EntitlementState.Free, EntitlementState.Unknown -> SubscriptionTier.FREE
                }
                if (newTier != _tier.value) {
                    _tier.value = newTier
                    settings.setSubscriptionTier(newTier)
                    AppLogger.i("BillingManager: tier changed → $newTier")
                }
            }
        }
    }

    fun canAccess(feature: PremiumFeature): Boolean {
        return when (feature) {
            PremiumFeature.CORE_BLOCKING -> true
            PremiumFeature.UNLIMITED_SOCIAL_APPS -> _tier.value != SubscriptionTier.FREE
            PremiumFeature.AI_NSFW_SCANNER -> _tier.value == SubscriptionTier.PREMIUM
            PremiumFeature.CLOUD_SYNC -> _tier.value != SubscriptionTier.FREE
            PremiumFeature.ACCOUNTABILITY_PARTNER -> _tier.value == SubscriptionTier.PREMIUM
            PremiumFeature.ADVANCED_STATISTICS -> _tier.value != SubscriptionTier.FREE
            PremiumFeature.STUDY_ROOM -> _tier.value != SubscriptionTier.FREE
            PremiumFeature.CUSTOM_BLOCKLISTS -> _tier.value != SubscriptionTier.FREE
        }
    }

    fun refresh() {
        scope.launch { wrapper.refreshEntitlements() }
    }

    /** Public surface for the upgrade UI to start a purchase flow. */
    fun purchase(activity: android.app.Activity, sku: String): Boolean {
        return wrapper.launchPurchaseFlow(activity, sku)
    }

    /** Restore an active subscription using a Play-provided purchase token. */
    fun updateSubscription(activity: android.app.Activity, newSku: String, oldToken: String): Boolean {
        return wrapper.launchUpdateFlow(activity, newSku, oldToken)
    }

    fun shutdown() {
        wrapper.endConnection()
    }
}

/**
 * Single source of truth for "is this gated?". Add a new entry here when
 * you add a new premium-only feature — the upgrade screen reads this
 * list to render the comparison table.
 */
enum class PremiumFeature(val displayKey: String) {
    CORE_BLOCKING("core_blocking"),
    UNLIMITED_SOCIAL_APPS("unlimited_social"),
    AI_NSFW_SCANNER("ai_nsfw_scanner"),
    CLOUD_SYNC("cloud_sync"),
    ACCOUNTABILITY_PARTNER("accountability_partner"),
    ADVANCED_STATISTICS("advanced_statistics"),
    STUDY_ROOM("study_room"),
    CUSTOM_BLOCKLISTS("custom_blocklists");

    val requiredTier: SubscriptionTier
        get() = when (this) {
            CORE_BLOCKING -> SubscriptionTier.FREE
            UNLIMITED_SOCIAL_APPS,
            CLOUD_SYNC,
            ADVANCED_STATISTICS,
            STUDY_ROOM,
            CUSTOM_BLOCKLISTS -> SubscriptionTier.PRO
            AI_NSFW_SCANNER,
            ACCOUNTABILITY_PARTNER -> SubscriptionTier.PREMIUM
        }
}
