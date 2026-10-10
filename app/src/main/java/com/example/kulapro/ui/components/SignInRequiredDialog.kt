package com.example.kulapro.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Explains why an action needs an account, and offers the way there.
 *
 * Shown at the moment the user reaches for something that needs signing in, rather than
 * gating the whole app behind a login wall they meet before seeing anything worth signing
 * in for.
 */
@Composable
fun SignInRequiredDialog(
    action: String,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text("Sign in to $action") },
        text = {
            Text(
                "You can browse restaurants without an account, but $action needs one " +
                    "so we know the booking is yours.",
            )
        },
        confirmButton = {
            TextButton(onClick = onSignIn) { Text("Sign in") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Keep browsing") }
        },
    )
}
