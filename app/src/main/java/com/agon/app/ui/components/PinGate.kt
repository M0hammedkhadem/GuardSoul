package com.agon.app.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.utils.SecurityUtils
import com.agon.app.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

@Composable
fun PinGate(
    hasPinSet: Boolean = false,
    storedHash: String? = null,
    content: @Composable () -> Unit
) {
    var isUnlocked by remember { mutableStateOf(!hasPinSet) }

    LaunchedEffect(hasPinSet) {
        if (!hasPinSet) isUnlocked = true
    }

    if (isUnlocked || !hasPinSet) {
        content()
    } else {
        PinEntryScreen(
            storedHash = storedHash ?: "",
            onPinVerified = { isUnlocked = true },
        )
    }
}

@Composable
fun PinEntryScreen(
    storedHash: String,
    onPinVerified: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var isLockedByDelay by remember { mutableStateOf(false) }
    var remainingDelaySeconds by remember { mutableIntStateOf(0) }
    
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val window = remember { (context as Activity).window }

    DisposableEffect(Unit) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    // Issue #195: Use a single job to manage backoff to avoid race conditions
    var backoffJob by remember { mutableStateOf<Job?>(null) }

    val startShake: () -> Unit = {
        scope.launch {
            shakeOffset.snapTo(0f)
            shakeOffset.animateTo(10f, tween(50))
            shakeOffset.animateTo(-10f, tween(50))
            shakeOffset.animateTo(6f, tween(50))
            shakeOffset.animateTo(-6f, tween(50))
            shakeOffset.animateTo(0f, tween(50))
        }
    }

    val onDigit: (Char) -> Unit = { digit ->
        if (pin.length < 4 && !isLockedByDelay) {
            pin += digit
            error = false
            if (pin.length == 4) {
                if (SecurityUtils.verifyPinAgainstHash(pin, storedHash)) {
                    onPinVerified()
                } else {
                    error = true
                    pin = ""
                    failedAttempts++
                    startShake()
                    
                    // Issue #194: Implement Hard Cap (Max 10 attempts before long lockout)
                    if (failedAttempts >= 3) {
                        backoffJob?.cancel()
                        backoffJob = scope.launch {
                            isLockedByDelay = true
                            val delaySec = if (failedAttempts >= 10) 300 // 5 minutes hard lock
                            else (2.0.pow((failedAttempts - 2).toDouble())).toInt().coerceAtMost(30)
                            
                            remainingDelaySeconds = delaySec
                            while (remainingDelaySeconds > 0) {
                                delay(1000)
                                remainingDelaySeconds--
                            }
                            isLockedByDelay = false
                        }
                    }
                }
            }
        }
    }

    val onDelete: () -> Unit = {
        if (pin.isNotEmpty() && !isLockedByDelay) {
            pin = pin.dropLast(1)
            error = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.pin_gate_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = text
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isLockedByDelay) {
                if (failedAttempts >= 10) "تم القفل بشكل دائم مؤقتاً. حاول بعد 5 دقائق"
                else stringResource(R.string.pin_locked_retry_in, remainingDelaySeconds)
            } else stringResource(R.string.pin_gate_desc),
            fontSize = 14.sp,
            color = if (isLockedByDelay) danger else textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
        ) {
            for (i in 0 until 4) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error -> danger
                                i < pin.length -> primary
                                else -> surfaceLight
                            }
                        )
                )
            }
        }

        if (error && !isLockedByDelay) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.pin_wrong),
                color = danger,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(40.dp))

        NumericKeypad(
            onDigit = onDigit,
            onDelete = onDelete,
            enabled = !isLockedByDelay
        )
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { digit ->
                    KeyButton(digit.toString(), onClick = { onDigit(digit) }, enabled = enabled)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(72.dp))
            KeyButton("0", onClick = { onDigit('0') }, enabled = enabled)
            IconButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (enabled) surfaceLight else surfaceLight.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.contentdesc_remove),
                    tint = if (enabled) text else textMuted
                )
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (enabled) surfaceLight else surfaceLight.copy(alpha = 0.5f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) text else textMuted
        )
    }
}
