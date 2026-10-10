package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.feature.booking.FakeRestaurantRepository
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerBookingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var owner: FakeOwnerRepository
    private lateinit var restaurants: FakeRestaurantRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        owner = FakeOwnerRepository()
        restaurants = FakeRestaurantRepository(
            restaurant = Restaurant(id = "r1", name = "The Bistro", capacityPerSlot = 40),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = OwnerBookingsViewModel(
        ownerRepository = owner,
        restaurantRepository = restaurants,
        savedStateHandle = SavedStateHandle(
            mapOf(OwnerBookingsViewModel.ARG_RESTAURANT_ID to "r1"),
        ),
    )

    private fun todayAt(hour: Int): Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun booking(
        id: String,
        partySize: Int = 2,
        status: ReservationStatus = ReservationStatus.CONFIRMED,
        hour: Int = 19,
        isWalkIn: Boolean = false,
    ) = Reservation(
        id = id,
        userId = "u1",
        restaurantId = "r1",
        startsAt = Timestamp(todayAt(hour)),
        partySize = partySize,
        status = status.name,
        isWalkIn = isWalkIn,
    )

    @Test
    fun `opens on today with a week behind it`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        // Yesterday's service is when anyone asks what the no-show rate was.
        assertEquals(DAYS_BEHIND, state.days.indexOf(state.selectedDate))
        assertEquals("The Bistro", state.restaurantName)
    }

    @Test
    fun `counts covers only for parties still occupying a table`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(
            booking("a", partySize = 4, status = ReservationStatus.CONFIRMED),
            booking("b", partySize = 2, status = ReservationStatus.SEATED),
            booking("c", partySize = 6, status = ReservationStatus.CANCELLED),
            booking("d", partySize = 3, status = ReservationStatus.COMPLETED),
        )

        val vm = viewModel()
        advanceUntilIdle()

        // Cancelled never came and completed has left, so neither is taking a seat.
        assertEquals(6, vm.state.value.covers)
        assertEquals(1, vm.state.value.expected)
        assertEquals(1, vm.state.value.seated)
    }

    @Test
    fun `reports the no-show rate over settled bookings only`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(
            booking("a", status = ReservationStatus.COMPLETED),
            booking("b", status = ReservationStatus.COMPLETED),
            booking("c", status = ReservationStatus.NO_SHOW),
            // Still to arrive, so it cannot count either way yet.
            booking("d", status = ReservationStatus.CONFIRMED),
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(33, vm.state.value.noShowPercent)
    }

    @Test
    fun `reports no no-shows rather than dividing by nothing`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(booking("a", status = ReservationStatus.CONFIRMED))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, vm.state.value.noShowPercent)
    }

    @Test
    fun `filters down to what the owner asked for`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(
            booking("a", status = ReservationStatus.PENDING),
            booking("b", status = ReservationStatus.SEATED),
            booking("c", status = ReservationStatus.COMPLETED),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.setFilter(BookingFilter.PENDING)
        assertEquals(listOf("a"), vm.state.value.visible.map { it.id })

        vm.setFilter(BookingFilter.SEATED)
        assertEquals(listOf("b"), vm.state.value.visible.map { it.id })

        vm.setFilter(BookingFilter.FINISHED)
        assertEquals(listOf("c"), vm.state.value.visible.map { it.id })

        vm.setFilter(BookingFilter.OPEN)
        assertEquals(listOf("a", "b"), vm.state.value.visible.map { it.id })

        vm.setFilter(BookingFilter.ALL)
        assertEquals(3, vm.state.value.visible.size)
    }

    @Test
    fun `moves a pending booking to confirmed`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(booking("a", status = ReservationStatus.PENDING))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateStatus(vm.state.value.bookings.single(), ReservationStatus.CONFIRMED)
        advanceUntilIdle()

        assertEquals(ReservationStatus.CONFIRMED, vm.state.value.bookings.single().statusEnum)
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.message!!.isError)
    }

    @Test
    fun `refuses a transition the status does not allow`() = runTest(dispatcher) {
        // A completed booking cannot become a no-show: the party demonstrably showed up.
        owner.bookings = mutableListOf(booking("a", status = ReservationStatus.COMPLETED))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateStatus(vm.state.value.bookings.single(), ReservationStatus.NO_SHOW)
        advanceUntilIdle()

        assertEquals(ReservationStatus.COMPLETED, vm.state.value.bookings.single().statusEnum)
    }

    @Test
    fun `refuses seating a cancelled booking`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(booking("a", status = ReservationStatus.CANCELLED))
        val vm = viewModel()
        advanceUntilIdle()

        vm.updateStatus(vm.state.value.bookings.single(), ReservationStatus.SEATED)
        advanceUntilIdle()

        assertEquals(ReservationStatus.CANCELLED, vm.state.value.bookings.single().statusEnum)
    }

    @Test
    fun `walks a booking through a whole service`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(booking("a", status = ReservationStatus.PENDING))
        val vm = viewModel()
        advanceUntilIdle()

        listOf(
            ReservationStatus.CONFIRMED,
            ReservationStatus.SEATED,
            ReservationStatus.COMPLETED,
        ).forEach { next ->
            vm.updateStatus(vm.state.value.bookings.single(), next)
            advanceUntilIdle()
            assertEquals(next, vm.state.value.bookings.single().statusEnum)
        }

        // Finished, so it no longer holds a seat.
        assertEquals(0, vm.state.value.covers)
    }

    @Test
    fun `says why when a status change fails and leaves the row alone`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(booking("a", status = ReservationStatus.PENDING))
        val vm = viewModel()
        advanceUntilIdle()
        owner.updateFailure = "You are not allowed to do that."

        vm.updateStatus(vm.state.value.bookings.single(), ReservationStatus.CONFIRMED)
        advanceUntilIdle()

        assertEquals(ReservationStatus.PENDING, vm.state.value.bookings.single().statusEnum)
        assertTrue(vm.state.value.message!!.isError)
    }

    @Test
    fun `records a walk-in, which takes seats like any other party`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addWalkIn(partySize = 4, at = todayAt(20))
        advanceUntilIdle()

        assertEquals(1, owner.walkIns.size)
        assertEquals(4, owner.walkIns.single().partySize)
        assertTrue(owner.walkIns.single().isWalkIn)
        // Seated on arrival by definition, so it counts against the sitting immediately.
        assertEquals(4, vm.state.value.covers)
        assertEquals(1, vm.state.value.walkIns)
    }

    @Test
    fun `refuses a walk-in of nobody`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.addWalkIn(partySize = 0, at = todayAt(20))
        advanceUntilIdle()

        assertTrue(owner.walkIns.isEmpty())
    }

    @Test
    fun `shows an empty day rather than yesterday's bookings when the read fails`() =
        runTest(dispatcher) {
            owner.bookings = mutableListOf(booking("a"))
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(1, vm.state.value.bookings.size)

            owner.bookingsFailure = "We cannot reach KulaPro right now."
            vm.selectDate(vm.state.value.days.first())
            advanceUntilIdle()

            assertTrue(vm.state.value.bookings.isEmpty())
            assertTrue(vm.state.value.message!!.isError)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `changing the day loads that day`() = runTest(dispatcher) {
        owner.bookings = mutableListOf(booking("today"))
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.bookings.size)

        vm.selectDate(vm.state.value.days.first())
        advanceUntilIdle()

        // A week ago had no service.
        assertTrue(vm.state.value.bookings.isEmpty())
    }

    @Test
    fun `offers three weeks of service around today`() {
        val days = serviceDays(Date())

        assertEquals(21, days.size)
        days.zipWithNext { a, b -> assertTrue(b.after(a)) }
    }
}
