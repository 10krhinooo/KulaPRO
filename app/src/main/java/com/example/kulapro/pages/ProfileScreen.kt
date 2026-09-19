package com.example.kulapro.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.kulapro.Routes
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.Result
import com.example.kulapro.util.Validators
import kotlinx.coroutines.launch

/**
 * Profile and credential management.
 *
 * Firebase refuses credential changes without a recent sign-in, so both actions here take the
 * current password and re-authenticate first. The first version omitted that step, which is
 * why its "Change password" button reliably failed once a session was more than a few
 * minutes old.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    navController: NavController,
    modifier: Modifier = Modifier,
    authRepository: AuthRepository = remember { AuthRepositoryFirebase() },
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPassword by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newEmailError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = authRepository.currentUserEmail ?: "Not signed in",
                style = MaterialTheme.typography.titleMedium,
            )

            HorizontalDivider()

            Text("Change your details", style = MaterialTheme.typography.titleSmall)
            Text(
                "For security, confirm your current password before making a change.",
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text("Current password") },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.Visibility
                            } else {
                                Icons.Filled.VisibilityOff
                            },
                            contentDescription = if (passwordVisible) {
                                "Hide password"
                            } else {
                                "Show password"
                            },
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = newEmail,
                onValueChange = {
                    newEmail = it
                    newEmailError = null
                },
                label = { Text("New email address") },
                isError = newEmailError != null,
                supportingText = newEmailError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    newEmailError = Validators.emailError(newEmail)
                    if (newEmailError != null) return@Button
                    if (currentPassword.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Enter your current password first")
                        }
                        return@Button
                    }
                    isBusy = true
                    scope.launch {
                        val result = authRepository.updateEmail(newEmail.trim(), currentPassword)
                        isBusy = false
                        snackbarHostState.showSnackbar(
                            when (result) {
                                // verifyBeforeUpdateEmail only takes effect once the new
                                // address is confirmed, so say that rather than claiming
                                // the change already happened.
                                is Result.Success ->
                                    "Check $newEmail to confirm the change"

                                is Result.Failure -> result.message
                            },
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Change email") }

            OutlinedTextField(
                value = newPassword,
                onValueChange = {
                    newPassword = it
                    newPasswordError = null
                },
                label = { Text("New password") },
                isError = newPasswordError != null,
                supportingText = newPasswordError?.let { { Text(it) } },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    newPasswordError = Validators.passwordError(newPassword)
                    if (newPasswordError != null) return@Button
                    if (currentPassword.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Enter your current password first")
                        }
                        return@Button
                    }
                    isBusy = true
                    scope.launch {
                        val result = authRepository.updatePassword(newPassword, currentPassword)
                        isBusy = false
                        snackbarHostState.showSnackbar(
                            when (result) {
                                is Result.Success -> "Password changed"
                                is Result.Failure -> result.message
                            },
                        )
                        if (result is Result.Success) {
                            newPassword = ""
                            currentPassword = ""
                        }
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Change password") }

            HorizontalDivider()

            OutlinedButton(
                onClick = {
                    authRepository.signOut()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
        }
    }
}
