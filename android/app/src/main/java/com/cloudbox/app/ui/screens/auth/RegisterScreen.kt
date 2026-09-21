package com.cloudbox.app.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudbox.app.ui.components.BackRow
import com.cloudbox.app.ui.components.CloudTextField
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.PillButton
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    val uiState by viewModel.uiState.collectAsState()

    val passwordStrength = calculatePasswordStrength(password)
    val strengthColor = when (passwordStrength) {
        0 -> Color.LightGray
        1 -> Color(0xFFE53935)
        2 -> Color(0xFFFBC02D)
        else -> Color(0xFF43A047)
    }

    LaunchedEffect(uiState.registerSuccess) {
        if (uiState.registerSuccess) {
            onRegisterSuccess()
        }
    }

    CloudboxScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BackRow("Create Account", onNavigateBack)
            Spacer(Modifier.height(8.dp))
            Text("Join Cloudbox", color = CloudNavy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Free cloud storage, backed by Telegram Drive.", color = CloudMuted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CloudCard)
            ) {
                Column(Modifier.padding(20.dp)) {
                    CloudTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email",
                        leadingIcon = Icons.Default.Email,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(Modifier.height(14.dp))
                    CloudTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "Display Name",
                        leadingIcon = Icons.Default.Person
                    )
                    Spacer(Modifier.height(14.dp))
                    CloudTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password",
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailing = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = CloudMuted
                                )
                            }
                        }
                    )
                    LinearProgressIndicator(
                        progress = { passwordStrength / 3f },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = strengthColor,
                        trackColor = CloudCard
                    )
                    Spacer(Modifier.height(14.dp))
                    CloudTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = "Confirm Password",
                        leadingIcon = Icons.Default.Lock,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailing = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = CloudMuted
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(20.dp))

                    if (validationMessage != null || uiState.error != null) {
                        Text(
                            text = validationMessage ?: uiState.error ?: "",
                            color = Color(0xFFE53935),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    PillButton(
                        text = "Sign Up",
                        loading = uiState.isLoading,
                        onClick = {
                            validationMessage = when {
                                email.isBlank() -> "Enter your email address."
                                displayName.trim().length < 2 -> "Display name must be at least 2 characters."
                                password.length < 8 -> "Password must be at least 8 characters."
                                password != confirmPassword -> "Passwords do not match."
                                else -> null
                            }
                            if (validationMessage == null) {
                                viewModel.registerThenLogin(email.trim(), displayName.trim(), password)
                            }
                        }
                    )
                }
            }
        }
    }
}

fun calculatePasswordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    var strength = 0
    if (password.length >= 8) strength++
    if (password.any { it.isDigit() }) strength++
    if (password.any { it.isUpperCase() } || password.any { !it.isLetterOrDigit() }) strength++
    return strength
}