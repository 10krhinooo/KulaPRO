package com.example.kulapro.data.repository

import com.example.kulapro.data.model.RequestStatus
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.domain.startOfDay
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date
import kotlinx.coroutines.tasks.await

class AdminRepositoryFirestore(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AdminRepository {

    override suspend fun snapshot(days: Int): Result<PlatformSnapshot> = runCatchingFirestore {
        // From the start of the first day in the window rather than from this moment minus
        // n days, so a window of one day means today and not "since this time yesterday".
        val from = Timestamp(
            Date(startOfDay(Date()).time - (days - 1).coerceAtLeast(0) * DAY_MILLIS),
        )

        val restaurants = firestore.collection(FirestorePaths.RESTAURANTS)
            .get()
            .await()
            .toObjects(Restaurant::class.java)

        val reservations = firestore.collection(FirestorePaths.RESERVATIONS)
            .whereGreaterThanOrEqualTo(FIELD_STARTS_AT, from)
            .get()
            .await()
            .toObjects(Reservation::class.java)

        // Counted on the server rather than fetched. Nobody needs the documents, and the
        // number of registered accounts is exactly the figure that grows until downloading
        // them all is the most expensive read in the app.
        val dinerCount = firestore.collection(FirestorePaths.USERS)
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
            .toInt()

        val pendingRequestCount = firestore.collection(FirestorePaths.OWNERSHIP_REQUESTS)
            .whereEqualTo(FIELD_STATUS, RequestStatus.PENDING.name)
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
            .toInt()

        PlatformSnapshot(
            restaurants = restaurants,
            reservations = reservations,
            dinerCount = dinerCount,
            pendingRequestCount = pendingRequestCount,
        )
    }
}

private const val FIELD_STARTS_AT = "startsAt"
private const val FIELD_STATUS = "status"
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
