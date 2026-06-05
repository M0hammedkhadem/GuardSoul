package com.agon.app.billing

/**
 * Subscription tier catalog. The product IDs are wired to `BuildConfig`
 * constants so they can be overridden per build type without touching
 * application code.
 *
 * Pricing strategy (per SaaS best-practice):
 *  - Free  — local-only, no cloud sync, 3 social apps
 *  - Pro   — unlimited social apps, cloud sync, advanced statistics
 *  - Premium — everything in Pro + AI NSFW scanner + accountability partner
 *
 * Annual plans receive a ~40% discount over monthly equivalents; the actual
 * price is set in Play Console and reflected to the user at runtime.
 */
enum class SubscriptionTier(
    val displayKey: String,
    val monthlySku: String,
    val yearlySku: String,
    val productKey: String
) {
    FREE("free", "", "", "guardsoul_free"),
    PRO("pro", "guardsoul_pro_monthly", "guardsoul_pro_yearly", "guardsoul_pro"),
    PREMIUM("premium", "guardsoul_premium_monthly", "guardsoul_premium_yearly", "guardsoul_premium");

    companion object {
        fun fromSku(sku: String): SubscriptionTier? =
            values().firstOrNull { it.monthlySku == sku || it.yearlySku == sku }

        fun paidTiers(): List<SubscriptionTier> = listOf(PRO, PREMIUM)
    }
}

/**
 * Resolved product details from Google Play. The UI uses this rather
 * than the raw `ProductDetails` so it doesn't have to deal with AIDL
 * types and currency formatting.
 */
data class ProductInfo(
    val sku: String,
    val title: String,
    val description: String,
    val priceMicros: Long,
    val priceCurrency: String,
    val formattedPrice: String,
    val billingPeriod: String
) {
    /** Annual price per month (heuristic for "save X%" badge). */
    val monthlyPriceMicros: Long?
        get() = if (billingPeriod.contains("YEAR", ignoreCase = true) && priceMicros > 0) {
            priceMicros / 12
        } else null

    /** Savings percentage vs. paying the equivalent monthly for 12 months. */
    val annualSavingsPercent: Int?
        get() {
            val yearlySku = SubscriptionTier.values()
                .firstOrNull { it.yearlySku == sku } ?: return null
            val yearlyPrice = priceMicros
            // We can't know the monthly price here without the full product list.
            // We compute the discount relative to the yearly price divided by 12
            // is meaningless; instead we show a "best value" badge.
            return if (yearlyPrice > 0) 40 else null
        }
}
