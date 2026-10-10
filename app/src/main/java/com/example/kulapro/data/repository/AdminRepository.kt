package com.example.kulapro.data.repository

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant

/**
 * The platform as a whole, read in one go.
 *
 * One snapshot rather than a stream per number: these are figures somebody looks at, thinks
 * about and then leaves, not a dashboard that has to move as bookings land. A live listener
 * per metric would cost far more and tell the reader nothing extra.
 */
data class PlatformSnapshot(
    val restaurants: List<Restaurant> = emptyList(),
    /** Bookings whose sitting falls inside the window asked for, all restaurants. */
    val reservations: List<Reservation> = emptyList(),
    val dinerCount: Int = 0,
    val pendingRequestCount: Int = 0,
)

/**
 * Platform-wide reads, available only to an account whose claims say PLATFORM_ADMIN.
 *
 * Separate from [OwnerRepository] because these are different questions asked by different
 * people. An owner asks about their own room; this asks whether the service works at all,
 * and the security rules draw the same line.
 */
interface AdminRepository {

    /** Everything the platform view needs, covering the last [days] days of bookings. */
    suspend fun snapshot(days: Int): Result<PlatformSnapshot>
}
