package com.example.kulapro.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.ReservationRepositoryFirestore
import com.example.kulapro.data.repository.Result
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The user's reservations, read from Firestore.
 *
 * The first version rendered three hardcoded rows, which is what hid the fact that no
 * reservation had ever been written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(
    modifier: Modifier = Modifier,
    repository: ReservationRepository = remember { ReservationRepositoryFirestore() },
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isLoading by remember { mutableStateOf(true) }

    val reservations by produceState(initialValue = emptyList<Reservation>(), repository) {
        repository.myReservations().collect {
            value = it
            isLoading = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Your reservations") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator()

                reservations.isEmpty() -> EmptyReservations()

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(reservations, key = { it.id }) { reservation ->
                        ReservationItem(
                            reservation = reservation,
                            onCancel = {
                                scope.launch {
                                    when (val result = repository.cancel(reservation.id)) {
                                        is Result.Success ->
                                            snackbarHostState.showSnackbar("Reservation cancelled")

                                        is Result.Failure ->
                                            snackbarHostState.showSnackbar(result.message)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReservations() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Text("No reservations yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "When you book a table it will show up here.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ReservationItem(reservation: Reservation, onCancel: () -> Unit) {
    val formatter = remember { SimpleDateFormat("EEE d MMM yyyy 'at' h:mm a", Locale.getDefault()) }
    val status = reservation.statusEnum

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = reservation.restaurantName.ifBlank { "Restaurant" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatter.format(reservation.startsAt.toDate()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${reservation.partySize} " +
                    if (reservation.partySize == 1) "guest" else "guests",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = status.name.lowercase().replaceFirstChar { it.uppercase() }
                    .replace('_', ' '),
                style = MaterialTheme.typography.labelLarge,
            )

            if (status.occupiesCapacity) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onCancel) { Text("Cancel booking") }
            }
        }
    }
}
