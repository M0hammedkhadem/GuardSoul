package com.agon.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.R
import com.agon.app.blocking.SignatureLearner
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.LearnerViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Debug + control screen for the **auto-learning engine**.
 *
 * Lists every package the learner has observed, with:
 *  - hit count + first/last seen timestamps,
 *  - the specific view-ids and class names it captured,
 *  - the current status (promoted / candidate),
 *  - per-row "Forget" action,
 *  - global "Forget all" action with confirmation dialog,
 *  - master "Enable auto-learning" toggle at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnerScreen(
    onBack: () -> Unit,
    vm: LearnerViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val enabled by vm.enabledFlow.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.learner_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.learner_subtitle),
                color = textSecondary,
                fontSize = 14.sp,
            )

            // Master toggle ----------------------------------------------------
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.learner_enabled_label),
                            fontWeight = FontWeight.SemiBold,
                            color = text,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.learner_enabled_summary),
                            color = textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = vm::setEnabled,
                    )
                }
            }

            // Entries ----------------------------------------------------------
            if (uiState.entries.isEmpty()) {
                EmptyState()
            } else {
                uiState.entries.forEach { sig ->
                    LearnerEntryCard(
                        sig = sig,
                        onForget = { vm.forget(sig.packageName) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.learner_forget_all))
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.learner_clear_confirm_title)) },
            text = { Text(stringResource(R.string.learner_clear_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    vm.forgetAll()
                }) { Text(stringResource(R.string.learner_clear_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.learner_clear_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun EmptyState() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.learner_empty),
                color = textSecondary,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun LearnerEntryCard(
    sig: SignatureLearner.LearnedSignature,
    onForget: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val statusLabel = if (sig.promoted) {
        stringResource(R.string.learner_promoted)
    } else {
        stringResource(R.string.learner_candidate, sig.hitCount, 5)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = card),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sig.packageName,
                    fontWeight = FontWeight.SemiBold,
                    color = text,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                )
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel, fontSize = 11.sp) },
                    colors = if (sig.promoted) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = success.copy(alpha = 0.18f),
                            labelColor = success,
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.learner_last_seen, dateFormat.format(Date(sig.lastSeen))),
                color = textSecondary, fontSize = 12.sp,
            )
            Text(
                text = stringResource(R.string.learner_first_seen, dateFormat.format(Date(sig.firstSeen))),
                color = textSecondary, fontSize = 12.sp,
            )

            // Tokens
            if (sig.viewIds.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.learner_view_ids),
                    color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
                TokenRow(sig.viewIds)
            }
            if (sig.classNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.learner_class_names),
                    color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
                TokenRow(sig.classNames)
            }

            // Forget
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onForget) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.learner_forget))
                }
            }
        }
    }
}

@Composable
private fun TokenRow(tokens: List<String>) {
    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tokens.take(6).forEach { token ->
            Text(
                text = "• $token",
                color = text,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        if (tokens.size > 6) {
            Text(
                text = "  … +${tokens.size - 6} more",
                color = textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
