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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    onBack: () -> Unit
) {
    val name by vm.profileName.collectAsStateWithLifecycle()
    val shieldActive by vm.shieldActive.collectAsStateWithLifecycle()
    val totalBlocks by vm.totalBlocks.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val trialMode by vm.trialMode.collectAsStateWithLifecycle()
    var editingName by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf(name) }

    Scaffold(
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.contentdesc_back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("G", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = primary)
            }
            Spacer(Modifier.height(16.dp))

            if (editingName) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.saveName(nameInput); editingName = false },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.profile_save)) }
            } else {
                Text(name.ifBlank { stringResource(R.string.profile_guardian_user) }, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text)
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { editingName = true; nameInput = name }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_edit_name))
                }
            }

            Spacer(Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = card),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.profile_account_summary), fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.height(12.dp))
                    ProfileRow(Icons.Default.Shield, stringResource(R.string.profile_shield_active), if (shieldActive) stringResource(R.string.profile_yes) else stringResource(R.string.profile_no), if (shieldActive) success else textMuted)
                    ProfileRow(Icons.Default.Block, stringResource(R.string.profile_total_blocks), totalBlocks.toString(), accent)
                    ProfileRow(Icons.Default.Science, stringResource(R.string.profile_trial_mode), if (trialMode) stringResource(R.string.profile_enabled) else stringResource(R.string.profile_disabled), textMuted)
                    ProfileRow(Icons.Default.Lock, stringResource(R.string.profile_pin_protection), if (hasPin) stringResource(R.string.profile_enabled) else stringResource(R.string.profile_disabled), if (hasPin) success else textMuted)
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
                    Text(stringResource(R.string.profile_app_info), fontWeight = FontWeight.Bold, color = text)
                    Spacer(Modifier.height(12.dp))
                    ProfileRow(Icons.Default.Info, stringResource(R.string.profile_version), stringResource(R.string.profile_version_value), textMuted)
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
