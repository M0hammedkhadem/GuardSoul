package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.agon.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.data.GuardianState
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel
import androidx.compose.ui.platform.LocalContext
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    viewModel: GuardianViewModel = viewModel(),
    onLaunchFacebookWrapper: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showYoutubeSheet by remember { mutableStateOf(false) }
    var showFacebookSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_social_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text
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
            // Direct Block Section
            SectionHeader(icon = Icons.Default.Block, title = stringResource(R.string.section_direct_block), color = danger)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    ToggleRow(
                        name = stringResource(R.string.app_instagram),
                        color = Color(0xFFE1306C),
                        isBlocked = state.instagramBlocked,
                        onToggle = { viewModel.toggleInstagram() }
                    )
                    HorizontalDivider(color = cardBorder)
                    ToggleRow(
                        name = stringResource(R.string.app_snapchat),
                        color = Color(0xFFFFFC00),
                        isBlocked = state.snapchatBlocked,
                        onToggle = { viewModel.toggleSnapchat() }
                    )
                    HorizontalDivider(color = cardBorder)
                    ToggleRow(
                        name = stringResource(R.string.app_twitter),
                        color = Color(0xFF1DA1F2),
                        isBlocked = state.twitterBlocked,
                        onToggle = { viewModel.toggleTwitter() }
                    )
                    HorizontalDivider(color = cardBorder)
                    ToggleRow(
                        name = stringResource(R.string.app_tiktok),
                        color = Color(0xFF69C9D0),
                        isBlocked = state.tiktokBlocked,
                        onToggle = { viewModel.toggleTiktok() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // YouTube Section
            SectionHeader(icon = Icons.Default.PlayArrow, title = stringResource(R.string.app_youtube), color = Color(0xFFFF0000))
            Spacer(modifier = Modifier.height(16.dp))
            DropdownCard(
                currentMode = state.youtubeMode,
                onClick = { showYoutubeSheet = true }
            )
            if (state.youtubeMode == "shorts") {
                Spacer(modifier = Modifier.height(8.dp))
                InfoBox(stringResource(R.string.info_youtube))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Facebook Section
            SectionHeader(icon = Icons.Default.Facebook, title = stringResource(R.string.app_facebook), color = Color(0xFF1877F2))
            Spacer(modifier = Modifier.height(16.dp))
            DropdownCard(
                currentMode = state.facebookMode,
                onClick = { showFacebookSheet = true }
            )
            if (state.facebookMode == "reels") {
                Spacer(modifier = Modifier.height(8.dp))
                InfoBox(stringResource(R.string.info_facebook))
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onLaunchFacebookWrapper,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1877F2),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_launch_facebook_wrapper), fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // How It Works
            HowItWorksCard()
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showYoutubeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showYoutubeSheet = false },
                containerColor = surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = textMuted) }
            ) {
                ModeSelectionSheet(
                    title = stringResource(R.string.sheet_youtube_title),
                    currentMode = state.youtubeMode,
                    options = listOf(
                        "off" to stringResource(R.string.option_no_block),
                        "full" to stringResource(R.string.option_yt_full_block),
                        "shorts" to stringResource(R.string.option_block_shorts)
                    ),
                    onSelect = { 
                        viewModel.setYoutubeMode(it)
                        showYoutubeSheet = false
                    }
                )
            }
        }

        if (showFacebookSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFacebookSheet = false },
                containerColor = surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = textMuted) }
            ) {
                ModeSelectionSheet(
                    title = stringResource(R.string.sheet_facebook_title),
                    currentMode = state.facebookMode,
                    options = listOf(
                        "off" to stringResource(R.string.option_no_block),
                        "full" to stringResource(R.string.option_fb_full_block),
                        "reels" to stringResource(R.string.option_block_reels)
                    ),
                    onSelect = { 
                        viewModel.setFacebookMode(it)
                        showFacebookSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text)
    }
}

@Composable
fun ToggleRow(name: String, color: Color, isBlocked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = name, fontSize = 16.sp, color = text)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isBlocked) {
                Surface(
                    color = danger.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, danger.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = stringResource(R.string.badge_blocked),
                        color = danger,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Switch(
                checked = isBlocked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = surface,
                    checkedTrackColor = danger,
                    uncheckedThumbColor = textSecondary,
                    uncheckedTrackColor = surfaceLight
                )
            )
        }
    }
}

@Composable
fun DropdownCard(currentMode: String, onClick: () -> Unit) {
    val (label, badgeText, badgeColor) = when (currentMode) {
        "full" -> Triple(stringResource(R.string.label_full_block), stringResource(R.string.badge_blocked), danger)
        "shorts", "reels" -> Triple(stringResource(R.string.label_partial_block), stringResource(R.string.badge_partial), warning)
        else -> Triple(stringResource(R.string.option_no_block), stringResource(R.string.badge_open), success)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 16.sp, color = text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = badgeColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = textMuted)
            }
        }
    }
}

@Composable
fun InfoBox(message: String) {
    Surface(
        color = warning.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, warning.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = warning, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = message, color = warning, fontSize = 13.sp)
        }
    }
}

@Composable
fun HowItWorksCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = card),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.card_how_it_works), fontWeight = FontWeight.Bold, color = textSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            HowItWorksRow(Icons.Default.Close, danger, stringResource(R.string.row_how_full_block))
            Spacer(modifier = Modifier.height(8.dp))
            HowItWorksRow(Icons.Default.SubdirectoryArrowLeft, warning, stringResource(R.string.row_how_partial_block))
            Spacer(modifier = Modifier.height(8.dp))
            HowItWorksRow(Icons.Default.Check, success, stringResource(R.string.row_how_no_block))
        }
    }
}

@Composable
fun HowItWorksRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = desc, color = textMuted, fontSize = 13.sp)
    }
}

@Composable
fun ModeSelectionSheet(
    title: String,
    currentMode: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp, top = 16.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        
        options.forEach { (value, label) ->
            val badgeColor = when (value) {
                "full" -> danger
                "shorts", "reels" -> warning
                else -> success
            }
            val badgeText = when (value) {
                "full" -> stringResource(R.string.badge_blocked_upper)
                "shorts", "reels" -> stringResource(R.string.badge_partial_upper)
                else -> stringResource(R.string.badge_open_upper)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, fontSize = 16.sp, color = text)
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        color = badgeColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                RadioButton(
                    selected = currentMode == value,
                    onClick = { onSelect(value) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = accent,
                        unselectedColor = textMuted
                    )
                )
            }
        }
    }
}
