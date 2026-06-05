package com.agon.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.analytics.AnalyticsManager
import com.agon.app.billing.BillingManager
import com.agon.app.billing.PremiumFeature
import com.agon.app.billing.ProductInfo
import com.agon.app.billing.SubscriptionTier
import com.agon.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeScreen(
    billingManager: BillingManager,
    analytics: AnalyticsManager,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val products by billingManager.products.collectAsState()
    val tier by billingManager.tier.collectAsState()
    val scope = rememberCoroutineScope()

    var billingPeriod by remember { mutableStateOf(BillingPeriod.YEARLY) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        billingManager.refresh()
        analytics.logPaywallViewed(source = "main_app")
    }

    val proMonthly = products[SubscriptionTier.PRO.monthlySku]
    val proYearly = products[SubscriptionTier.PRO.yearlySku]
    val premiumMonthly = products[SubscriptionTier.PREMIUM.monthlySku]
    val premiumYearly = products[SubscriptionTier.PREMIUM.yearlySku]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.upgrade_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text
                )
            )
        },
        containerColor = background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                stringResource(R.string.upgrade_hero_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.upgrade_hero_subtitle),
                fontSize = 14.sp,
                color = textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            BillingPeriodSwitch(
                selected = billingPeriod,
                onSelect = { billingPeriod = it }
            )
            Spacer(Modifier.height(20.dp))

            PlanCard(
                tier = SubscriptionTier.PRO,
                headline = stringResource(R.string.upgrade_pro_headline),
                price = if (billingPeriod == BillingPeriod.YEARLY) proYearly else proMonthly,
                yearlyPrice = proYearly,
                isCurrent = tier == SubscriptionTier.PRO,
                onSelect = {
                    purchase(context as Activity, billingManager, analytics,
                        if (billingPeriod == BillingPeriod.YEARLY) SubscriptionTier.PRO.yearlySku
                        else SubscriptionTier.PRO.monthlySku)
                },
                features = listOf(
                    R.string.upgrade_feature_unlimited_social,
                    R.string.upgrade_feature_cloud_sync,
                    R.string.upgrade_feature_advanced_stats,
                    R.string.upgrade_feature_study_room,
                    R.string.upgrade_feature_custom_lists
                )
            )
            Spacer(Modifier.height(12.dp))
            PlanCard(
                tier = SubscriptionTier.PREMIUM,
                headline = stringResource(R.string.upgrade_premium_headline),
                price = if (billingPeriod == BillingPeriod.YEARLY) premiumYearly else premiumMonthly,
                yearlyPrice = premiumYearly,
                isCurrent = tier == SubscriptionTier.PREMIUM,
                isHighlighted = true,
                onSelect = {
                    purchase(context as Activity, billingManager, analytics,
                        if (billingPeriod == BillingPeriod.YEARLY) SubscriptionTier.PREMIUM.yearlySku
                        else SubscriptionTier.PREMIUM.monthlySku)
                },
                features = listOf(
                    R.string.upgrade_feature_everything_in_pro,
                    R.string.upgrade_feature_ai_scanner,
                    R.string.upgrade_feature_accountability,
                    R.string.upgrade_feature_priority_support,
                    R.string.upgrade_feature_early_access
                )
            )

            errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(24.dp))
            ComparisonTable()
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.upgrade_legal_note),
                color = textMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun purchase(
    activity: Activity,
    billingManager: BillingManager,
    analytics: AnalyticsManager,
    sku: String
) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        val started = billingManager.let { bm ->
            // We expose launchPurchaseFlow indirectly via wrapper. We use
            // a Koin-resolved wrapper here. Easiest: call connect then
            // launch through the public surface.
            // The UpgradeScreen needs the wrapper, but we keep the public
            // surface minimal — so we add a convenience method on manager.
            // See BillingManager.purchase(activity, sku).
            bm.purchase(activity, sku)
        }
        if (started) {
            val tier = com.agon.app.billing.SubscriptionTier.fromSku(sku)
            if (tier != null) {
                analytics.logSubscriptionStarted(tier = tier.displayKey, period = if (sku.contains("yearly")) "yearly" else "monthly")
            }
        }
    }
}

