package com.example.kulapro.pages


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth


@Composable
fun ProfilePage(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()

    // State for user input
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "") }
    var newEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf("") }
    var passwordVisibility by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // Function to update email
    fun updateEmail() {
        val user = auth.currentUser
        if (newEmail.isNotEmpty() && user != null) {
            isLoading = true
            user.updateEmail(newEmail).addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    feedbackMessage = "Email updated successfully!"
                    user.sendEmailVerification() // Optionally send a verification email
                } else {
                    feedbackMessage = "Failed to update email: ${task.exception?.message}"
                }
            }
        } else {
            feedbackMessage = "Please enter a new email address."
        }
    }

    // Function to change password
    fun changePassword() {
        val user = auth.currentUser
        if (password.isNotEmpty() && user != null) {
            isLoading = true
            user.updatePassword(password).addOnCompleteListener { task ->
                isLoading = false
                if (task.isSuccessful) {
                    feedbackMessage = "Password changed successfully!"
                    // Optionally, send email after password change
                    user.sendEmailVerification()
                } else {
                    feedbackMessage = "Failed to change password: ${task.exception?.message}"
                }
            }
        } else {
            feedbackMessage = "Please enter a new password."
        }
    }

    // Function to log out
    fun logOut() {
        auth.signOut()
        // Navigate back to the login screen after logout
        navController.navigate("login") {
            popUpTo("login") { inclusive = true } // Clear the back stack to avoid navigating back to the profile page
        }
    }

    // UI
    Column(modifier = modifier.padding(16.dp)) {
        Text("Update Profile",  style = MaterialTheme.typography.displayMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Email field
        OutlinedTextField(
            value = newEmail,
            onValueChange = { newEmail = it },
            label = { Text("New Email Address") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email),
            isError = feedbackMessage.contains("Failed")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New Password") },
            visualTransformation = if (passwordVisibility) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisibility = !passwordVisibility }) {
                    Icon(
                        imageVector = if (passwordVisibility) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Toggle password visibility"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            isError = feedbackMessage.contains("Failed")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Update email button
        Button(
            onClick = { updateEmail() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Change Email")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Change password button
        Button(
            onClick = { changePassword() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Change Password")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Loading indicator
//        if (isLoading) {
//            CircularProgressIndicator(modifier = Modifier.align(LineHeightStyle.Alignment.Center))
//        }

        // Feedback message
        if (feedbackMessage.isNotEmpty()) {
            Text(
                text = feedbackMessage,
                color = if (feedbackMessage.contains("successfully")) Color.Green else Color.Red,
                style = MaterialTheme.typography.displayMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logout button
        Button(
            onClick = { logOut() },
            modifier = Modifier.fillMaxWidth(),

        ) {
            Text("Log Out", color = Color.White)
        }
    }
}
