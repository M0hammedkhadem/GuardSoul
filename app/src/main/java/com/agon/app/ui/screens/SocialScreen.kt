package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(vm: SocialViewModel) {
    val context = LocalContext.current
    val instagramMode by vm.instagramMode.collectAsStateWithLifecycle()
    val snapchatBlocked by vm.snapchat.collectAsStateWithLifecycle()
    val twitterBlocked by vm.twitter.collectAsStateWithLifecycle()
    val tiktokBlocked by vm.tiktok.collectAsStateWithLifecycle()
    val youtubeMode by vm.youtubeMode.collectAsStateWithLifecycle()
    val facebookMode by vm.facebookMode.collectAsStateWithLifecycle()
    val blocksToday by vm.blocksToday.collectAsStateWithLifecycle()
    val blocksPerApp by vm.blocksPerApp.collectAsStateWithLifecycle()
    val instagramServiceRunning by vm.instagramServiceRunning.collectAsStateWithLifecycle()
    val youtubeServiceRunning by vm.youtubeServiceRunning.collectAsStateWithLifecycle()
    val facebookServiceRunning by vm.facebookServiceRunning.collectAsStateWithLifecycle()
    
    var showInstagramSheet by remember { mutableStateOf(false) }
    var showYoutubeSheet by remember { mutableStateOf(false) }
    var showFacebookSheet by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var snackbarSeq by remember { mutableIntStateOf(0) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val blockedOn = context.getString(R.string.social_blocked_on)
    val blockedOff = context.getString(R.string.social_blocked_off)

    // Issue #216 Fix: Use packageName instead of appLabel for reliable lookup
    fun appCount(pkg: String): Int {
        return blocksPerApp.find { it.packageName == pkg }?.count ?: 0
    }

    LaunchedEffect(snackbarSeq) {
        if (snackbarSeq > 0) {
            snackbarHostState.showSnackbar(snackbarMessage)
        }
    }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_social_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, titleContentColor = text)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(scrollState)
        ) {
            SummaryCard(totalBlocks = blocksToday)
            Spacer(Modifier.height(16.dp))

            SectionHeader(Icons.Default.Block, stringResource(R.string.section_direct_block), danger)
            Spacer(Modifier.height(4.dp))
            
            ToggleRow(Icons.AutoMirrored.Filled.Chat, stringResource(R.string.app_snapchat), snapchatBlocked, { vm.setSnapchat(it); snackbarMessage = if (it) blockedOn else blockedOff; snackbarSeq++ }, count = appCount("com.snapchat.android"))
            ToggleRow(Icons.Default.Tag, stringResource(R.string.app_twitter), twitterBlocked, { vm.setTwitter(it); snackbarMessage = if (it) blockedOn else blockedOff; snackbarSeq++ }, count = appCount("com.twitter.android"))
            ToggleRow(Icons.Default.MusicNote, stringResource(R.string.app_tiktok), tiktokBlocked, { vm.setTiktok(it); snackbarMessage = if (it) blockedOn else blockedOff; snackbarSeq++ }, count = appCount("com.zhiliaoapp.musically"))

            Spacer(Modifier.height(24.dp))
            SectionHeader(Icons.Default.CameraAlt, stringResource(R.string.section_instagram), accent)
            Spacer(Modifier.height(4.dp))
            DropdownCard(
                title = stringResource(R.string.app_instagram),
                mode = instagramMode,
                badgeMap = mapOf(
                    "off" to Triple(Icons.Default.CheckCircle, stringResource(R.string.badge_open), success),
                    "full" to Triple(Icons.Default.Cancel, stringResource(R.string.badge_blocked_upper), danger),
                    "reels" to Triple(Icons.Default.Warning, stringResource(R.string.badge_partial), warning)
                ),
                serviceRunning = instagramMode != "off" && instagramServiceRunning,
                onClick = { if (instagramMode != "off" && !instagramServiceRunning) showAccessibilityDialog = true else showInstagramSheet = true }
            )
            if (instagramMode == "reels") {
                InfoBox(stringResource(R.string.info_instagram))
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(Icons.Default.Videocam, stringResource(R.string.section_youtube), shieldRed)
            Spacer(Modifier.height(4.dp))
            DropdownCard(
                title = stringResource(R.string.app_youtube),
                mode = youtubeMode,
                badgeMap = mapOf("off" to Triple(Icons.Default.CheckCircle, stringResource(R.string.badge_open), success), "full" to Triple(Icons.Default.Cancel, stringResource(R.string.badge_blocked_upper), danger), "shorts" to Triple(Icons.Default.Warning, stringResource(R.string.badge_partial), warning)),
                serviceRunning = youtubeMode != "off" && youtubeServiceRunning,
                onClick = { if (youtubeMode != "off" && !youtubeServiceRunning) showAccessibilityDialog = true else showYoutubeSheet = true }
            )
            if (youtubeMode == "shorts") {
                InfoBox(stringResource(R.string.info_youtube))
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader(Icons.Default.Groups, stringResource(R.string.section_facebook), primary)
            Spacer(Modifier.height(4.dp))
            DropdownCard(
                title = stringResource(R.string.app_facebook),
                mode = facebookMode,
                badgeMap = mapOf("off" to Triple(Icons.Default.CheckCircle, stringResource(R.string.badge_open), success), "full" to Triple(Icons.Default.Cancel, stringResource(R.string.badge_blocked_upper), danger), "reels" to Triple(Icons.Default.Warning, stringResource(R.string.badge_partial), warning)),
                serviceRunning = facebookMode != "off" && facebookServiceRunning,
                onClick = { if (facebookMode != "off" && !facebookServiceRunning) showAccessibilityDialog = true else showFacebookSheet = true }
            )
            if (facebookMode == "reels") {
                InfoBox(stringResource(R.string.info_facebook))
            }

            Spacer(Modifier.height(24.dp))
            HowItWorksCard()
            Spacer(Modifier.height(16.dp))

            // =========================================================
            // Shortstop — surgical scheduling & quota
            // =========================================================
            val quotaMin by vm.shortstopDailyQuotaMinutes.collectAsStateWithLifecycle()
            val breakIntervalMin by vm.shortstopBreakIntervalMinutes.collectAsStateWithLifecycle()
            val breakLengthMin by vm.shortstopBreakLengthMinutes.collectAsStateWithLifecycle()
            val minutesSpent by vm.shortstopMinutesSpentToday.collectAsStateWithLifecycle()
            val quotaExceeded by vm.shortstopDailyQuotaExceeded.collectAsStateWithLifecycle()
            val breakActive by vm.shortstopBreakActive.collectAsStateWithLifecycle()
            val blockedHourActive by vm.shortstopBlockedHourActive.collectAsStateWithLifecycle()

            SectionHeader(Icons.Default.Warning, stringResource(R.string.shortstop_settings_title), danger)
            Spacer(Modifier.height(4.dp))
            InfoBox(stringResource(R.string.shortstop_settings_subtitle))
            Spacer(Modifier.height(8.dp))

            ShortstopStepperCard(
                label = stringResource(R.string.shortstop_quota_label),
                summary = stringResource(R.string.shortstop_quota_summary, quotaMin),
                current = quotaMin,
                range = 0..120,
                step = 5,
                unit = " min",
                onValueChange = { vm.setShortstopDailyQuota(it) },
            )
            ShortstopStepperCard(
                label = stringResource(R.string.shortstop_break_interval_label),
                summary = stringResource(R.string.shortstop_break_interval_summary, breakIntervalMin),
                current = breakIntervalMin,
                range = 0..60,
                step = 5,
                unit = " min",
                onValueChange = { vm.setShortstopBreakInterval(it) },
            )
            ShortstopStepperCard(
                label = stringResource(R.string.shortstop_break_length_label),
                summary = stringResource(R.string.shortstop_break_length_summary, breakLengthMin),
                current = breakLengthMin,
                range = 0..30,
                step = 1,
                unit = " min",
                onValueChange = { vm.setShortstopBreakLength(it) },
            )

            if (quotaExceeded) {
                Spacer(Modifier.height(8.dp))
                InfoBox(stringResource(R.string.shortstop_reason_quota))
            }
            if (breakActive) {
                Spacer(Modifier.height(8.dp))
                InfoBox(stringResource(R.string.shortstop_reason_break))
            }
            if (blockedHourActive) {
                Spacer(Modifier.height(8.dp))
                InfoBox(stringResource(R.string.shortstop_reason_hours, ""))
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showInstagramSheet) {
        ModalBottomSheet(onDismissRequest = { showInstagramSheet = false }, containerColor = surface) {
            ModeSelectionSheet(
                title = stringResource(R.string.sheet_instagram_title),
                currentMode = instagramMode,
                options = listOf(
                    Triple("off", stringResource(R.string.option_no_block), stringResource(R.string.desc_instagram_off)),
                    Triple("full", stringResource(R.string.option_ig_full_block), stringResource(R.string.desc_instagram_full)),
                    Triple("reels", stringResource(R.string.option_block_reels), stringResource(R.string.desc_instagram_reels))
                ),
                onSelect = { vm.setInstagramMode(it); showInstagramSheet = false; snackbarMessage = if (it == "off") blockedOff else blockedOn; snackbarSeq++ }
            )
        }
    }

    if (showYoutubeSheet) {
        ModalBottomSheet(onDismissRequest = { showYoutubeSheet = false }, containerColor = surface) {
            ModeSelectionSheet(
                title = stringResource(R.string.sheet_youtube_title),
                currentMode = youtubeMode,
                options = listOf(
                    Triple("off", stringResource(R.string.option_no_block), stringResource(R.string.desc_youtube_off)),
                    Triple("full", stringResource(R.string.option_yt_full_block), stringResource(R.string.desc_youtube_full)),
                    Triple("shorts", stringResource(R.string.option_block_shorts), stringResource(R.string.desc_youtube_shorts))
                ),
                onSelect = { vm.setYoutubeMode(it); showYoutubeSheet = false; snackbarMessage = if (it == "off") blockedOff else blockedOn; snackbarSeq++ }
            )
        }
    }

    if (showFacebookSheet) {
        ModalBottomSheet(onDismissRequest = { showFacebookSheet = false }, containerColor = surface) {
            ModeSelectionSheet(
                title = stringResource(R.string.sheet_facebook_title),
                currentMode = facebookMode,
                options = listOf(
                    Triple("off", stringResource(R.string.option_no_block), stringResource(R.string.desc_facebook_off)),
                    Triple("full", stringResource(R.string.option_fb_full_block), stringResource(R.string.desc_facebook_full)),
                    Triple("reels", stringResource(R.string.option_block_reels), stringResource(R.string.desc_facebook_reels))
                ),
                onSelect = { vm.setFacebookMode(it); showFacebookSheet = false; snackbarMessage = if (it == "off") blockedOff else blockedOn; snackbarSeq++ }
            )
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text(stringResource(R.string.dialog_accessibility_title)) },
            text = { Text(stringResource(R.string.dialog_accessibility_text)) },
            confirmButton = {
                TextButton(onClick = { AccessibilityUtils.openAccessibilitySettings(context); showAccessibilityDialog = false }) {
                    Text(stringResource(R.string.dialog_accessibility_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) { Text(stringResource(R.string.btn_later)) }
            }
        )
    }
}

@Composable
private fun SummaryCard(totalBlocks: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(danger.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, stringResource(R.string.contentdesc_shield), tint = danger, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.summary_blocks_today), fontSize = 12.sp, color = textSecondary)
                Spacer(Modifier.height(2.dp))
                Text("$totalBlocks", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = text)
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, title, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
    }
}

@Composable
private fun ToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean, onToggle: (Boolean) -> Unit, count: Int = 0) {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, cardBorder), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = textMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = text, modifier = Modifier.weight(1f))
            if (count > 0 && checked) {
                Surface(color = danger.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, danger.copy(alpha = 0.25f)), modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("$count", color = danger, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Switch(checked = checked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = danger, checkedThumbColor = surface))
        }
    }
}

