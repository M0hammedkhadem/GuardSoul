package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingStep(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val permissionKey: String? = null,
    val color: androidx.compose.ui.graphics.Color = primary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: (String) -> Unit,
    onRequestPermission: (String) -> Unit,
    accessibilityGranted: Boolean = false,
    vpnGranted: Boolean = false,
    deviceAdminGranted: Boolean = false,
    overlayGranted: Boolean = false,
    usageAccessGranted: Boolean = false
) {
    var profileName by remember { mutableStateOf("") }
    var currentPage by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 7 }, initialPage = 0)
    val scope = rememberCoroutineScope()

    val steps = listOf(
        OnboardingStep(
            Icons.Default.Person, "Welcome to Guardian",
            "Your digital wellness shield. Block distractions, filter explicit content, and build healthier digital habits.\n\nLet's set up your profile in 5 quick steps.",
            color = primary
        ),
        OnboardingStep(
            Icons.Default.Accessibility, "Accessibility Service",
            "Guardian needs this to detect when you open blocked apps and enforce focus rules.\n\nYou'll be taken to system settings to enable it.",
            "accessibility", accent
        ),
        OnboardingStep(
            Icons.Default.VpnKey, "VPN Permission",
            "Required for the Porn Blocker to filter web traffic and enforce safe search on all browsers.\n\nYou'll see a VPN connection request.",
            "vpn", warning
        ),
        OnboardingStep(
            Icons.Default.AdminPanelSettings, "Device Admin",
            "Prevents the app from being uninstalled and settings from being tampered with.\n\nYou'll be asked to activate device admin.",
            "device_admin", shieldGreen
        ),
        OnboardingStep(
            Icons.Default.Widgets, "Display Overlay",
            "Allows Guardian to show the block screen on top of restricted apps.\n\nYou'll be taken to system overlay settings.",
            "overlay", accent
        ),
        OnboardingStep(
            Icons.Default.BarChart, "Usage Access",
            "Required to detect which app is currently open and track your daily usage limits.\n\nYou'll be taken to usage access settings.",
            "usage", warning
        ),
        OnboardingStep(
            Icons.Default.CheckCircle, "You're All Set!",
            "Guardian is now ready to protect your digital wellness.\n\nReview and customize your settings anytime from the app.",
            color = success
        )
    )

    val permissionsGranted = accessibilityGranted && vpnGranted && deviceAdminGranted && overlayGranted && usageAccessGranted

    LaunchedEffect(pagerState.currentPage) { currentPage = pagerState.currentPage }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (currentPage < 6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
            ) {
                for (i in 0 until 6) {
                    Box(
                        modifier = Modifier
                            .width(if (i == currentPage) 24.dp else 8.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i <= currentPage) primary else surfaceLight)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = false
        ) { page ->
            val step = steps[page]
            AnimatedContent(targetState = page, transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            }) { _ ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (page == 0) {
                        // Profile setup on welcome page
                        Icon(step.icon, null, tint = step.color, modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(step.title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = text, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(step.description, fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Your name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else if (page == 6) {
                        // Final page
                        Icon(step.icon, null, tint = step.color, modifier = Modifier.size(96.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(step.title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = text, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(step.description, fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center)
                    } else {
                        // Permission pages
                        val isGranted = when (step.permissionKey) {
                            "accessibility" -> accessibilityGranted
                            "vpn" -> vpnGranted
                            "device_admin" -> deviceAdminGranted
                            "overlay" -> overlayGranted
                            "usage" -> usageAccessGranted
                            else -> false
                        }

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isGranted) success.copy(alpha = 0.1f) else step.color.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isGranted) Icons.Default.CheckCircle else step.icon,
                                null,
                                tint = if (isGranted) success else step.color,
                                modifier = Modifier.size(60.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(step.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(step.description, fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center)

                        if (isGranted) {
                            Spacer(Modifier.height(16.dp))
                            AssistChip(onClick = {}, label = { Text("Granted", color = success) }, leadingIcon = { Icon(Icons.Default.Check, null, tint = success, modifier = Modifier.size(16.dp)) })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bottom buttons
        when (currentPage) {
            0 -> {
                Button(
                    onClick = {
                        val name = profileName.ifBlank { "User" }
                        if (currentPage < 6) scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                        else onComplete(name)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Get Started") }
            }
            6 -> {
                Button(
                    onClick = { onComplete(profileName.ifBlank { "User" }) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Start Protecting") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onComplete(profileName.ifBlank { "User" }) }) {
                    Text("Skip", color = textMuted)
                }
            }
            else -> {
                // Permission page
                val step = steps[currentPage]
                val isGranted = when (step.permissionKey) {
                    "accessibility" -> accessibilityGranted
                    "vpn" -> vpnGranted
                    "device_admin" -> deviceAdminGranted
                    "overlay" -> overlayGranted
                    "usage" -> usageAccessGranted
                    else -> false
                }

                if (!isGranted) {
                    Button(
                        onClick = { step.permissionKey?.let { onRequestPermission(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Grant Permission") }
                }

                Spacer(Modifier.height(8.dp))
                if (isGranted) {
                    Button(
                        onClick = { if (currentPage < 6) scope.launch { pagerState.animateScrollToPage(currentPage + 1) } },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Continue") }
                }

                TextButton(
                    onClick = { if (currentPage < 6) scope.launch { pagerState.animateScrollToPage(currentPage + 1) } }
                ) {
                    Text("Skip this step", color = textMuted)
                }
            }
        }
    }
}
