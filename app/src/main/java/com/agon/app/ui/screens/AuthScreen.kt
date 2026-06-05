package com.agon.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    mode: AuthMode,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
    onSignIn: suspend (String, String) -> Result<Unit>,
    onSignUp: suspend (String, String, String?) -> Result<Unit>,
    onSignInWithGoogle: suspend () -> Result<Unit>,
    onContinueAnonymously: suspend () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (mode == AuthMode.SignIn) R.string.auth_title_signin
                            else R.string.auth_title_signup
                        ),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text
                )
            )
        },
        containerColor = background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(primary.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (mode == AuthMode.SignIn) R.string.auth_subtitle_signin
                    else R.string.auth_subtitle_signup
                ),
                color = textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim() },
                label = { Text(stringResource(R.string.auth_email_label)) },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = cardBorder,
                    cursorColor = primary
                )
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password_label)) },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primary,
                    unfocusedBorderColor = cardBorder,
                    cursorColor = primary
                )
            )

            if (mode == AuthMode.SignUp) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.auth_display_name_label)) },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = primary
                    )
                )
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    errorMessage = null
                    isLoading = true
                    kotlinx.coroutines.GlobalScope.launch(
                        kotlinx.coroutines.Dispatchers.Main
                    ) {
                        val result = if (mode == AuthMode.SignIn) {
                            onSignIn(email, password)
                        } else {
                            onSignUp(email, password, displayName.ifBlank { null })
                        }
                        isLoading = false
                        result.onSuccess { onAuthSuccess() }
                            .onFailure { errorMessage = it.message ?: "Authentication failed" }
                    }
                },
                enabled = !isLoading && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = background,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (mode == AuthMode.SignIn) R.string.auth_btn_signin
                            else R.string.auth_btn_signup
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = cardBorder)
                Text(
                    text = stringResource(R.string.auth_or),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = textMuted,
                    fontSize = 12.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = cardBorder)
            }
            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    isLoading = true
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                        onSignInWithGoogle()
                            .onSuccess { onAuthSuccess() }
                            .onFailure { errorMessage = it.message ?: "Google sign-in failed" }
                        isLoading = false
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.auth_btn_google), fontSize = 14.sp, color = text)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                        onContinueAnonymously()
                        onAuthSuccess()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.auth_btn_continue_anonymous), color = textSecondary)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
