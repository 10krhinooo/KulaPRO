package com.example.kulapro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.ui.theme.Motion

/**
 * Reservation status as a coloured pill.
 *
 * The colour animates on change so a booking moving from pending to confirmed reads as the
 * same row changing state, rather than as a different row appearing.
 */
@Composable
fun StatusBadge(status: ReservationStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    val targetContainer = when (status) {
        ReservationStatus.CONFIRMED -> scheme.primaryContainer
        ReservationStatus.PENDING -> scheme.secondaryContainer
        ReservationStatus.COMPLETED -> scheme.surfaceVariant
        ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> scheme.errorContainer
    }
    val targetContent = when (status) {
        ReservationStatus.CONFIRMED -> scheme.onPrimaryContainer
        ReservationStatus.PENDING -> scheme.onSecondaryContainer
        ReservationStatus.COMPLETED -> scheme.onSurfaceVariant
        ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW -> scheme.onErrorContainer
    }

    val container by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = Motion.smooth(),
        label = "statusContainer",
    )
    val content by animateColorAsState(
        targetValue = targetContent,
        animationSpec = Motion.smooth(),
        label = "statusContent",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PILL_CORNER_PERCENT),
        color = container,
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** A fully rounded pill: the radius is half the height whatever the text length. */
private const val PILL_CORNER_PERCENT = 50

private val ReservationStatus.label: String
    get() = when (this) {
        ReservationStatus.PENDING -> "Pending"
        ReservationStatus.CONFIRMED -> "Confirmed"
        ReservationStatus.CANCELLED -> "Cancelled"
        ReservationStatus.COMPLETED -> "Completed"
        ReservationStatus.NO_SHOW -> "Missed"
    }
