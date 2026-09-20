package com.example.kulapro.feature.reservations

import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Review
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.repository.ReviewRepository
import com.example.kulapro.feature.reminders.BookingReminderScheduler
import com.example.kulapro.util.Clock
import com.google.firebase.Timestamp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Reservations a test can set, with a real flow so a cancellation is seen by the screen. */
private class FakeMyReservations(
    initial: List<Reservation> = emptyList(),
    var cancelFailure: String? = null,
) : com.example.kulapro.data.repository.ReservationRepository {

    val state = MutableStateFlow(initial)
    val cancelled = mutableListOf<String>()

    override fun myReservations() = state

    override suspend fun slotCountsFor(
        restaurantId: String,
        from: Timestamp,
        to: Timestamp,
    ): Result<Map<Long, com.example.kulapro.data.model.SlotCount>> = Result.Success(emptyMap())

    override suspend fun slotCountsForAll(
        from: Timestamp,
        to: Timestamp,
    ): Result<List<com.example.kulapro.data.model.SlotCount>> = Result.Success(emptyList())

    override suspend fun create(reservation: Reservation): Result<String> =
        Result.Success("new")

    override suspend fun cancel(reservationId: String): Result<Unit> {
        cancelled += reservationId
        cancelFailure?.let { return Result.Failure(it) }
        state.value = state.value.map {
            if (it.id == reservationId) {
                it.copy(status = ReservationStatus.CANCELLED.name)
            } else {
                it
            }
        }
        return Result.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReservationsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    private val clock = Clock { now }

    private lateinit var reservations: FakeMyReservations
    private lateinit var reviews: ReviewRepository
    private lateinit var reminders: BookingReminderScheduler
    private lateinit var auth: AuthRepository
    private lateinit var signedInUid: MutableStateFlow<String?>

    private fun at(offsetMinutes: Int) =
        Timestamp(Date(now.time + offsetMinutes * 60L * 1000L))

    private fun booking(
        id: String,
        offsetMinutes: Int,
        status: ReservationStatus = ReservationStatus.CONFIRMED,
    ) = Reservation(
        id = id,
        restaurantId = "r1",
        restaurantName = "The Bistro",
        startsAt = at(offsetMinutes),
        status = status.name,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        reservations = FakeMyReservations()
        reviews = mockk(relaxed = true)
        reminders = mockk(relaxed = true)
        signedInUid = MutableStateFlow<String?>("user-1")
        auth = mockk(relaxed = true)
        every { auth.authState() } returns signedInUid
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReservationsViewModel(
        reservationRepository = reservations,
        reviewRepository = reviews,
        reminderScheduler = reminders,
        authRepository = auth,
        clock = clock,
    )

    @Test
    fun `a signed in user's bookings load`() = runTest(dispatcher) {
        reservations.state.value = listOf(booking("b1", 120))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals("b1", viewModel.state.value.next?.id)
    }

    @Test
    fun `a guest is asked to sign in rather than shown an empty list`() =
        runTest(dispatcher) {
            signedInUid.value = null

            val viewModel = viewModel()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSignedIn)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `signing out empties the list rather than leaving one person's bookings`() =
        runTest(dispatcher) {
            reservations.state.value = listOf(booking("b1", 120))
            val viewModel = viewModel()
            advanceUntilIdle()

            signedInUid.value = null
            advanceUntilIdle()

            assertTrue(viewModel.state.value.reservations.isEmpty())
        }

    @Test
    fun `cancelling a booking gives the seats back and says so`() = runTest(dispatcher) {
        reservations.state.value = listOf(booking("b1", 120))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.cancel(booking("b1", 120))
        advanceUntilIdle()

        assertEquals(listOf("b1"), reservations.cancelled)
        assertEquals("Booking cancelled. The seats are back.", viewModel.state.value.message)
        assertFalse(viewModel.state.value.isError)
        assertNull(viewModel.state.value.busyReservationId)
    }

    @Test
    fun `cancelling a booking cancels its reminder too`() = runTest(dispatcher) {
        reservations.state.value = listOf(booking("b1", 120))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.cancel(booking("b1", 120))
        advanceUntilIdle()

        // A notification for a table nobody is keeping is worse than no notification.
        verify { reminders.cancel("b1") }
    }

    @Test
    fun `a cancelled booking moves out of upcoming`() = runTest(dispatcher) {
        reservations.state.value = listOf(booking("b1", 120))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.cancel(booking("b1", 120))
        advanceUntilIdle()

        assertNull(viewModel.state.value.next)
        assertEquals(listOf("b1"), viewModel.state.value.past.map { it.id })
    }

    @Test
    fun `a cancellation that fails says why and leaves the booking alone`() =
        runTest(dispatcher) {
            reservations = FakeMyReservations(
                initial = listOf(booking("b1", 120)),
                cancelFailure = "You are offline.",
            )
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.cancel(booking("b1", 120))
            advanceUntilIdle()

            assertEquals("You are offline.", viewModel.state.value.message)
            assertTrue(viewModel.state.value.isError)
            assertEquals("b1", viewModel.state.value.next?.id)
        }

    @Test
    fun `a review is posted against the reservation it is about`() = runTest(dispatcher) {
        val done = booking("b1", -120, ReservationStatus.COMPLETED)
        reservations.state.value = listOf(done)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startReview(done)
        viewModel.submitReview(rating = 5, comment = "Lovely")
        advanceUntilIdle()

        // The reservation id is what the security rules read to check the reviewer
        // actually ate there.
        coVerify {
            reviews.submit(
                match<Review> {
                    it.reservationId == "b1" && it.restaurantId == "r1" && it.rating == 5
                },
            )
        }
    }

    @Test
    fun `a posted review closes the dialog and thanks the reviewer`() = runTest(dispatcher) {
        val done = booking("b1", -120, ReservationStatus.COMPLETED)
        reservations.state.value = listOf(done)
        coEvery { reviews.submit(any()) } returns Result.Success(Unit)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startReview(done)
        viewModel.submitReview(rating = 5, comment = "Lovely")
        advanceUntilIdle()

        assertNull(viewModel.state.value.reviewTarget)
        assertFalse(viewModel.state.value.isPostingReview)
        assertEquals("Thanks, your review is live", viewModel.state.value.message)
    }

    @Test
    fun `a review that is refused says why`() = runTest(dispatcher) {
        val done = booking("b1", -120, ReservationStatus.COMPLETED)
        reservations.state.value = listOf(done)
        coEvery { reviews.submit(any()) } returns
            Result.Failure("You can only review a table you have been to.")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startReview(done)
        viewModel.submitReview(rating = 1, comment = "")
        advanceUntilIdle()

        assertEquals(
            "You can only review a table you have been to.",
            viewModel.state.value.message,
        )
        assertTrue(viewModel.state.value.isError)
    }

    @Test
    fun `submitting with nothing selected does nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.submitReview(rating = 5, comment = "")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isPostingReview)
    }

    @Test
    fun `dismissing the review dialog writes nothing`() = runTest(dispatcher) {
        val done = booking("b1", -120, ReservationStatus.COMPLETED)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.startReview(done)
        viewModel.cancelReview()

        assertNull(viewModel.state.value.reviewTarget)
    }

    @Test
    fun `the tab can be switched`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.selectTab(ReservationTab.PAST)

        assertEquals(ReservationTab.PAST, viewModel.state.value.tab)
    }

    @Test
    fun `a message can be dismissed`() = runTest(dispatcher) {
        reservations.state.value = listOf(booking("b1", 120))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.cancel(booking("b1", 120))
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertNull(viewModel.state.value.message)
    }
}
