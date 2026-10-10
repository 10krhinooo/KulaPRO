package com.example.kulapro.feature.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels the reminder for a booking.
 *
 * Wrapped rather than calling WorkManager from the view model, so booking logic stays
 * testable without a scheduler and there is one place that knows how a reminder is named.
 */
@Singleton
class BookingReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    /**
     * Books a reminder, replacing any existing one for the same reservation.
     *
     * Does nothing when the sitting has already passed or reminders are off, so a caller
     * does not have to decide whether a reminder is worth having.
     */
    fun schedule(
        reservationId: String,
        restaurantName: String,
        whenLabel: String,
        startsAtMillis: Long,
        leadHours: Int,
        enabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (!enabled) return
        val delay = BookingReminder.delayMillis(startsAtMillis, nowMillis, leadHours) ?: return

        workManager.enqueueUniqueWork(
            BookingReminder.workName(reservationId),
            // Replace, so changing a booking moves its reminder rather than adding one.
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BookingReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(BookingReminderWorker.KEY_RESTAURANT_NAME, restaurantName)
                        .putString(BookingReminderWorker.KEY_WHEN_LABEL, whenLabel)
                        .build(),
                )
                .build(),
        )
    }

    /** Cancelling a booking cancels its reminder, or the nudge outlives the table. */
    fun cancel(reservationId: String) {
        workManager.cancelUniqueWork(BookingReminder.workName(reservationId))
    }
}

/** Provided rather than constructed, so a test can hand in its own WorkManager. */
@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object ReminderModule {

    @dagger.Provides
    @Singleton
    fun workManager(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)
}