@Composable
private fun DropdownCard(title: String, mode: String, badgeMap: Map<String, Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Color>>, onClick: () -> Unit, serviceRunning: Boolean = true) {
    val badge = badgeMap[mode] ?: badgeMap["off"] ?: Triple(Icons.Default.Help, stringResource(R.string.contentdesc_help), textMuted)
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (mode != "off" && !serviceRunning) warning.copy(alpha = 0.5f) else cardBorder), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Medium, color = text, fontSize = 15.sp)
                    if (mode != "off") {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (serviceRunning) success else warning))
                    }
                }
            }
            Surface(color = badge.third.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, badge.third.copy(alpha = 0.3f))) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(badge.first, badge.second, tint = badge.third, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(badge.second, color = badge.third, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.contentdesc_arrow_down), tint = textMuted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InfoBox(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = warning.copy(alpha = 0.05f)), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, warning.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, stringResource(R.string.contentdesc_info), tint = warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HowItWorksCard() {
    Card(colors = CardDefaults.cardColors(containerColor = card), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, cardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.card_how_it_works), fontWeight = FontWeight.Bold, color = text)
            Spacer(Modifier.height(8.dp))
            HowItWorksRow(Icons.Default.Cancel, stringResource(R.string.row_how_full_block), danger)
            HowItWorksRow(Icons.Default.VisibilityOff, stringResource(R.string.row_how_partial_block), warning)
            HowItWorksRow(Icons.Default.CheckCircle, stringResource(R.string.row_how_no_block), success)
        }
    }
}

@Composable
private fun HowItWorksRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, text, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, color = textSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectionSheet(title: String, currentMode: String, options: List<Triple<String, String, String>>, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
        Spacer(Modifier.height(16.dp))
        options.forEach { (key, label, desc) ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(key) }.padding(vertical = 12.dp).then(if (key == currentMode) Modifier.background(primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = key == currentMode, onClick = { onSelect(key) }, colors = RadioButtonDefaults.colors(selectedColor = primary))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(label, fontWeight = FontWeight.Medium, color = text)
                    Text(desc, fontSize = 12.sp, color = textSecondary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ShortstopStepperCard(
    label: String,
    summary: String,
    current: Int,
    range: IntRange,
    step: Int,
    unit: String,
    onValueChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, color = text, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(summary, fontSize = 12.sp, color = textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { onValueChange((current - step).coerceIn(range)) },
                ) { Text("−", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Text(
                    "$current$unit",
                    color = text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { onValueChange((current + step).coerceIn(range)) },
                ) { Text("+", color = text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
