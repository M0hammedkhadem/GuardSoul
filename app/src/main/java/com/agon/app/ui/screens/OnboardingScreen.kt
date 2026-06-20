package com.agon.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onRequestPermission: (String) -> Unit,
    onSetName: (String) -> Unit = {},
    onSetLanguage: (String) -> Unit = {},
    onRequestLanguageChange: (name: String, language: String) -> Unit = { _, _ -> },
    initialName: String = "",
    initialLanguage: String = "en",
    accessibilityGranted: Boolean = false,
) {
    val pagerState = rememberPagerState(pageCount = { 3 }, initialPage = 0)
    val scope = rememberCoroutineScope()

    var nameInput by rememberSaveable { mutableStateOf(initialName) }
    var selectedLanguage by rememberSaveable { mutableStateOf(initialLanguage) }

    val layoutDirection = if (selectedLanguage == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (pagerState.currentPage < 2) {
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1) / 2f },
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
                AnimatedContent(targetState = page, transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                }) { _ ->
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (page == 0) Arrangement.Top else Arrangement.Center
                    ) {
                        when (page) {
                            0 -> WelcomeContent(
                                nameInput = nameInput,
                                onNameChange = { nameInput = it },
                                selectedLanguage = selectedLanguage,
                                onLanguageChange = { lang ->
                                    selectedLanguage = lang
                                    onRequestLanguageChange(nameInput, lang)
                                }
                            )
                            1 -> PermissionContent(
                                isGranted = accessibilityGranted
                            )
                            2 -> CompleteContent()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            when (pagerState.currentPage) {
                0 -> {
                    Button(
                        onClick = {
                            onSetName(nameInput)
                            onSetLanguage(selectedLanguage)
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.onboarding_btn_start)) }
                }
                2 -> {
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(stringResource(R.string.onboarding_btn_protect)) }
                }
                1 -> {
                    if (!accessibilityGranted) {
                        Button(
                            onClick = { onRequestPermission("accessibility") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.onboarding_btn_grant)) }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (accessibilityGranted) {
                        Button(
                            onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(stringResource(R.string.onboarding_btn_continue)) }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(0) } }) {
                            Text(stringResource(R.string.contentdesc_back), color = textMuted)
                        }
                        TextButton(
                            onClick = { scope.launch { pagerState.animateScrollToPage(2) } }
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
private fun WelcomeContent(
    nameInput: String,
    onNameChange: (String) -> Unit,
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Default.Shield, contentDescription = null, tint = primary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), fontSize = 28.sp, fontWeight = FontWeight.Black, color = text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.onboarding_welcome_desc), fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.onboarding_profile_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primary, unfocusedBorderColor = cardBorder,
                focusedLabelColor = primary, unfocusedLabelColor = textMuted, cursorColor = primary
            )
        )
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.onboarding_language_label), fontSize = 14.sp, color = textSecondary)
        Spacer(Modifier.height(12.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LanguageChip(label = "عربية", selected = selectedLanguage == "ar", onClick = { onLanguageChange("ar") })
                LanguageChip(label = "English", selected = selectedLanguage == "en", onClick = { onLanguageChange("en") })
            }
        }
    }
}

@Composable
private fun CompleteContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = success, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_step7_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_step7_desc), fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(20.dp),
        color = if (selected) primary else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) primary else cardBorder),
        modifier = Modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp)) {
            Text(label, color = if (selected) background else text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PermissionContent(isGranted: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (isGranted) success.copy(alpha = 0.1f) else primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.Accessibility,
                contentDescription = null,
                tint = if (isGranted) success else primary,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.onboarding_step2_title), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_step2_desc), fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center)
        if (isGranted) {
            Spacer(Modifier.height(16.dp))
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.onboarding_granted_chip), color = success) },
                leadingIcon = { Icon(Icons.Default.Check, null, tint = success, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}
