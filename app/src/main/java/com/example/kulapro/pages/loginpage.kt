package com.example.kulapro.pages

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.kulapro.Routes
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.AuthScaffold
import com.example.kulapro.ui.components.KulaPasswordField
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.SecondaryButton
import com.example.kulapro.ui.components.shakeOnError
import com.example.kulapro.util.Validators
import kotlinx.coroutines.launch

@Composable
fun LoginPage(
    navController: NavController,
    modifier: Modifier = Modifier,
    onSignedIn: () -> Unit = { navController.navigate(Routes.HOME) },
    appContext: android.content.Context = LocalContext.current.applicationContext,
    authRepository: AuthRepository = remember { AuthRepositoryFirebase(appContext) },
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    // Bumped on every failed submit so the shake replays even for the same error text.
    var errorNonce by remember { mutableStateOf<Int?>(null) }

    fun submit() {
        emailError = Validators.emailError(email)
        passwordError = Validators.signInPasswordError(password)
        if (emailError != null || passwordError != null) {
            errorNonce = (errorNonce ?: 0) + 1
            return
        }
        isSubmitting = true
        scope.launch {
            val result = authRepository.signIn(email.trim(), password)
            isSubmitting = false
            when (result) {
                is Result.Success -> onSignedIn()

                is Result.Failure -> {
                    errorNonce = (errorNonce ?: 0) + 1
                    snackbarHostState.showSnackbar(result.message)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            AuthScaffold(
                title = "Welcome back",
                subtitle = "Sign in to book your table.",
                onBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else {
                    null
                },
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

                PrimaryButton(
                    text = "Sign in",
                    loadingText = "Signing in",
                    loading = isSubmitting,
                    onClick = ::submit,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "or",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                GoogleSignInButton(
                    enabled = !isSubmitting,
                    onClick = {
                        val activity = context as? Activity ?: return@GoogleSignInButton
                        isSubmitting = true
                        scope.launch {
                            val result = authRepository.signInWithGoogle(activity)
                            isSubmitting = false
                            when (result) {
                                is Result.Success -> onSignedIn()

                                is Result.Failure ->
                                    snackbarHostState.showSnackbar(result.message)
                            }
                        }
                    },
                )

                TextButton(
                    onClick = { navController.navigate(Routes.FORGOT) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Forgot your password?") }

                SecondaryButton(
                    text = "Create an account",
                    onClick = { navController.navigate(Routes.REGISTER) },
                    enabled = !isSubmitting,
                )
            }
        }
    }
}
