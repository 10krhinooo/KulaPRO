package com.example.kulapro.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.example.kulapro.data.model.Review
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.ReservationRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.repository.ReviewRepository
import com.example.kulapro.data.repository.ReviewRepositoryFirestore
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.RestaurantCardSkeleton
import com.example.kulapro.ui.components.ReviewDialog
import com.example.kulapro.ui.components.SignInPrompt
import com.example.kulapro.ui.components.StatusBadge
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.ui.components.report
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The user's reservations, read from Firestore.
 *
 * The first version rendered three hardcoded rows, which is what hid the fact that no
 * reservation had ever been written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationScreen(
    onSignIn: () -> Unit,
    isSignedIn: Boolean,
    modifier: Modifier = Modifier,
    repository: ReservationRepository = remember { ReservationRepositoryFirestore() },
    reviewRepository: ReviewRepository = remember { ReviewRepositoryFirestore() },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()
    var isLoading by remember { mutableStateOf(true) }
    var reviewTarget by remember { mutableStateOf<Reservation?>(null) }
    var isPostingReview by remember { mutableStateOf(false) }

    val reservations by produceState(initialValue = emptyList<Reservation>(), repository) {
        repository.myReservations().collect {
            value = it
            isLoading = false
        }
    }

    reviewTarget?.let { target ->
        ReviewDialog(
            restaurantName = target.restaurantName.ifBlank { "this restaurant" },
            submitting = isPostingReview,
            onDismiss = { reviewTarget = null },
            onSubmit = { rating, comment ->
                isPostingReview = true
                scope.launch {
                    val result = reviewRepository.submit(
                        Review(
                            restaurantId = target.restaurantId,
                            reservationId = target.id,
                            rating = rating,
                            comment = comment,
                        ),
                    )
                    isPostingReview = false
                    reviewTarget = null
                    messages.report(result, "Thanks, your review is live")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Your reservations") }) },
        snackbarHost = { MessageHost(messages) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !isSignedIn -> SignInPrompt(
                    title = "Sign in to see your bookings",
                    description = "Your reservations are tied to your account. " +
                        "Browsing restaurants does not need one.",
                    onSignIn = onSignIn,
                )

                isLoading -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(PLACEHOLDER_COUNT) { RestaurantCardSkeleton() }
                }

                reservations.isEmpty() -> EmptyState(
                    title = "No reservations yet",
                    description = "When you book a table it will show up here.",
                    icon = Icons.AutoMirrored.Outlined.EventNote,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(
                        items = reservations,
                        key = { _, reservation -> reservation.id },
                    ) { index, reservation ->
                        AnimatedListItem(index = index) {
                        ReservationItem(
                            reservation = reservation,
                            onCancel = {
                                scope.launch {
                                    when (val result = repository.cancel(reservation.id)) {
                                        is Result.Success ->
                                            messages.showSuccess("Reservation cancelled")

                                        is Result.Failure ->
                                            messages.showError(result.message)
                                    }
                                }
                            },
                            onReview = { reviewTarget = reservation },
                        )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationItem(
    reservation: Reservation,
    onCancel: () -> Unit,
    onReview: () -> Unit,
) {
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
            StatusBadge(status = status, modifier = Modifier.padding(top = 4.dp))

            when {
                status.occupiesCapacity -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onCancel) { Text("Cancel booking") }
                }

                // Only a completed visit can be reviewed, which mirrors what the security
                // rules will accept, so the button never leads to a rejected write.
                status == ReservationStatus.COMPLETED -> {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    TextButton(onClick = onReview) { Text("Leave a review") }
                }
            }
        }
    }
}

private const val PLACEHOLDER_COUNT = 3
