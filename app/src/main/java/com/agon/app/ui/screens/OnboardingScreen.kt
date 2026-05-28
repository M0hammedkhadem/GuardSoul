package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.ui.theme.*
import kotlinx.coroutines.launch

enum class OnboardingStep {
    WELCOME, ACCESSIBILITY, VPN, DEVICE_ADMIN, OVERLAY, USAGE_ACCESS, NOTIFICATIONS, COMPLETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onBack: () -> Unit = {},
    accessibilityGranted: Boolean = false,
    vpnGranted: Boolean = false,
    deviceAdminGranted: Boolean = false,
    overlayGranted: Boolean = false,
    usageAccessGranted: Boolean = false,
    notificationGranted: Boolean = false
) {
    val pagerState = rememberPagerState(pageCount = { 8 }, initialPage = 0)
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pagerState.currentPage < 7) {
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1) / 7f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = primary,
                    trackColor = surfaceLight,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                val step = OnboardingStep.entries[page]
                AnimatedContent(targetState = page, transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                }) { _ ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        when (step) {
                            OnboardingStep.WELCOME -> WelcomeContent()
                            OnboardingStep.COMPLETE -> CompleteContent()
                            else -> PermissionContent(
                                step = step,
                                isGranted = when (step) {
                                    OnboardingStep.ACCESSIBILITY -> accessibilityGranted
                                    OnboardingStep.VPN -> vpnGranted
                                    OnboardingStep.DEVICE_ADMIN -> deviceAdminGranted
                                    OnboardingStep.OVERLAY -> overlayGranted
                                    OnboardingStep.USAGE_ACCESS -> usageAccessGranted
                                    OnboardingStep.NOTIFICATIONS -> notificationGranted
                                    else -> false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (OnboardingStep.entries[pagerState.currentPage]) {
                OnboardingStep.WELCOME -> {
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.onboarding_btn_start)) }
                }
                OnboardingStep.COMPLETE -> {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.onboarding_btn_protect)) }
                }
                else -> {
                    val step = OnboardingStep.entries[pagerState.currentPage]
                    val isGranted = when (step) {
                        OnboardingStep.ACCESSIBILITY -> accessibilityGranted
                        OnboardingStep.VPN -> vpnGranted
                        OnboardingStep.DEVICE_ADMIN -> deviceAdminGranted
                        OnboardingStep.OVERLAY -> overlayGranted
                        OnboardingStep.USAGE_ACCESS -> usageAccessGranted
                        OnboardingStep.NOTIFICATIONS -> notificationGranted
                        else -> false
                    }

                    if (!isGranted) {
                        Button(
                            onClick = {
                                val key = when (step) {
                                    OnboardingStep.ACCESSIBILITY -> "accessibility"
                                    OnboardingStep.VPN -> "vpn"
                                    OnboardingStep.DEVICE_ADMIN -> "device_admin"
                                    OnboardingStep.OVERLAY -> "overlay"
                                    OnboardingStep.USAGE_ACCESS -> "usage_access"
                                    OnboardingStep.NOTIFICATIONS -> "notifications"
                                    else -> null
                                }
                                key?.let { onRequestPermission(it) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.onboarding_btn_grant)) }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (isGranted) {
                        Button(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.onboarding_btn_continue)) }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                                Text(stringResource(R.string.contentdesc_back), color = textMuted)
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }
                        ) {
                            Text(stringResource(R.string.onboarding_skip_step), color = textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeContent() {
    Icon(
        Icons.Default.Shield,
        contentDescription = null,
        tint = primary,
        modifier = Modifier.size(80.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.app_name),
        fontSize = 28.sp,
        fontWeight = FontWeight.Black,
        color = text,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onboarding_welcome_desc),
        fontSize = 14.sp,
        color = textSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CompleteContent() {
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        tint = success,
        modifier = Modifier.size(96.dp)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(R.string.onboarding_step7_title),
        fontSize = 28.sp,
        fontWeight = FontWeight.Black,
        color = text,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.onboarding_step7_desc),
        fontSize = 14.sp,
        color = textSecondary,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PermissionContent(step: OnboardingStep, isGranted: Boolean) {
    val (icon, titleRes, descRes, color) = when (step) {
        OnboardingStep.ACCESSIBILITY -> listOf(
            Icons.Default.Accessibility, R.string.onboarding_step2_title,
            R.string.onboarding_step2_desc, accent
        )
        OnboardingStep.VPN -> listOf(
            Icons.Default.VpnKey, R.string.onboarding_step3_title,
            R.string.onboarding_step3_desc, warning
        )
        OnboardingStep.DEVICE_ADMIN -> listOf(
            Icons.Default.AdminPanelSettings, R.string.onboarding_step4_title,
            R.string.onboarding_step4_desc, shieldGreen
        )
        OnboardingStep.OVERLAY -> listOf(
            Icons.Default.Widgets, R.string.onboarding_step5_title,
            R.string.onboarding_step5_desc, accent
        )
        OnboardingStep.USAGE_ACCESS -> listOf(
            Icons.Default.DataUsage, R.string.onboarding_step6_title,
            R.string.onboarding_step6_desc, accent
        )
        OnboardingStep.NOTIFICATIONS -> listOf(
            Icons.Default.Notifications, R.string.onboarding_step7_title,
            R.string.onboarding_step7_desc, primary
        )
        else -> return
    }

    @Suppress("UNCHECKED_CAST")
    val iconVector = icon as ImageVector
    @Suppress("UNCHECKED_CAST")
    val title = titleRes as Int
    @Suppress("UNCHECKED_CAST")
    val desc = descRes as Int
    @Suppress("UNCHECKED_CAST")
    val tint = color as androidx.compose.ui.graphics.Color

    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isGranted) success.copy(alpha = 0.1f) else tint.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isGranted) Icons.Default.CheckCircle else iconVector,
            contentDescription = null,
            tint = if (isGranted) success else tint,
            modifier = Modifier.size(60.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Text(
        stringResource(title),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = text,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(desc),
        fontSize = 14.sp,
        color = textSecondary,
        textAlign = TextAlign.Center
    )

    if (isGranted) {
        Spacer(Modifier.height(16.dp))
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.onboarding_granted_chip), color = success) },
            leadingIcon = {
                Icon(
                    Icons.Default.Check, null,
                    tint = success, modifier = Modifier.size(16.dp)
                )
            }
        )
    }
}
