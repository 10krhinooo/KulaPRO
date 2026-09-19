package com.example.kulapro.feature.owner

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.ui.theme.Motion

/**
 * What an owner needs to know on opening the app: how full tonight is, and who is coming.
 *
 * This is the screen that makes the customer-flow claim real, rather than a line in a README.
 */
@Composable
fun OwnerDashboard(
    restaurant: Restaurant,
    todaysBookings: List<Reservation>,
    onEditRestaurant: () -> Unit,
    onViewBookings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val covers = todaysBookings
        .filter { it.statusEnum.occupiesCapacity }
        .sumOf { it.partySize }
    val slotsToday = todaysBookings
        .filter { it.statusEnum.occupiesCapacity }
        .map { it.startsAt.seconds / SECONDS_PER_HOUR }
        .distinct()
        .size
    val theoreticalCapacity = (restaurant.capacityPerSlot * slotsToday.coerceAtLeast(1))
    val occupancy = if (theoreticalCapacity > 0) {
        (covers * PERCENT / theoreticalCapacity).coerceIn(0, PERCENT)
    } else {
        0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(restaurant.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Today at a glance",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Covers today",
                value = covers,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Bookings",
                value = todaysBookings.count { it.statusEnum.occupiesCapacity },
                modifier = Modifier.weight(1f),
            )
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Occupancy", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(
                    progress = { occupancy / PERCENT.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                )
                Text(
                    text = "$occupancy% of today's seats are spoken for",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(onClick = onViewBookings, modifier = Modifier.fillMaxWidth()) {
            Text("See today's bookings")
        }
        Button(onClick = onEditRestaurant, modifier = Modifier.fillMaxWidth()) {
            Text("Edit restaurant details")
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    // Counting up rather than snapping makes the number read as a live measurement.
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = Motion.smooth(),
        label = "stat_$label",
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = animated.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Start,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private const val PERCENT = 100
private const val SECONDS_PER_HOUR = 3600
