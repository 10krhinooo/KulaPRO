package com.example.kulapro.feature.ownership

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.OwnershipRequest
import com.example.kulapro.data.model.RequestStatus

/**
 * Where a user's own ownership requests got to.
 *
 * A request that vanishes after sending looks like it was thrown away, so every one stays
 * visible until it is decided, and a decline shows the reason with it.
 */
@Composable
fun MyRequestsCard(requests: List<OwnershipRequest>, modifier: Modifier = Modifier) {
    if (requests.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Your restaurant requests", style = MaterialTheme.typography.titleMedium)

            requests.forEach { request ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = request.restaurantName.ifBlank { "Your restaurant" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(request.statusEnum)
                    }
                    Text(
                        text = when (request.statusEnum) {
                            RequestStatus.PENDING ->
                                "We are looking at this and will call you on " +
                                    request.contactPhone.ifBlank { "the number you gave us" }

                            RequestStatus.APPROVED ->
                                "Approved. Switch to hosting from the home screen to manage it."

                            RequestStatus.REJECTED -> request.reviewNote.ifBlank {
                                "Declined. Get in touch if you think this was a mistake."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: RequestStatus) {
    val container = when (status) {
        RequestStatus.PENDING -> MaterialTheme.colorScheme.secondaryContainer
        RequestStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
        RequestStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (status) {
        RequestStatus.PENDING -> MaterialTheme.colorScheme.onSecondaryContainer
        RequestStatus.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
        RequestStatus.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = when (status) {
                RequestStatus.PENDING -> "Waiting"
                RequestStatus.APPROVED -> "Approved"
                RequestStatus.REJECTED -> "Declined"
            },
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
