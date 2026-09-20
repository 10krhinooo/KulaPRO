package com.example.kulapro.feature.ownership

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.ProposedRestaurant
import com.example.kulapro.data.repository.OwnershipRepository
import com.example.kulapro.data.repository.OwnershipRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.rememberMessageHostState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Asking to be put in charge of a restaurant.
 *
 * One screen for both cases. Claiming a listing that already exists and asking for one to
 * be added differ only in whether the restaurant's details are already known, and splitting
 * them would have duplicated the part that matters: who is asking, how to reach them, and
 * why they say it is theirs.
 *
 * Nothing here grants anything, and the screen says so. A form that looks like it switched
 * something on and did not is worse than one that was honest about waiting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnershipRequestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    restaurantId: String = "",
    restaurantName: String = "",
    repository: OwnershipRepository = remember { OwnershipRepositoryFirestore() },
) {
    val isClaim = restaurantId.isNotBlank()
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()

    var role by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }

    var name by remember { mutableStateOf("") }
    var cuisine by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var seats by remember { mutableStateOf("") }

    var isSubmitting by remember { mutableStateOf(false) }
    var isSent by remember { mutableStateOf(false) }

    // Leaves on its own once the confirmation has been read, so the user is not left on a
    // form they have already sent.
    LaunchedEffect(isSent) {
        if (isSent) {
            delay(SENT_DWELL_MILLIS)
            onBack()
        }
    }

    val canSubmit = role.isNotBlank() && phone.isNotBlank() &&
        (isClaim || (name.isNotBlank() && address.isNotBlank()))

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MessageHost(messages) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isClaim) "Claim this restaurant" else "List your restaurant")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
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
                text = if (isClaim) {
                    "Tell us how you are connected to $restaurantName. Someone at KulaPro " +
                        "reads every request and will be in touch before anything changes."
                } else {
                    "Tell us about the restaurant and how to reach you. Someone at KulaPro " +
                        "reads every request, and the listing goes live once it is approved."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!isClaim) {
                KulaTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Restaurant name",
                    leadingIcon = Icons.Rounded.Restaurant,
                    enabled = !isSubmitting,
                )
                KulaTextField(
                    value = cuisine,
                    onValueChange = { cuisine = it },
                    label = "Cuisine",
                    helperText = "What diners would call it, like Italian or Swahili",
                    enabled = !isSubmitting,
                )
                KulaTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Address",
                    leadingIcon = Icons.Rounded.Place,
                    enabled = !isSubmitting,
                )
                KulaTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    helperText = "A line or two about the place",
                    enabled = !isSubmitting,
                )
                KulaTextField(
                    value = seats,
                    onValueChange = { seats = it.filter(Char::isDigit) },
                    label = "Seats in one sitting",
                    helperText = "Roughly how many people you can seat at once",
                    keyboardType = KeyboardType.Number,
                    enabled = !isSubmitting,
                )
            }

            KulaTextField(
                value = role,
                onValueChange = { role = it },
                label = "Your role there",
                helperText = "Owner, manager, head chef",
                enabled = !isSubmitting,
            )
            KulaTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone we can reach you on",
                leadingIcon = Icons.Rounded.Call,
                keyboardType = KeyboardType.Phone,
                enabled = !isSubmitting,
            )
            KulaTextField(
                value = evidence,
                onValueChange = { evidence = it },
                label = "Anything that helps us confirm it",
                helperText = "A work email, a website, the name on the licence",
                enabled = !isSubmitting,
            )

            PrimaryButton(
                text = "Send request",
                loadingText = "Sending",
                loading = isSubmitting,
                enabled = canSubmit && !isSent,
                onClick = {
                    isSubmitting = true
                    scope.launch {
                        val result = if (isClaim) {
                            repository.requestClaim(
                                restaurantId = restaurantId,
                                restaurantName = restaurantName,
                                role = role,
                                contactPhone = phone,
                                evidence = evidence,
                            )
                        } else {
                            repository.requestNewListing(
                                proposed = ProposedRestaurant(
                                    name = name,
                                    description = description,
                                    cuisine = cuisine,
                                    address = address,
                                    phone = phone,
                                    capacityPerSlot = seats.toIntOrNull() ?: 0,
                                ),
                                role = role,
                                contactPhone = phone,
                                evidence = evidence,
                            )
                        }
                        isSubmitting = false
                        when (result) {
                            is Result.Success -> {
                                isSent = true
                                messages.showSuccess(
                                    "Request sent. We will come back to you on the number " +
                                        "you gave us.",
                                )
                            }

                            is Result.Failure -> messages.showError(result.message)
                        }
                    }
                },
            )
        }
    }
}

/** Long enough to read the confirmation before the screen closes itself. */
private const val SENT_DWELL_MILLIS = 2_200L
