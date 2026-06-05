package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.account.UserSession
import com.agon.app.billing.BillingManager
import com.agon.app.billing.SubscriptionTier
import com.agon.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    session: UserSession,
    billingManager: BillingManager,
    onBack: () -> Unit,
    onSignInClicked: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    cloudSyncEnabled: Boolean,
    cloudLastSyncAt: Long
) {
    val tier by billingManager.tier.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            ProfileHeader(session, tier)
            Spacer(Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.account_section_subscription))
            ActionRow(
                icon = Icons.Default.Star,
                title = stringResource(R.string.account_subscription_title),
                subtitle = when (tier) {
                    SubscriptionTier.FREE -> stringResource(R.string.account_tier_free)
                    SubscriptionTier.PRO -> stringResource(R.string.account_tier_pro)
                    SubscriptionTier.PREMIUM -> stringResource(R.string.account_tier_premium)
                },
                onClick = onOpenSubscription
            )
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.account_section_data))
            ActionRow(
                icon = Icons.Default.Cloud,
                title = stringResource(R.string.account_cloud_sync),
                subtitle = if (cloudSyncEnabled) {
                    if (cloudLastSyncAt > 0) {
                        stringResource(R.string.account_cloud_synced_format, formatRelativeTime(cloudLastSyncAt))
                    } else stringResource(R.string.account_cloud_enabled)
                } else stringResource(R.string.account_cloud_disabled),
                trailing = {
                    Switch(
                        checked = cloudSyncEnabled,
                        onCheckedChange = onToggleCloudSync,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = background,
                            checkedTrackColor = primary
                        )
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            SectionHeader(stringResource(R.string.account_section_legal))
            ActionRow(
                icon = Icons.Default.Description,
                title = stringResource(R.string.privacy_title),
                subtitle = stringResource(R.string.privacy_subtitle),
                onClick = onOpenPrivacy
            )
            Spacer(Modifier.height(8.dp))
            ActionRow(
                icon = Icons.Default.Article,
                title = stringResource(R.string.terms_title),
                subtitle = stringResource(R.string.terms_subtitle),
                onClick = onOpenTerms
            )
            Spacer(Modifier.height(20.dp))
            if (session is UserSession.SignedIn && !session.isAnonymous) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_btn_signout))
                }
            } else {
                Button(
                    onClick = onSignInClicked,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = background)
                ) {
                    Text(stringResource(R.string.account_btn_signin))
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProfileHeader(session: UserSession, tier: SubscriptionTier) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (session) {
                        is UserSession.SignedIn -> session.initials
                        UserSession.SignedOut -> "G"
                    },
                    color = background,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (session) {
                        is UserSession.SignedIn -> session.displayName.ifBlank {
                            session.email.ifBlank { stringResource(R.string.account_anonymous_label) }
                        }
                        UserSession.SignedOut -> stringResource(R.string.account_signed_out)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = text
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (session) {
                        is UserSession.SignedIn -> when (session.provider) {
                            "anonymous" -> stringResource(R.string.account_provider_anonymous)
                            "email" -> session.email
                            "google" -> stringResource(R.string.account_provider_google)
                            else -> stringResource(R.string.account_provider_unknown)
                        }
                        UserSession.SignedOut -> stringResource(R.string.account_signin_prompt)
                    },
                    fontSize = 12.sp,
                    color = textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(
                            when (tier) {
                                SubscriptionTier.FREE -> cardBorder
                                SubscriptionTier.PRO -> primary.copy(alpha = 0.15f)
                                SubscriptionTier.PREMIUM -> primary
                            },
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (tier) {
                            SubscriptionTier.FREE -> stringResource(R.string.account_tier_free)
                            SubscriptionTier.PRO -> stringResource(R.string.account_tier_pro)
                            SubscriptionTier.PREMIUM -> stringResource(R.string.account_tier_premium)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tier == SubscriptionTier.PREMIUM) background else primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = textMuted,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = text)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = textSecondary)
            }
            trailing?.invoke()
        }
    }
}

private fun formatRelativeTime(epoch: Long): String {
    val delta = System.currentTimeMillis() - epoch
    val minutes = delta / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
