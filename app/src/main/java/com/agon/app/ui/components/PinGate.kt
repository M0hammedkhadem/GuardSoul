package com.agon.app.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.utils.SecurityUtils
import com.agon.app.ui.theme.*
import kotlinx.coroutines.launch
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
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val window = remember { (context as Activity).window }

    DisposableEffect(Unit) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

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
        if (pin.length < 4) {
            pin += digit
            error = false
            if (pin.length == 4) {
                val hashed = SecurityUtils.hashPin(pin)
                if (hashed == storedHash) {
                    onPinVerified()
                } else {
                    error = true
                    pin = ""
                    startShake()
                }
            }
        }
    }

    val onDelete: () -> Unit = {
        if (pin.isNotEmpty()) {
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
            stringResource(R.string.pin_gate_desc),
            fontSize = 14.sp,
            color = textSecondary
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

        if (error) {
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
            onDelete = onDelete
        )
    }
}

@Composable
private fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit
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
                    KeyButton(digit.toString(), onClick = { onDigit(digit) })
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(72.dp))
            KeyButton("0", onClick = { onDigit('0') })
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(surfaceLight)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = stringResource(R.string.contentdesc_remove),
                    tint = text
                )
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(surfaceLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = text
        )
    }
}
