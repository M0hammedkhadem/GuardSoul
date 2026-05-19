package com.agon.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.*
import kotlinx.coroutines.delay

private fun String.sha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(this.toByteArray()).joinToString("") { "%02x".format(it) }
}

@Composable
fun PinSetupScreen(
    existingPin: String? = null,
    onPinSet: (String) -> Unit,
    onSkip: (() -> Unit)? = null
) {
    val isChanging = existingPin != null
    var step by remember { mutableIntStateOf(0) } // 0=enter, 1=confirm, 2=done
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isChanging) "Change PIN" else "Set PIN Code",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (step) {
                0 -> "Enter a 4-6 digit PIN to protect your settings"
                1 -> "Confirm your PIN"
                else -> "PIN set successfully!"
            },
            fontSize = 14.sp,
            color = textSecondary
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (step) {
            0 -> {
                PinInputField(
                    pin = pin,
                    onPinChange = { if (it.length <= 6) pin = it },
                    error = error
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (pin.length < 4) {
                            error = "PIN must be at least 4 digits"
                        } else {
                            error = null
                            step = 1
                        }
                    },
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Continue") }
                if (onSkip != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onSkip) {
                        Text("Skip", color = textMuted)
                    }
                }
            }
            1 -> {
                PinInputField(
                    pin = confirmPin,
                    onPinChange = { if (it.length <= 6) confirmPin = it },
                    error = error
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (confirmPin != pin) {
                            error = "PINs don't match"
                            confirmPin = ""
                        } else {
                            error = null
                            val hashed = pin.sha256()
                            onPinSet(hashed)
                            step = 2
                        }
                    },
                    enabled = confirmPin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Confirm") }
            }
            2 -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = success,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LaunchedEffect(Unit) { delay(1500); onPinSet(pin.sha256()) }
            }
        }
    }
}

@Composable
private fun PinInputField(
    pin: String,
    onPinChange: (String) -> Unit,
    error: String?
) {
    OutlinedTextField(
        value = pin,
        onValueChange = onPinChange,
        label = { Text("PIN") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 0 until 6) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (i < pin.length) primary else surfaceLight)
            )
        }
    }
}

@Composable
fun PinGateScreen(
    onUnlock: () -> Unit,
    onLock: () -> Unit = {},
    storedPinHash: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

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
        Spacer(modifier = Modifier.height(16.dp))
        Text("Guardian Locked", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text)
        Text("Enter PIN to access settings", fontSize = 14.sp, color = textSecondary)
        Spacer(modifier = Modifier.height(32.dp))
        PinInputField(
            pin = pin,
            onPinChange = { if (it.length <= 6) { pin = it; error = null } },
            error = error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val hashed = digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
                if (storedPinHash == hashed) {
                    onUnlock()
                } else {
                    error = "Wrong PIN"
                    pin = ""
                }
            },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Unlock") }
    }
}
