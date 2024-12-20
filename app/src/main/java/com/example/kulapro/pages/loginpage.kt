package com.example.kulapro.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AuthenticationManager {
    private val auth = Firebase.auth

    fun createAccountWithEmail(email: String, password: String): Flow<AuthResponse> = callbackFlow {
        if (email.isEmpty() || password.isEmpty()) {
            trySend(AuthResponse.Error("Please fill all fields"))
            return@callbackFlow
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    trySend(AuthResponse.Success)
                } else {
                    trySend(AuthResponse.Error(message = task.exception?.message ?: "Something went wrong"))
                }
            }
        awaitClose()
    }

    fun LoginWithEmail(email: String, password: String): Flow<AuthResponse> = callbackFlow {
        if (email.isEmpty() || password.isEmpty()) {
            trySend(AuthResponse.Error("Please fill all fields"))
            return@callbackFlow
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    trySend(AuthResponse.Success)
                } else {
                    trySend(AuthResponse.Error(message = task.exception?.message ?: "Something went wrong"))
                }
            }
        awaitClose()
    }
}


interface AuthResponse  {
    data  object Success: AuthResponse
    data class Error (val message: String): AuthResponse

}




@Composable
fun LoginPage (modifier: Modifier = Modifier,navController: NavController) {
    var email by remember {
        mutableStateOf("")
    }
    var password by remember {
        mutableStateOf("")
    }

    val authenticationManager = remember {
        AuthenticationManager()
    }
    val coroutineScope = rememberCoroutineScope()
    var feedbackMessage by remember { mutableStateOf("") }
    var showSnackbar by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "KulaPro",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold

    )
        Spacer(modifier = Modifier.height(20.dp) )

        Text(text = "Login",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,


        )



        Spacer(modifier = Modifier.height(20.dp) )


        OutlinedTextField(
            value = email,
            onValueChange = { newValue ->
                email = newValue
                formError = false // Clear error when user starts typing
            },
            placeholder = { Text(text = "E-mail") },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Email, contentDescription = null)
            },
            isError = formError && email.isEmpty(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (formError && email.isEmpty()) {
            Text(
                text = "Email is required",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { newValue ->
                password = newValue
                formError = false // Clear error when user starts typing
            },
            placeholder = { Text(text = "Password") },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
            },

            visualTransformation = PasswordVisualTransformation(),
            isError = formError && password.isEmpty(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()

        )
        if (formError && password.isEmpty()) {
            Text(
                text = "Password is required",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(23.dp))

        Button(
            onClick = {
                if (email.isEmpty() || password.isEmpty()) {
                    formError = true
                    feedbackMessage = "Please fill in all fields"
                    showSnackbar = true
                } else {
                    authenticationManager.LoginWithEmail(email, password)
                        .onEach { response ->
                            when (response) {
                                is AuthResponse.Success -> {
                                    feedbackMessage = "Login successful"
                                    navController.navigate("home")
                                }
                                is AuthResponse.Error -> {
                                    feedbackMessage = response.message
                                }
                            }
                            showSnackbar = true
                        }.launchIn(coroutineScope)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Login",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (showSnackbar) {
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { showSnackbar = false }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(feedbackMessage)
            }
        }
        Spacer(modifier = Modifier.height(10.dp) )

//        TextButton(onClick = {
//            navController.navigate("register")
//        }
//        ) {
//            Text(text = "Don't have an account, Register")
//
//        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),

            contentAlignment = Alignment.Center
        ){
            Text(text = "Don't have an account ?")
        }

        Button(
            onClick = {
                navController.navigate("register")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Create an Account",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
            Spacer(modifier = Modifier.height(10.dp) )


        Button(
            onClick = {
                navController.navigate("forgot")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Forgot Password",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        }
    }