@Composable
private fun BillingPeriodSwitch(
    selected: BillingPeriod,
    onSelect: (BillingPeriod) -> Unit
) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(card, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BillingPeriod.values().forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) primary else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(period) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(period.labelRes),
                        color = if (isSelected) background else text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (period == BillingPeriod.YEARLY) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) background else primary,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.upgrade_save_badge),
                                color = if (isSelected) primary else background,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    tier: SubscriptionTier,
    headline: String,
    price: ProductInfo?,
    yearlyPrice: ProductInfo?,
    isCurrent: Boolean,
    isHighlighted: Boolean = false,
    onSelect: () -> Unit,
    features: List<Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) primary.copy(alpha = 0.08f) else card
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isHighlighted) 2.dp else 1.dp,
            color = if (isHighlighted) primary else cardBorder
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isHighlighted) {
                    Icon(Icons.Default.Star, null, tint = primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(headline, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text)
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(primary, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.upgrade_current_plan), color = background, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price?.formattedPrice ?: "—",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = text
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(
                        if (price?.billingPeriod?.contains("YEAR", true) == true)
                            R.string.upgrade_per_year
                        else R.string.upgrade_per_month
                    ),
                    fontSize = 12.sp,
                    color = textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (price?.billingPeriod?.contains("YEAR", true) == true && yearlyPrice != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.upgrade_equivalent_monthly, yearlyPrice.formattedPrice),
                    fontSize = 11.sp,
                    color = textMuted
                )
            }
            Spacer(Modifier.height(12.dp))
            features.forEach { featureRes ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(featureRes),
                        fontSize = 13.sp,
                        color = text
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSelect,
                enabled = !isCurrent,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHighlighted) primary else primary.copy(alpha = 0.7f),
                    contentColor = background,
                    disabledContainerColor = cardBorder,
                    disabledContentColor = textMuted
                )
            ) {
                Text(
                    text = stringResource(
                        if (isCurrent) R.string.upgrade_btn_current
                        else R.string.upgrade_btn_subscribe
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ComparisonTable() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.upgrade_compare_title),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = text
            )
            Spacer(Modifier.height(12.dp))
            PremiumFeature.values().forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            when (feature) {
                                PremiumFeature.CORE_BLOCKING -> R.string.feat_core_blocking
                                PremiumFeature.UNLIMITED_SOCIAL_APPS -> R.string.feat_unlimited_social
                                PremiumFeature.AI_NSFW_SCANNER -> R.string.feat_ai_scanner
                                PremiumFeature.CLOUD_SYNC -> R.string.feat_cloud_sync
                                PremiumFeature.ACCOUNTABILITY_PARTNER -> R.string.feat_accountability
                                PremiumFeature.ADVANCED_STATISTICS -> R.string.feat_advanced_stats
                                PremiumFeature.STUDY_ROOM -> R.string.feat_study_room
                                PremiumFeature.CUSTOM_BLOCKLISTS -> R.string.feat_custom_lists
                            }
                        ),
                        fontSize = 13.sp,
                        color = text,
                        modifier = Modifier.weight(1f)
                    )
                    CheckCell(enabled = true)
                    Spacer(Modifier.width(12.dp))
                    CheckCell(enabled = feature.requiredTier.ordinal <= SubscriptionTier.PRO.ordinal)
                    Spacer(Modifier.width(12.dp))
                    CheckCell(enabled = feature.requiredTier.ordinal <= SubscriptionTier.PREMIUM.ordinal)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.upgrade_col_free), fontSize = 11.sp, color = textMuted, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.upgrade_col_pro), fontSize = 11.sp, color = textMuted, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.upgrade_col_premium), fontSize = 11.sp, color = primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CheckCell(enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(
                if (enabled) primary.copy(alpha = 0.15f) else cardBorder.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            Icon(Icons.Default.Check, null, tint = primary, modifier = Modifier.size(14.dp))
        }
    }
}

private enum class BillingPeriod(val labelRes: Int) {
    MONTHLY(com.agon.app.R.string.upgrade_period_monthly),
    YEARLY(com.agon.app.R.string.upgrade_period_yearly)
}
