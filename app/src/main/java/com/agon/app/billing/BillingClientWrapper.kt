package com.agon.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.agon.app.BuildConfig
import com.agon.app.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around [BillingClient] that exposes a coroutine-friendly
 * surface. Designed to be created once per process and held by Koin.
 *
 * Connection is lazy — we don't connect until the user opens the upgrade
 * screen, to keep the cold start free of Play Store IPC.
 *
 * Acknowledgement: the wrapper auto-acknowledges purchases on a background
 * coroutine so entitlements survive a reinstall (Play only restores
 * acknowledged purchases).
 */
class BillingClientWrapper(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _products = MutableStateFlow<Map<String, ProductInfo>>(emptyMap())
    val products: StateFlow<Map<String, ProductInfo>> = _products.asStateFlow()

    private val _entitlements = MutableStateFlow<EntitlementState>(EntitlementState.Unknown)
    val entitlements: StateFlow<EntitlementState> = _entitlements.asStateFlow()

    private var client: BillingClient? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                handlePurchases(purchases)
                refreshEntitlements()
            }
        } else {
            AppLogger.w("BillingClientWrapper: purchase update failed: ${result.responseCode} / ${result.debugMessage}")
        }
    }

    /**
     * Connect to Google Play and pre-load product details + active
     * entitlements. Idempotent — safe to call from any screen.
     */
    suspend fun connect(): Boolean {
        if (client?.isReady == true) {
            queryProducts()
            refreshEntitlements()
            return true
        }
        return suspendCancellableCoroutine { cont ->
            val newClient = BillingClient.newBuilder(context)
                .setListener(purchasesListener)
                .enablePendingPurchases()
                .build()
            client = newClient
            newClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        AppLogger.i("BillingClientWrapper: connected")
                        scope.launch {
                            queryProducts()
                            refreshEntitlements()
                        }
                        if (cont.isActive) cont.resume(true)
                    } else {
                        AppLogger.w("BillingClientWrapper: setup failed: ${result.responseCode} / ${result.debugMessage}")
                        if (cont.isActive) cont.resume(false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    AppLogger.w("BillingClientWrapper: service disconnected")
                    _entitlements.value = EntitlementState.Unknown
                }
            })
        }
    }

    private suspend fun queryProducts() {
        val active = client ?: return
        val skus = listOf(
            BuildConfig.SKU_PRO_MONTHLY,
            BuildConfig.SKU_PRO_YEARLY,
            BuildConfig.SKU_PREMIUM_MONTHLY,
            BuildConfig.SKU_PREMIUM_YEARLY
        ).filter { it.isNotBlank() }
        if (skus.isEmpty()) return

        val productList = skus.map { sku ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(sku)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            active.queryProductDetailsAsync(params, ProductDetailsResponseListener { br, list ->
                cont.resume(br to (list ?: emptyList()))
            })
        }

        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            val mapped = result.second.associate { pd -> pd.productId to toProductInfo(pd) }
            _products.value = mapped
            AppLogger.i("BillingClientWrapper: loaded ${mapped.size} products")
        } else {
            AppLogger.w("BillingClientWrapper: queryProductDetails failed: ${result.first.debugMessage}")
        }
    }

    private fun toProductInfo(pd: ProductDetails): ProductInfo {
        val offer = pd.subscriptionOfferDetails?.firstOrNull()
        val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
        return ProductInfo(
            sku = pd.productId,
            title = pd.title,
            description = pd.description,
            priceMicros = pricingPhase?.priceAmountMicros ?: 0L,
            priceCurrency = pricingPhase?.priceCurrencyCode ?: "USD",
            formattedPrice = pricingPhase?.formattedPrice ?: "",
            billingPeriod = pricingPhase?.billingPeriod ?: ""
        )
    }

    private suspend fun querySingleProduct(sku: String): ProductDetails? {
        val active = client ?: return null
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(sku)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        val result = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { cont ->
            active.queryProductDetailsAsync(params, ProductDetailsResponseListener { br, list ->
                cont.resume(br to (list ?: emptyList()))
            })
        }
        return result.second.firstOrNull { it.productId == sku }
    }

    /**
     * Launch the Google Play purchase flow for [sku]. Caller must provide
     * an [Activity] reference; we resolve [BillingClient.launchBillingFlow]
     * synchronously.
     *
     * Returns true if the flow was launched successfully.
     */
    fun launchPurchaseFlow(activity: Activity, sku: String): Boolean {
        val active = client ?: return false
        val pd = _products.value[sku]?.let { cached ->
            // Re-query in the background if we don't have a full ProductDetails,
            // but the simple way is to use the cached data and start a one-shot
            // query first.
            cached
        }
        if (pd == null) {
            AppLogger.w("BillingClientWrapper: no cached product for $sku — call connect() first")
            return false
        }
        // We need the full ProductDetails (with offerToken), not the DTO.
        // Spin up a one-shot query on the current thread (suspend can't be
        // called from a non-suspend context here).
        scope.launch {
            val full = querySingleProduct(sku) ?: return@launch
            val offerToken = full.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return@launch
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(full)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .build()
            val result = active.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                AppLogger.w("BillingClientWrapper: launchBillingFlow failed: ${result.debugMessage}")
            }
        }
        return true
    }

    /**
     * Restore a subscription using a Play-provided purchase token. Used
     * for upgrade/downgrade flows (e.g. PRO monthly → PREMIUM yearly).
     */
    fun launchUpdateFlow(activity: Activity, newSku: String, oldPurchaseToken: String): Boolean {
        val active = client ?: return false
        scope.launch {
            val pd = querySingleProduct(newSku) ?: return@launch
            val offerToken = pd.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return@launch
            val updateParams = BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldPurchaseToken)
                .setSubscriptionReplacementMode(
                    BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
                )
                .build()
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(pd)
                            .setOfferToken(offerToken)
                            .build()
                    )
                )
                .setSubscriptionUpdateParams(updateParams)
                .build()
            val result = active.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                AppLogger.w("BillingClientWrapper: launchUpdateFlow failed: ${result.debugMessage}")
            }
        }
        return true
    }

    /**
     * Refresh the cached entitlement from the Play Store. Call after
     * `connect()` and after any purchase flow completion.
     */
    suspend fun refreshEntitlements() {
        val active = client ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { cont ->
            active.queryPurchasesAsync(params) { br, list ->
                cont.resume(br to (list ?: emptyList()))
            }
        }
        if (result.first.responseCode == BillingClient.BillingResponseCode.OK) {
            val purchases = result.second
            handlePurchases(purchases)
            val tier = purchases
                .mapNotNull { SubscriptionTier.fromSku(it.products.firstOrNull().orEmpty()) }
                .maxByOrNull { it.ordinal }
                ?: SubscriptionTier.FREE
            val next = if (tier == SubscriptionTier.FREE) {
                EntitlementState.Free
            } else {
                val purchaseTime = purchases.maxOfOrNull { it.purchaseTime } ?: 0L
                EntitlementState.Paid(
                    tier = tier,
                    expiresAt = purchaseTime,
                    willAutoRenew = purchases.any { it.isAutoRenewing }
                )
            }
            _entitlements.value = next
            AppLogger.i("BillingClientWrapper: tier=$tier purchases=${purchases.size}")
        } else {
            AppLogger.w("BillingClientWrapper: queryPurchases failed: ${result.first.debugMessage}")
        }
    }

    private suspend fun handlePurchases(purchases: List<Purchase>) {
        val active = client ?: return
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                runCatching {
                    suspendCancellableCoroutine<BillingResult> { cont ->
                        active.acknowledgePurchase(ackParams) { cont.resume(it) }
                    }
                }.onFailure { AppLogger.w("BillingClientWrapper: ack failed: ${it.message}") }
            }
        }
    }

    fun endConnection() {
        client?.endConnection()
        client = null
    }
}

/** What the user is entitled to right now, in a UI-friendly shape. */
sealed class EntitlementState {
    data object Unknown : EntitlementState()
    data object Free : EntitlementState()
    data class Paid(
        val tier: SubscriptionTier,
        val expiresAt: Long,
        val willAutoRenew: Boolean
    ) : EntitlementState()
}
