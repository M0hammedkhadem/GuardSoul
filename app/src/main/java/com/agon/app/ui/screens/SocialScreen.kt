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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.data.GuardianState
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.GuardianViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(viewModel: GuardianViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showYoutubeSheet by remember { mutableStateOf(false) }
    var showFacebookSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Social Media", fontWeight = FontWeight.Bold) },
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
            SectionHeader(icon = Icons.Default.Block, title = "Direct Block", color = danger)
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder)
            ) {
                Column {
                    ToggleRow(
                        name = "Instagram",
                        color = Color(0xFFE1306C),
                        isBlocked = state.instagramBlocked,
                        onToggle = { viewModel.toggleInstagram() }
                    )
                    HorizontalDivider(color = cardBorder)
                    ToggleRow(
                        name = "Snapchat",
                        color = Color(0xFFFFFC00),
                        isBlocked = state.snapchatBlocked,
                        onToggle = { viewModel.toggleSnapchat() }
                    )
                    HorizontalDivider(color = cardBorder)
                    ToggleRow(
                        name = "X (Twitter)",
                        color = Color(0xFF1DA1F2),
                        isBlocked = state.twitterBlocked,
                        onToggle = { viewModel.toggleTwitter() }
                    )
                    HorizontalDivider(color = cardBorder)
                    ToggleRow(
                        name = "TikTok",
                        color = Color(0xFF69C9D0),
                        isBlocked = state.tiktokBlocked,
                        onToggle = { viewModel.toggleTiktok() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // YouTube Section
            SectionHeader(icon = Icons.Default.PlayArrow, title = "YouTube", color = Color(0xFFFF0000))
            Spacer(modifier = Modifier.height(16.dp))
            DropdownCard(
                currentMode = state.youtubeMode,
                onClick = { showYoutubeSheet = true }
            )
            if (state.youtubeMode == "shorts") {
                Spacer(modifier = Modifier.height(8.dp))
                InfoBox("When a user opens a Short or enters the Shorts section, they will be silently redirected to the YouTube home page without notification.")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Facebook Section
            SectionHeader(icon = Icons.Default.Facebook, title = "Facebook", color = Color(0xFF1877F2))
            Spacer(modifier = Modifier.height(16.dp))
            DropdownCard(
                currentMode = state.facebookMode,
                onClick = { showFacebookSheet = true }
            )
            if (state.facebookMode == "reels") {
                Spacer(modifier = Modifier.height(8.dp))
                InfoBox("Reels in the main feed are allowed. Blocking triggers only when entering the full-screen Reels section or tapping a Reel to open it.")
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
                    title = "YouTube Restriction",
                    currentMode = state.youtubeMode,
                    options = listOf(
                        "off" to "No Block",
                        "full" to "YT Full Block",
                        "shorts" to "Block Shorts Only"
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
                    title = "Facebook Restriction",
                    currentMode = state.facebookMode,
                    options = listOf(
                        "off" to "No Block",
                        "full" to "FB Full Block",
                        "reels" to "Block Reels Only"
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
                        text = "BLOCKED",
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
        "full" -> Triple("Full Block", "BLOCKED", danger)
        "shorts", "reels" -> Triple("Partial Block", "PARTIAL", warning)
        else -> Triple("No Block", "OPEN", success)
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
            Text("How it works", fontWeight = FontWeight.Bold, color = textSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            HowItWorksRow(Icons.Default.Close, danger, "Full Block — Prevents opening the app entirely")
            Spacer(modifier = Modifier.height(8.dp))
            HowItWorksRow(Icons.Default.SubdirectoryArrowLeft, warning, "Partial Block — Silently redirects short-form content")
            Spacer(modifier = Modifier.height(8.dp))
            HowItWorksRow(Icons.Default.Check, success, "No Block — App is fully accessible")
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
                "full" -> "BLOCKED"
                "shorts", "reels" -> "PARTIAL"
                else -> "OPEN"
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
