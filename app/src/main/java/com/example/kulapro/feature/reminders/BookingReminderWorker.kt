package com.example.kulapro.feature.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.kulapro.R
import com.example.kulapro.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Reminds someone about a table, from the device itself.
 *
 * Deliberately local rather than a push from a server. A scheduled Cloud Function would
 * need a billing plan the project does not have, and for a reminder about a booking the
 * phone already knows about, sending it round trip to a server to be sent back is work for
 * its own sake. The trade is that it does not survive the app being uninstalled, and does
 * not fire if the restaurant cancels while the phone is offline.
 */
@HiltWorker
class BookingReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Checked at fire time, not at schedule time: someone who turns reminders off after
        // booking should not still get one.
        if (!settingsRepository.settings.first().remindersEnabled) return Result.success()

        val restaurant = inputData.getString(KEY_RESTAURANT_NAME).orEmpty()
        val whenLabel = inputData.getString(KEY_WHEN_LABEL).orEmpty()
        if (restaurant.isBlank()) return Result.success()

        notify(restaurant, whenLabel)
        return Result.success()
    }

    private fun notify(restaurant: String, whenLabel: String) {
        val manager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Booking reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "A nudge before a table you have booked"
                },
            )
        }

        // From Android 13 posting a notification needs permission. Without this check the
        // call is simply dropped, which is harder to explain than not sending one.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return

        manager.notify(
            id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_kula_mark)
                .setContentTitle("Your table at $restaurant")
                .setContentText(
                    if (whenLabel.isBlank()) {
                        "Coming up shortly."
                    } else {
                        "Coming up at $whenLabel."
                    },
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val KEY_RESTAURANT_NAME = "restaurantName"
        const val KEY_WHEN_LABEL = "whenLabel"
        private const val CHANNEL_ID = "booking-reminders"
    }
}
