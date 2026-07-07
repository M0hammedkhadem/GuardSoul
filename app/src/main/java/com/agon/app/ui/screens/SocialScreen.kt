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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(vm: SocialViewModel) {
    val context = LocalContext.current
    val youtubeMode by vm.youtubeMode.collectAsStateWithLifecycle()
    val blocksToday by vm.blocksToday.collectAsStateWithLifecycle()
    val accessibilityServiceRunning by vm.accessibilityServiceRunning.collectAsStateWithLifecycle()
    val youtubeServiceRunning by vm.youtubeServiceRunning.collectAsStateWithLifecycle()

    val instagramBlocked by vm.instagramBlocked.collectAsStateWithLifecycle()
    val snapchatBlocked by vm.snapchatBlocked.collectAsStateWithLifecycle()
    val twitterBlocked by vm.twitterBlocked.collectAsStateWithLifecycle()
    val tiktokBlocked by vm.tiktokBlocked.collectAsStateWithLifecycle()
    val facebookMode by vm.facebookMode.collectAsStateWithLifecycle()
    val facebookServiceRunning by vm.facebookServiceRunning.collectAsStateWithLifecycle()

    var showYoutubeSheet by remember { mutableStateOf(false) }
    var showFacebookSheet by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var snackbarSeq by remember { mutableIntStateOf(0) }
    var snackbarMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val blockedOn = context.getString(R.string.social_blocked_on)
    val blockedOff = context.getString(R.string.social_blocked_off)

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            SummaryCard(totalBlocks = blocksToday)
            Spacer(Modifier.height(16.dp))

            SectionHeader(
                Icons.Default.Block,
                stringResource(R.string.section_direct_block),
                danger
            )
            Spacer(Modifier.height(8.dp))

            DirectBlockCard(
                appName = stringResource(R.string.app_instagram),
                packageName = "com.instagram.android",
                isBlocked = instagramBlocked,
                onToggle = { vm.toggleInstagram() }
            )
            Spacer(Modifier.height(12.dp))
            DirectBlockCard(
                appName = stringResource(R.string.app_snapchat),
                packageName = "com.snapchat.android",
                isBlocked = snapchatBlocked,
                onToggle = { vm.toggleSnapchat() }
            )
            Spacer(Modifier.height(12.dp))
            DirectBlockCard(
                appName = "X (Twitter)",
                packageName = "com.twitter.android",
                isBlocked = twitterBlocked,
                onToggle = { vm.toggleTwitter() }
            )
            Spacer(Modifier.height(12.dp))
            DirectBlockCard(
                appName = stringResource(R.string.app_tiktok),
                packageName = "com.zhiliaoapp.musically",
                isBlocked = tiktokBlocked,
                onToggle = { vm.toggleTiktok() }
            )

            Spacer(Modifier.height(24.dp))

            SectionHeader(
                Icons.Default.Videocam,
                stringResource(R.string.section_youtube),
                shieldRed
            )
            Spacer(Modifier.height(8.dp))
            DropdownCard(
                title = stringResource(R.string.app_youtube),
                packageName = "com.google.android.youtube",
                mode = youtubeMode,
                badgeMap = mapOf(
                    "off" to Triple(Icons.Default.CheckCircle, stringResource(R.string.badge_open), success),
                    "full" to Triple(Icons.Default.Cancel, stringResource(R.string.badge_blocked_upper), danger),
                    "shorts" to Triple(Icons.Default.Warning, stringResource(R.string.badge_partial), warning)
                ),
                serviceRunning = youtubeMode != "off" && youtubeServiceRunning,
                onClick = {
                    if (!accessibilityServiceRunning) showAccessibilityDialog = true
                    else showYoutubeSheet = true
                }
            )
            if (youtubeMode == "shorts") {
                InfoBox(stringResource(R.string.info_youtube))
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader(
                Icons.Default.Shield,
                stringResource(R.string.section_facebook),
                primary
            )
            Spacer(Modifier.height(8.dp))
            DropdownCard(
                title = stringResource(R.string.app_facebook),
                packageName = "com.facebook.katana",
                mode = facebookMode,
                badgeMap = mapOf(
                    "off" to Triple(Icons.Default.CheckCircle, stringResource(R.string.badge_open), success),
                    "full" to Triple(Icons.Default.Cancel, stringResource(R.string.badge_blocked_upper), danger),
                    "reels" to Triple(Icons.Default.Warning, stringResource(R.string.badge_partial), warning)
                ),
                serviceRunning = facebookServiceRunning,
                onClick = {
                    if (!accessibilityServiceRunning) showAccessibilityDialog = true
                    else showFacebookSheet = true
                }
            )
            if (facebookMode == "reels") {
                InfoBox(stringResource(R.string.info_facebook))
                Spacer(Modifier.height(4.dp))
                InfoBox(stringResource(R.string.info_facebook_deep_links))
            }
            if (facebookMode == "full") {
                InfoBox(stringResource(R.string.info_facebook_full_mode))
            }

            Spacer(Modifier.height(32.dp))
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
                onSelect = {
                    vm.setYoutubeMode(it)
                    showYoutubeSheet = false
                    snackbarMessage = if (it == "off") blockedOff else blockedOn
                    snackbarSeq++
                }
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
                onSelect = {
                    vm.setFacebookMode(it)
                    showFacebookSheet = false
                    snackbarMessage = if (it == "off") blockedOff else blockedOn
                    snackbarSeq++
                }
            )
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text(stringResource(R.string.dialog_accessibility_title)) },
            text = { Text(stringResource(R.string.dialog_accessibility_text)) },
            confirmButton = {
                TextButton(onClick = {
                    AccessibilityUtils.openAccessibilitySettings(context)
                    showAccessibilityDialog = false
                }) {
                    Text(stringResource(R.string.dialog_accessibility_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text(stringResource(R.string.btn_later))
                }
            }
        )
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val fallbackRes = when (packageName) {
        "com.instagram.android" -> R.drawable.ic_instagram
        "com.twitter.android" -> R.drawable.ic_twitter_x
        "com.snapchat.android" -> R.drawable.ic_snapchat
        "com.zhiliaoapp.musically" -> R.drawable.ic_tiktok
        "com.google.android.youtube" -> R.drawable.ic_youtube
        "com.facebook.katana" -> R.drawable.ic_facebook
        else -> R.drawable.ic_instagram
    }
    val bitmap = remember(packageName) {
        try {
            val icon = context.packageManager.getApplicationIcon(packageName)
            icon.toBitmap(128, 128).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = packageName,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(fallbackRes),
            contentDescription = packageName,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
private fun DirectBlockCard(
    appName: String,
    packageName: String,
    isBlocked: Boolean,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = isBlocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = text,
                    checkedTrackColor = danger,
                    uncheckedThumbColor = textMuted,
                    uncheckedTrackColor = surfaceLight
                ),
                modifier = Modifier.size(48.dp, 26.dp)
            )
            Spacer(Modifier.width(12.dp))
            Surface(
                color = if (isBlocked) danger.copy(alpha = 0.15f) else success.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, if (isBlocked) danger.copy(alpha = 0.5f) else success.copy(alpha = 0.5f))
            ) {
                Text(
                    text = if (isBlocked) stringResource(R.string.badge_blocked_upper) else stringResource(R.string.badge_open),
                    color = if (isBlocked) danger else success,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(appName, color = text, fontSize = 15.sp)
            Spacer(Modifier.width(12.dp))
            AppIcon(
                packageName = packageName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
        }
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
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(danger.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
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
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = text, fontSize = 16.sp)
    }
}

@Composable
private fun DropdownCard(
    title: String,
    packageName: String,
    mode: String,
    badgeMap: Map<String, Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Color>>,
    onClick: () -> Unit,
    serviceRunning: Boolean = true
) {
    val badge = badgeMap[mode] ?: badgeMap["off"] ?: Triple(Icons.Default.Help, stringResource(R.string.contentdesc_help), textMuted)
    Card(
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (mode != "off" && !serviceRunning) warning.copy(alpha = 0.5f) else cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                packageName = packageName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Medium, color = text, fontSize = 15.sp)
                    if (mode != "off") {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (serviceRunning) success else warning)
                        )
                    }
                }
            }
            Surface(
                color = badge.third.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, badge.third.copy(alpha = 0.3f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(badge.first, badge.second, tint = badge.third, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(badge.second, color = badge.third, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.KeyboardArrowDown,
                stringResource(R.string.contentdesc_arrow_down),
                tint = textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InfoBox(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = warning.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, warning.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, stringResource(R.string.contentdesc_info), tint = warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = textSecondary, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectionSheet(
    title: String,
    currentMode: String,
    options: List<Triple<String, String, String>>,
    onSelect: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
        Spacer(Modifier.height(16.dp))
        options.forEach { (key, label, desc) ->
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onSelect(key) }
                    .padding(vertical = 12.dp)
                    .then(
                        if (key == currentMode) Modifier
                            .background(primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = key == currentMode,
                    onClick = { onSelect(key) },
                    colors = RadioButtonDefaults.colors(selectedColor = primary)
                )
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
