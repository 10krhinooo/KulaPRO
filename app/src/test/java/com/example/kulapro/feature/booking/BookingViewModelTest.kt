package com.example.kulapro.feature.booking

import androidx.lifecycle.SavedStateHandle
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.data.model.SlotCount
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var restaurants: FakeRestaurantRepository
    private lateinit var reservations: FakeReservationRepository

    private val openAllHours = mapOf(
        "monday" to "00:00-23:59", "tuesday" to "00:00-23:59",
        "wednesday" to "00:00-23:59", "thursday" to "00:00-23:59",
        "friday" to "00:00-23:59", "saturday" to "00:00-23:59",
        "sunday" to "00:00-23:59",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        restaurants = FakeRestaurantRepository(
            restaurant = Restaurant(
                id = "r1",
                name = "The Bistro",
                capacityPerSlot = 20,
                slotDurationMinutes = 90,
                openingHours = openAllHours,
            ),
        )
        reservations = FakeReservationRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BookingViewModel(
        reservationRepository = reservations,
        restaurantRepository = restaurants,
        savedStateHandle = SavedStateHandle(
            mapOf(
                BookingViewModel.ARG_RESTAURANT_ID to "r1",
                BookingViewModel.ARG_RESTAURANT_NAME to "The Bistro",
            ),
        ),
    )

    @Test
    fun `opens on today with a sensible party size`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(BOOKABLE_DAYS, state.days.size)
        assertEquals(state.days.first(), state.selectedDate)
        assertEquals(DEFAULT_PARTY_SIZE, state.partySize)
        assertFalse(state.isLoadingSlots)
    }

    @Test
    fun `offers slots once availability lands`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.slots.isNotEmpty())
        assertFalse(vm.state.value.isClosedToday)
    }

    @Test
    fun `says the restaurant is closed rather than full when there are no sittings`() =
        runTest(dispatcher) {
            restaurants.restaurant = restaurants.restaurant.copy(openingHours = emptyMap())

            val vm = viewModel()
            advanceUntilIdle()

            // Two different problems with two different answers, so they are not collapsed.
            assertTrue(vm.state.value.isClosedToday)
            assertFalse(vm.state.value.isFullyBooked)
        }

    @Test
    fun `says fully booked when every sitting is taken`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val allFull = vm.state.value.slots.associate { slot ->
            slot.startsAt.time / 1000 to SlotCount(
                restaurantId = "r1",
                startsAtSeconds = slot.startsAt.time / 1000,
                seatsTaken = 20,
            )
        }
        reservations.slotCounts = allFull

        vm.setPartySize(4)
        advanceUntilIdle()

        assertTrue(vm.state.value.isFullyBooked)
        assertFalse(vm.state.value.isClosedToday)
    }

    @Test
    fun `raising the party size releases the slot already chosen`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })
        assertNotNull(vm.state.value.selectedSlot)

        // The chosen sitting may no longer fit, so it cannot quietly survive the change.
        vm.setPartySize(8)
        advanceUntilIdle()

        assertNull(vm.state.value.selectedSlot)
    }

    @Test
    fun `changing the day releases both the slot and the table`() = runTest(dispatcher) {
        restaurants.tables = listOf(table("t1", seats = 4))
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })
        vm.toggleTable(restaurants.tables.first())
        assertNotNull(vm.state.value.selectedTable)

        vm.selectDate(vm.state.value.days[1])
        advanceUntilIdle()

        assertNull(vm.state.value.selectedSlot)
        assertNull(vm.state.value.selectedTable)
    }

    @Test
    fun `changing the sitting releases the table, which was only free in the old one`() =
        runTest(dispatcher) {
            restaurants.tables = listOf(table("t1", seats = 4))
            val vm = viewModel()
            advanceUntilIdle()
            val free = vm.state.value.slots.filter { it.isAvailable }
            vm.selectSlot(free[0])
            vm.toggleTable(restaurants.tables.first())

            vm.selectSlot(free[1])

            assertNull(vm.state.value.selectedTable)
        }

    @Test
    fun `refuses a table too small for the party`() = runTest(dispatcher) {
        restaurants.tables = listOf(table("t1", seats = 2))
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })

        vm.setPartySize(6)
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })
        vm.toggleTable(restaurants.tables.first())

        assertNull(vm.state.value.selectedTable)
    }

    @Test
    fun `refuses a table someone else already has`() = runTest(dispatcher) {
        restaurants.tables = listOf(table("t1", seats = 4))
        val vm = viewModel()
        advanceUntilIdle()
        val slot = vm.state.value.slots.first { it.isAvailable }
        reservations.slotCounts = mapOf(
            slot.startsAt.time / 1000 to SlotCount(
                restaurantId = "r1",
                startsAtSeconds = slot.startsAt.time / 1000,
                seatsTaken = 2,
                takenTableIds = listOf("t1"),
            ),
        )
        vm.setPartySize(4)
        advanceUntilIdle()

        val refreshed = vm.state.value.slots.first { it.startsAt == slot.startsAt }
        vm.selectSlot(refreshed)
        vm.toggleTable(restaurants.tables.first())

        assertNull(vm.state.value.selectedTable)
    }

    @Test
    fun `tapping the chosen table again clears it`() = runTest(dispatcher) {
        restaurants.tables = listOf(table("t1", seats = 4))
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })

        vm.toggleTable(restaurants.tables.first())
        assertNotNull(vm.state.value.selectedTable)
        vm.toggleTable(restaurants.tables.first())

        assertNull(vm.state.value.selectedTable)
    }

    @Test
    fun `will not submit before a sitting is chosen`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.state.value.canSubmit)
        vm.submit()
        advanceUntilIdle()

        assertTrue(reservations.created.isEmpty())
    }

    @Test
    fun `writes the booking with the party and the table on it`() = runTest(dispatcher) {
        restaurants.tables = listOf(table("t1", seats = 6, label = "W1"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.setPartySize(5)
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })
        vm.toggleTable(restaurants.tables.first())

        vm.submit()
        advanceUntilIdle()

        assertEquals(1, reservations.created.size)
        val saved = reservations.created.single()
        assertEquals("r1", saved.restaurantId)
        assertEquals(5, saved.partySize)
        assertEquals("t1", saved.tableId)
        assertEquals("W1", saved.tableLabel)
        assertNotNull(vm.state.value.confirmedLabel)
    }

    @Test
    fun `books without a table when the floor is not mapped`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })

        vm.submit()
        advanceUntilIdle()

        assertEquals("", reservations.created.single().tableId)
        assertFalse(vm.state.value.showsTablePlan)
    }

    @Test
    fun `stays put and says why when the booking fails`() = runTest(dispatcher) {
        reservations.createFailure = "That table was taken while you were choosing."
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })

        vm.submit()
        advanceUntilIdle()

        // Nothing was booked, so the screen must not look like it was.
        assertNull(vm.state.value.confirmedLabel)
        assertEquals(
            "That table was taken while you were choosing.",
            vm.state.value.message?.text,
        )
        assertFalse(vm.state.value.isSubmitting)
    }

    @Test
    fun `cannot book twice by submitting again`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.selectSlot(vm.state.value.slots.first { it.isAvailable })

        vm.submit()
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, reservations.created.size)
    }

    @Test
    fun `reports a failure to read availability rather than showing an empty day`() =
        runTest(dispatcher) {
            reservations.slotCountsFailure = "We cannot reach KulaPro right now."

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals("We cannot reach KulaPro right now.", vm.state.value.message?.text)
            assertFalse(vm.state.value.isLoadingSlots)
        }

    @Test
    fun `carries on without a floor plan when the tables cannot be read`() =
        runTest(dispatcher) {
            restaurants.tablesFailure = "offline"

            val vm = viewModel()
            advanceUntilIdle()

            // Not an error the diner needs to see: they are simply seated on arrival.
            assertTrue(vm.state.value.tables.isEmpty())
            assertNull(vm.state.value.message)
        }

    @Test
    fun `holds the party size inside what a restaurant will seat`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setPartySize(0)
        advanceUntilIdle()
        assertEquals(MIN_PARTY_SIZE, vm.state.value.partySize)

        vm.setPartySize(500)
        advanceUntilIdle()
        assertEquals(MAX_PARTY_SIZE, vm.state.value.partySize)
    }

    @Test
    fun `offers a fortnight starting today`() {
        val today = Date()
        val days = upcomingDays(BOOKABLE_DAYS, today)

        assertEquals(BOOKABLE_DAYS, days.size)
        assertEquals(startOfDay(today), days.first())
        val calendar = Calendar.getInstance().apply { time = days.first() }
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        // Strictly increasing, so no day is offered twice or skipped.
        days.zipWithNext { a, b -> assertTrue(b.after(a)) }
    }

    private fun table(id: String, seats: Int, label: String = id) = RestaurantTable(
        id = id,
        restaurantId = "r1",
        label = label,
        seats = seats,
        zone = "Main floor",
    )
}
