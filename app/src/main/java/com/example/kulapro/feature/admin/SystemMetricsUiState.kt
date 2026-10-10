package com.example.kulapro.feature.admin

import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.repository.PlatformSnapshot
import com.example.kulapro.domain.DayLoad
import com.example.kulapro.domain.HourLoad
import com.example.kulapro.domain.PlatformMetrics
import java.util.Date

/**
 * How the platform is doing, as the admin console reads it.
 *
 * The figures are derived here rather than stored, so there is no counter to drift out of
 * step with the bookings it claims to describe. The snapshot is the only fact; everything
 * below it is a question asked of that fact.
 */
data class SystemMetricsUiState(
    val isLoading: Boolean = true,
    val window: MetricsWindow = MetricsWindow.MONTH,
    val snapshot: PlatformSnapshot = PlatformSnapshot(),
    val now: Date = Date(),
    val error: String? = null,
) {
    val restaurantCount: Int get() = snapshot.restaurants.size

    /**
     * Listings nobody runs yet.
     *
     * The number that says whether the platform is a directory or a service: a restaurant
     * with no owner cannot confirm a booking, so every one of these is a diner about to be
     * let down.
     */
    val unclaimedRestaurantCount: Int
        get() = snapshot.restaurants.count { it.ownerUserId.isBlank() }

    val dinerCount: Int get() = snapshot.dinerCount

    val pendingRequestCount: Int get() = snapshot.pendingRequestCount

    val bookingCount: Int get() = snapshot.reservations.size

    val coverCount: Int get() = PlatformMetrics.covers(snapshot.reservations)

    val cancellationRate: Float
        get() = PlatformMetrics.shareEnding(snapshot.reservations, ReservationStatus.CANCELLED)

    val noShowRate: Float
        get() = PlatformMetrics.shareEnding(snapshot.reservations, ReservationStatus.NO_SHOW)

    val walkInShare: Float get() = PlatformMetrics.walkInShare(snapshot.reservations)

    val coversByDay: List<DayLoad>
        get() = PlatformMetrics.coversByDay(snapshot.reservations, window.days, now)

    val coversByHour: List<HourLoad>
        get() = PlatformMetrics.coversByHour(snapshot.reservations)

    val busiestHour: HourLoad? get() = PlatformMetrics.busiestHour(snapshot.reservations)

    /** Nothing was booked in the window, so the charts would be a row of flat lines. */
    val hasNoBookings: Boolean get() = !isLoading && snapshot.reservations.isEmpty()
}

/** How far back the console is looking. */
enum class MetricsWindow(val days: Int) {
    WEEK(DAYS_IN_WEEK),
    MONTH(DAYS_IN_MONTH),
    QUARTER(DAYS_IN_QUARTER),
    ;

    /** Derived rather than written out, so a chip can never disagree with what it fetches. */
    val label: String get() = "$days days"
}

private const val DAYS_IN_WEEK = 7
private const val DAYS_IN_MONTH = 30
private const val DAYS_IN_QUARTER = 90
