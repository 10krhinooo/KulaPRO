package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.feature.booking.FakeRestaurantRepository
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
class CapacityViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var owner: FakeOwnerRepository
    private lateinit var restaurants: FakeRestaurantRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        owner = FakeOwnerRepository()
        restaurants = FakeRestaurantRepository(
            restaurant = Restaurant(
                id = "r1",
                name = "The Bistro",
                capacityPerSlot = 40,
                slotDurationMinutes = 120,
                openingHours = mapOf("monday" to "12:00-22:00"),
            ),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CapacityViewModel(
        ownerRepository = owner,
        restaurantRepository = restaurants,
        savedStateHandle = SavedStateHandle(
            mapOf(OwnerBookingsViewModel.ARG_RESTAURANT_ID to "r1"),
        ),
    )

    @Test
    fun `what is already stored comes back into the form`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("The Bistro", state.restaurantName)
        assertEquals("40", state.seatsPerSitting)
        assertEquals(120, state.sittingMinutes)
        assertEquals(1, state.openDays)
        assertTrue(state.days.first { it.key == "monday" }.isOpen)
        assertFalse(state.isLoading)
    }

    @Test
    fun `a restaurant that will not load offers a retry rather than an empty form`() =
        runTest(dispatcher) {
            restaurants.restaurantFailure = "You are offline."

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals("You are offline.", viewModel.state.value.loadError)
        }

    @Test
    fun `a restaurant with no stored sitting length falls back to the default`() =
        runTest(dispatcher) {
            restaurants.restaurant = Restaurant(id = "r1", slotDurationMinutes = 0)

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(DEFAULT_SITTING_MINUTES, viewModel.state.value.sittingMinutes)
        }

    @Test
    fun `saving writes the seats, the sitting length and the open days`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setSeatsPerSitting("60")
            viewModel.setSittingMinutes(90)
            viewModel.setDay(
                DayHours("tuesday", isOpen = true, opens = "17:00", closes = "23:00"),
            )
            viewModel.save()
            advanceUntilIdle()

            val saved = owner.seating
            assertNotNull(saved)
            assertEquals(60, saved?.capacityPerSlot)
            assertEquals(90, saved?.slotDurationMinutes)
            assertEquals("12:00-22:00", saved?.openingHours?.get("monday"))
            assertEquals("17:00-23:00", saved?.openingHours?.get("tuesday"))
        }

    @Test
    fun `a closed day is left out entirely, which is what reads as shut`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setDay(DayHours("monday", isOpen = false))
            viewModel.save()
            advanceUntilIdle()

            assertTrue(owner.seating?.openingHours.orEmpty().isEmpty())
        }

    @Test
    fun `a half typed closing time is not saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setDay(DayHours("monday", isOpen = true, opens = "12:00", closes = "2"))
        viewModel.save()
        advanceUntilIdle()

        assertNull(owner.seating)
    }

    @Test
    fun `seats are kept to digits as they are typed`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.setSeatsPerSitting("4a0 ")

        assertEquals("40", viewModel.state.value.seatsPerSitting)
    }

    @Test
    fun `a save that is refused says so and does not claim success`() = runTest(dispatcher) {
        owner.saveFailure = "You no longer manage this restaurant."
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertEquals(
            "You no longer manage this restaurant.",
            viewModel.state.value.message?.text,
        )
        assertTrue(viewModel.state.value.message?.isError == true)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `tables load alongside the rest of the setup`() = runTest(dispatcher) {
        restaurants.tables = listOf(RestaurantTable(id = "t1", label = "T1", seats = 4))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.tables.size)
        assertEquals(4, viewModel.state.value.seatsOnTheFloor)
    }

    @Test
    fun `a floor plan that will not load does not take the whole screen down with it`() =
        runTest(dispatcher) {
            restaurants.tablesFailure = "Check your connection."

            val viewModel = viewModel()
            advanceUntilIdle()

            assertNull(viewModel.state.value.loadError)
            assertEquals("Check your connection.", viewModel.state.value.message?.text)
            assertEquals("40", viewModel.state.value.seatsPerSitting)
        }

    @Test
    fun `adding a table saves it and closes the form`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addTable()
        viewModel.updateTableDraft(TableDraft(label = "T7", seats = "4", zone = "Terrace"))
        viewModel.saveTable()
        advanceUntilIdle()

        assertEquals(1, owner.tables.size)
        assertEquals("T7", owner.tables.single().label)
        assertEquals("r1", owner.tables.single().restaurantId)
        assertNull(viewModel.state.value.tableDraft)
    }

    @Test
    fun `a table that does not validate is not written`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addTable()
        viewModel.updateTableDraft(TableDraft(label = "", seats = "0"))
        viewModel.saveTable()
        advanceUntilIdle()

        assertTrue(owner.tables.isEmpty())
        assertNotNull(viewModel.state.value.tableDraft)
    }

    @Test
    fun `editing a table rewrites it rather than adding another`() = runTest(dispatcher) {
        val existing = RestaurantTable(id = "t1", label = "T1", seats = 2)
        restaurants.tables = listOf(existing)
        owner.tables += existing

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.editTable(existing)
        viewModel.updateTableDraft(TableDraft.of(existing).copy(seats = "6"))
        viewModel.saveTable()
        advanceUntilIdle()

        assertEquals(1, owner.tables.size)
        assertEquals(6, owner.tables.single().seats)
    }

    @Test
    fun `removing a table takes it off the plan straight away`() = runTest(dispatcher) {
        val existing = RestaurantTable(id = "t1", label = "T1", seats = 2)
        restaurants.tables = listOf(existing)
        owner.tables += existing

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.deleteTable(existing)
        advanceUntilIdle()

        assertTrue(owner.tables.isEmpty())
        assertTrue(viewModel.state.value.tables.isEmpty())
        assertNull(viewModel.state.value.deletingTableId)
    }

    @Test
    fun `a table that could not be removed stays on the plan`() = runTest(dispatcher) {
        val existing = RestaurantTable(id = "t1", label = "T1", seats = 2)
        restaurants.tables = listOf(existing)
        owner.deleteFailure = "Check your connection."

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.deleteTable(existing)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.tables.size)
        assertEquals("Check your connection.", viewModel.state.value.message?.text)
    }

    @Test
    fun `a table that could not be saved keeps the form open and says why`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            owner.saveFailure = "You no longer manage this restaurant."
            viewModel.addTable()
            viewModel.updateTableDraft(TableDraft(label = "T7", seats = "4"))
            viewModel.saveTable()
            advanceUntilIdle()

            assertNotNull(viewModel.state.value.tableDraft)
            assertEquals(
                "You no longer manage this restaurant.",
                viewModel.state.value.message?.text,
            )
            assertFalse(viewModel.state.value.isSaving)
        }

    @Test
    fun `cancelling the table form writes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addTable()
        viewModel.updateTableDraft(TableDraft(label = "T7", seats = "4"))
        viewModel.cancelTableEditing()
        advanceUntilIdle()

        assertNull(viewModel.state.value.tableDraft)
        assertTrue(owner.tables.isEmpty())
    }

    @Test
    fun `a message can be dismissed`() = runTest(dispatcher) {
        restaurants.tablesFailure = "No."
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertNull(viewModel.state.value.message)
    }
}
