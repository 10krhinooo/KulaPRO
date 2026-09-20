package com.example.kulapro.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.AuthScaffold
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.SecondaryButton
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.ui.components.shakeOnError
import com.example.kulapro.util.Validators
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    appContext: android.content.Context = LocalContext.current.applicationContext,
    authRepository: AuthRepository = remember { AuthRepositoryFirebase(appContext) },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var sentTo by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorNonce by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MessageHost(messages) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AuthScaffold(
                title = "Reset your password",
                subtitle = "We will email you a link to set a new one.",
                onBack = { navController.popBackStack() },
            ) {
                KulaTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                        sentTo = null
                    },
                    label = "Email",
                    leadingIcon = Icons.Rounded.Email,
                    error = emailError,
                    keyboardType = KeyboardType.Email,
                    enabled = !isSubmitting,
                    modifier = Modifier.shakeOnError(errorNonce?.takeIf { emailError != null }),
                )

                PrimaryButton(
                    text = "Send reset link",
                    loadingText = "Sending",
                    loading = isSubmitting,
                    onClick = {
                        emailError = Validators.emailError(email)
                        if (emailError != null) {
                            errorNonce = (errorNonce ?: 0) + 1
                            return@PrimaryButton
                        }
                        isSubmitting = true
                        scope.launch {
                            val target = email.trim()
                            val result = authRepository.sendPasswordReset(target)
                            isSubmitting = false
                            when (result) {
                                // Stay put so the confirmation is readable. The first version
                                // set a message and navigated away in the same breath.
                                is Result.Success -> sentTo = target
                                is Result.Failure -> {
                                    errorNonce = (errorNonce ?: 0) + 1
                                    messages.showError(result.message)
                                }
                            }
                        }
                    },
                )

                AnimatedVisibility(
                    visible = sentTo != null,
                    enter = fadeIn() + expandVertically(),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Link sent to ${sentTo.orEmpty()}. " +
                                    "Check your inbox, and your spam folder.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                SecondaryButton(
                    text = "Back to sign in",
                    onClick = { navController.popBackStack() },
                )
            }
        }
    }
}
