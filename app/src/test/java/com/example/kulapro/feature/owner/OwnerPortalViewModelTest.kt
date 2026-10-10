package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.ReservationStatus
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.feature.booking.FakeRestaurantRepository
import com.google.firebase.Timestamp
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnerPortalViewModelTest {

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

    private fun viewModel(restaurantId: String = "r1") = OwnerPortalViewModel(
        restaurantRepository = restaurants,
        ownerRepository = owner,
        savedStateHandle = SavedStateHandle(
            mapOf(OwnerBookingsViewModel.ARG_RESTAURANT_ID to restaurantId),
        ),
    )

    @Test
    fun `an owner opens on today, with their restaurant loaded`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(OwnerSection.TODAY, viewModel.state.value.section)
        assertEquals("The Bistro", viewModel.state.value.restaurant?.name)
    }

    @Test
    fun `today's bookings are loaded for the dashboard`() = runTest(dispatcher) {
        owner.bookings += Reservation(
            id = "b1",
            restaurantId = "r1",
            partySize = 4,
            startsAt = Timestamp(Date()),
            status = ReservationStatus.CONFIRMED.name,
        )

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.todaysBookings.size)
    }

    @Test
    fun `losing today's numbers does not lose the whole portal`() = runTest(dispatcher) {
        owner.bookingsFailure = "Check your connection."

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("The Bistro", viewModel.state.value.restaurant?.name)
        assertNull(viewModel.state.value.loadError)
        assertEquals("Check your connection.", viewModel.state.value.message?.text)
    }

    @Test
    fun `a restaurant that will not load offers a retry`() = runTest(dispatcher) {
        restaurants.restaurantFailure = "You are offline."

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("You are offline.", viewModel.state.value.loadError)
    }

    @Test
    fun `choosing a section closes the listing editor`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.editListing()
        viewModel.selectSection(OwnerSection.MENU)

        assertEquals(OwnerSection.MENU, viewModel.state.value.section)
        assertFalse(viewModel.state.value.isEditingListing)
    }

    @Test
    fun `leaving the listing editor returns to the section behind it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.editListing()
        assertTrue(viewModel.state.value.isEditingListing)

        viewModel.stopEditingListing()
        assertFalse(viewModel.state.value.isEditingListing)
    }

    @Test
    fun `saving the listing shows the new details without a reload`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.editListing()
        viewModel.saveListing(Restaurant(id = "r1", name = "The Bistro Nairobi"))
        advanceUntilIdle()

        assertEquals("The Bistro Nairobi", viewModel.state.value.restaurant?.name)
        assertFalse(viewModel.state.value.isEditingListing)
        assertFalse(viewModel.state.value.isSaving)
        assertEquals("Listing updated", viewModel.state.value.message?.text)
    }

    @Test
    fun `a listing that cannot be saved keeps the editor open and says why`() =
        runTest(dispatcher) {
            owner.saveFailure = "You no longer manage this restaurant."
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.editListing()
            viewModel.saveListing(Restaurant(id = "r1", name = "Renamed"))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.isEditingListing)
            assertEquals("The Bistro", viewModel.state.value.restaurant?.name)
            assertEquals(
                "You no longer manage this restaurant.",
                viewModel.state.value.message?.text,
            )
            assertFalse(viewModel.state.value.isSaving)
        }

    @Test
    fun `a message can be dismissed`() = runTest(dispatcher) {
        owner.bookingsFailure = "No."
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertNull(viewModel.state.value.message)
    }
}
