package com.agon.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.LanguageManager
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.guardianApp
import com.agon.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSocial: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToPinSetup: () -> Unit = {},
    onBack: () -> Unit,
    vm: SettingsViewModel? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.agon.app.GuardianApp
    val settings = app.repository.getAppSettings()
    val scrollState = rememberScrollState()

    val delayDays by settings.deactivationDelayFlow.collectAsState(initial = 0)
    var showDelayDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.contentdesc_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text,
                    navigationIconContentColor = text
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(stringResource(R.string.section_quick_access), fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    SettingsRow(icon = Icons.Default.People, title = stringResource(R.string.row_social_media), onClick = onNavigateToSocial)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.VpnKey, title = stringResource(R.string.row_permissions_settings), onClick = onNavigateToPermissions)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Lock, title = stringResource(R.string.profile_pin_protection), onClick = onNavigateToPinSetup)
                    HorizontalDivider(color = cardBorder)
                    SettingsRow(icon = Icons.Default.Restore, title = stringResource(R.string.btn_reset_all_settings)) { vm?.resetAllSettings() }
                }
            }

            HorizontalDivider(color = cardBorder, modifier = Modifier.padding(vertical = 16.dp))

            Text("Protection", fontWeight = FontWeight.Bold, color = textSecondary, modifier = Modifier.padding(bottom = 12.dp, start = 4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    // Deactivation Delay chooser
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showDelayDialog = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.HourglassEmpty, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.card_delay_title), color = text, fontSize = 16.sp)
                            Text(text = "Current: ${delayDays} days", color = textSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                    }
                    HorizontalDivider(color = cardBorder)
                    // Language toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showLanguageDialog = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Translate, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Language", color = text, fontSize = 16.sp)
                            Text(text = LanguageManager.currentLanguageCode.uppercase(), color = textSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = card),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.about_title), color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.about_version), color = textMuted, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        color = textSecondary, fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delay selection dialog
    if (showDelayDialog) {
        AlertDialog(
            onDismissRequest = { showDelayDialog = false },
            title = { Text(stringResource(R.string.card_delay_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val options = listOf(0, 2, 7, 15, 30)
                    options.forEach { days ->
                        val selected = delayDays == days
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm?.setDeactivationDelay(days)
                                    showDelayDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                .then(
                                    if (selected) Modifier.background(
                                        primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    vm?.setDeactivationDelay(days)
                                    showDelayDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (days) {
                                    0 -> stringResource(R.string.delay_no_delay)
                                    2 -> stringResource(R.string.delay_2_days)
                                    7 -> stringResource(R.string.delay_7_days)
                                    15 -> stringResource(R.string.delay_15_days)
                                    30 -> stringResource(R.string.delay_1_month)
                                    else -> "$days days"
                                },
                                color = if (selected) primary else text,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDelayDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Language selection dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Language", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val options = listOf("en" to "English", "ar" to "العربية")
                    options.forEach { (code, label) ->
                        val selected = LanguageManager.currentLanguageCode == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LanguageManager.setLanguage(context, code)
                                    (context as Activity).recreate()
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                                .then(
                                    if (selected) Modifier.background(
                                        primary.copy(alpha = 0.1f),
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    LanguageManager.setLanguage(context, code)
                                    (context as Activity).recreate()
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = if (selected) primary else text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = textMuted)
    }
}
