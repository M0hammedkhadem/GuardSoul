package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.data.GuardianState
import com.agon.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: GuardianState,
    onUpdateName: (String) -> Unit,
    onBack: () -> Unit
) {
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember(state.profileName) { mutableStateOf(state.profileName) }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.profileName.take(2).ifEmpty { "G" }.uppercase(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }

            Spacer(Modifier.height(16.dp))

            if (editingName) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        onUpdateName(nameInput.ifBlank { "User" })
                        editingName = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            } else {
                Text(
                    state.profileName.ifEmpty { "Guardian User" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = text
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { editingName = true; nameInput = state.profileName }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit name")
                }
            }

            Spacer(Modifier.height(32.dp))

            // Stats summary
            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Account Summary", fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.height(12.dp))
                    ProfileRow(Icons.Default.Shield, "Shield Active", if (state.isShieldActive) "Yes" else "No", if (state.isShieldActive) shieldGreen else textMuted)
                    ProfileRow(Icons.Default.Block, "Total Blocks", state.blocksCount.toString(), accent)
                    ProfileRow(Icons.Default.Science, "Trial Mode", if (state.isTrialModeActive) if (state.isTrialExpired) "Expired" else "Active" else "Off", if (state.isTrialModeActive && !state.isTrialExpired) warning else textMuted)
                    ProfileRow(Icons.Default.Lock, "PIN Protection", if (state.pinCode != null) "Enabled" else "Disabled", if (state.pinCode != null) success else textMuted)
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("App Info", fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.height(12.dp))
                    ProfileRow(Icons.Default.Info, "Version", "1.0.0", textMuted)
                    ProfileRow(Icons.Default.Schedule, "Installed", state.installTimestamp?.let {
                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                    } ?: "Unknown", textMuted)
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
