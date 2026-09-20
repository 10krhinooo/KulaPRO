package com.example.kulapro.pages

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.navigation.NavController
import com.example.kulapro.Routes
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.AuthScaffold
import com.example.kulapro.ui.components.KulaPasswordField
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.PasswordStrengthMeter
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.ui.components.shakeOnError
import com.example.kulapro.util.Validators
import kotlinx.coroutines.launch

@Composable
fun RegisterPage(
    navController: NavController,
    modifier: Modifier = Modifier,
    appContext: android.content.Context = LocalContext.current.applicationContext,
    authRepository: AuthRepository = remember { AuthRepositoryFirebase(appContext) },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorNonce by remember { mutableStateOf<Int?>(null) }

    fun submit() {
        emailError = Validators.emailError(email)
        passwordError = Validators.passwordError(password)
        confirmError = if (confirmPassword != password) "Passwords do not match" else null
        if (emailError != null || passwordError != null || confirmError != null) {
            errorNonce = (errorNonce ?: 0) + 1
            return
        }
        isSubmitting = true
        scope.launch {
            val result = authRepository.register(email.trim(), password)
            isSubmitting = false
            when (result) {
                is Result.Success -> navController.navigate(Routes.HOME) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }

                is Result.Failure -> {
                    errorNonce = (errorNonce ?: 0) + 1
                    messages.showError(result.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MessageHost(messages) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AuthScaffold(
                title = "Create your account",
                subtitle = "It takes about thirty seconds.",
                onBack = { navController.popBackStack() },
            ) {
                KulaTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = null
                    },
                    label = "Email",
                    leadingIcon = Icons.Rounded.Email,
                    error = emailError,
                    keyboardType = KeyboardType.Email,
                    enabled = !isSubmitting,
                    modifier = Modifier.shakeOnError(errorNonce?.takeIf { emailError != null }),
                )

                KulaPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = "Password",
                    error = passwordError,
                    enabled = !isSubmitting,
                    modifier = Modifier.shakeOnError(errorNonce?.takeIf { passwordError != null }),
                )

                // Live feedback as they type, rather than only telling them what is wrong
                // after they have already pressed the button.
                AnimatedVisibility(visible = password.isNotEmpty()) {
                    PasswordStrengthMeter(password = password)
                }

                KulaPasswordField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmError = null
                    },
                    label = "Confirm password",
                    error = confirmError,
                    enabled = !isSubmitting,
                    modifier = Modifier.shakeOnError(errorNonce?.takeIf { confirmError != null }),
                )

                PrimaryButton(
                    text = "Create account",
                    loadingText = "Creating account",
                    loading = isSubmitting,
                    onClick = ::submit,
                )

                GoogleSignInButton(
                    enabled = !isSubmitting,
                    label = "Sign up with Google",
                    onClick = {
                        val activity = context as? Activity ?: return@GoogleSignInButton
                        isSubmitting = true
                        scope.launch {
                            val result = authRepository.signInWithGoogle(activity)
                            isSubmitting = false
                            when (result) {
                                is Result.Success -> navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.LOGIN) { inclusive = true }
                                }

                                is Result.Failure ->
                                    messages.showError(result.message)
                            }
                        }
                    },
                )

                TextButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Already have an account? Sign in",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
