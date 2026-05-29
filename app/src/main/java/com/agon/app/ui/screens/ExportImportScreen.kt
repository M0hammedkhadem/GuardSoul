package com.agon.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.ExportImportViewModel

@Composable
fun ExportImportScreen(
    onBack: () -> Unit,
    vm: ExportImportViewModel? = null
) {
    val context = LocalContext.current
    val effectiveVm = vm ?: viewModel<ExportImportViewModel>()

    val statusMessage by effectiveVm.statusMessage.collectAsStateWithLifecycle()
    val isError by effectiveVm.isError.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            effectiveVm.exportData(context, uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            effectiveVm.importData(context, uri)
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            kotlinx.coroutines.delay(3000)
            effectiveVm.clearStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(16.dp)
    ) {
        @OptIn(ExperimentalMaterial3Api::class)
        TopAppBar(
            title = { Text(stringResource(R.string.export_title), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.contentdesc_back)) } },
            colors = @OptIn(ExperimentalMaterial3Api::class) TopAppBarDefaults.topAppBarColors(containerColor = background)
        )

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.FileUpload, null, tint = primary, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.export_card_title), fontWeight = FontWeight.Bold, color = text)
                    Text(stringResource(R.string.export_card_desc), fontSize = 13.sp, color = textSecondary)
                }
                FilledTonalButton(
                    onClick = { exportLauncher.launch(context.getString(R.string.export_default_filename)) },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.export_btn)) }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.FileDownload, null, tint = accent, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_card_title), fontWeight = FontWeight.Bold, color = text)
                    Text(stringResource(R.string.import_card_desc), fontSize = 13.sp, color = textSecondary)
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch(arrayOf("text/plain", "*/*")) },
                    shape = RoundedCornerShape(12.dp)
                ) { Text(stringResource(R.string.import_btn)) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = textMuted, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.export_format_title), fontWeight = FontWeight.Bold, color = text)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.export_format_websites), fontSize = 12.sp, color = textSecondary)
                Text(stringResource(R.string.export_example_website), fontSize = 12.sp, color = textMuted)
                Text(stringResource(R.string.export_example_website2), fontSize = 12.sp, color = textMuted)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.export_format_keywords), fontSize = 12.sp, color = textSecondary)
                Text(stringResource(R.string.export_example_keyword), fontSize = 12.sp, color = textMuted)
                Text(stringResource(R.string.export_example_keyword2), fontSize = 12.sp, color = textMuted)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.export_format_apps), fontSize = 12.sp, color = textSecondary)
                Text(stringResource(R.string.export_example_app), fontSize = 12.sp, color = textMuted)
            }
        }

        if (statusMessage != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isError) shieldRed.copy(alpha = 0.1f) else success.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                        null,
                        tint = if (isError) shieldRed else success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(statusMessage!!, color = text, fontSize = 14.sp)
                }
            }
        }
    }
}
