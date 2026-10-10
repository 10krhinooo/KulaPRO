package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import com.example.kulapro.data.model.MenuItem
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
class MenuViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var owner: FakeOwnerRepository
    private lateinit var restaurants: FakeRestaurantRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        owner = FakeOwnerRepository()
        restaurants = FakeRestaurantRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MenuViewModel(
        ownerRepository = owner,
        restaurantRepository = restaurants,
        savedStateHandle = SavedStateHandle(
            mapOf(OwnerBookingsViewModel.ARG_RESTAURANT_ID to "r1"),
        ),
    )

    @Test
    fun `the menu loads on open`() = runTest(dispatcher) {
        restaurants.menuItems = listOf(MenuItem(id = "m1", name = "Ugali"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("Ugali"), viewModel.state.value.items.map { it.name })
    }

    @Test
    fun `a menu that cannot be read shows a way to try again, not a blank list`() =
        runTest(dispatcher) {
            restaurants.menuFailure = "You are offline."

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals("You are offline.", viewModel.state.value.loadError)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `adding a dish saves it and shows it in the list`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addDish()
        viewModel.updateDraft(MenuDraft(name = "Ugali", price = "250"))
        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals(1, owner.menuItems.size)
        assertEquals("Ugali", owner.menuItems.single().name)
        assertEquals(25_000L, owner.menuItems.single().priceCents)
        assertNull(viewModel.state.value.draft)
    }

    @Test
    fun `a dish that does not validate is not written`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addDish()
        viewModel.updateDraft(MenuDraft(name = "", price = "nope"))
        viewModel.saveDraft()
        advanceUntilIdle()

        assertTrue(owner.menuItems.isEmpty())
        // The form stays open, so the person can fix it rather than losing what they typed.
        assertNotNull(viewModel.state.value.draft)
    }

    @Test
    fun `a failed save keeps the form open and says why`() = runTest(dispatcher) {
        owner.saveFailure = "You no longer manage this restaurant."
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addDish()
        viewModel.updateDraft(MenuDraft(name = "Ugali", price = "250"))
        viewModel.saveDraft()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.draft)
        assertEquals(
            "You no longer manage this restaurant.",
            viewModel.state.value.message?.text,
        )
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `editing an existing dish rewrites it rather than adding a second`() =
        runTest(dispatcher) {
            val existing = MenuItem(id = "m1", name = "Ugali", priceCents = 25_000)
            restaurants.menuItems = listOf(existing)
            owner.menuItems += existing

            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.editDish(existing)
            viewModel.updateDraft(MenuDraft.of(existing).copy(price = "300"))
            viewModel.saveDraft()
            advanceUntilIdle()

            assertEquals(1, owner.menuItems.size)
            assertEquals(30_000L, owner.menuItems.single().priceCents)
        }

    @Test
    fun `removing a dish takes it off the list straight away`() = runTest(dispatcher) {
        val existing = MenuItem(id = "m1", name = "Ugali")
        restaurants.menuItems = listOf(existing)
        owner.menuItems += existing

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.deleteDish(existing)
        advanceUntilIdle()

        assertTrue(owner.menuItems.isEmpty())
        assertTrue(viewModel.state.value.items.isEmpty())
        assertNull(viewModel.state.value.deletingId)
    }

    @Test
    fun `a dish that could not be removed stays on the list`() = runTest(dispatcher) {
        val existing = MenuItem(id = "m1", name = "Ugali")
        restaurants.menuItems = listOf(existing)
        owner.deleteFailure = "Check your connection and try again."

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.deleteDish(existing)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertEquals(
            "Check your connection and try again.",
            viewModel.state.value.message?.text,
        )
    }

    @Test
    fun `a new dish is priced in the currency the menu already uses`() = runTest(dispatcher) {
        restaurants.menuItems = listOf(MenuItem(id = "m1", currency = "USD"))

        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addDish()
        viewModel.updateDraft(MenuDraft(name = "Ugali", price = "12"))
        viewModel.saveDraft()
        advanceUntilIdle()

        assertEquals("USD", owner.menuItems.last().currency)
    }

    @Test
    fun `cancelling closes the form without writing anything`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addDish()
        viewModel.updateDraft(MenuDraft(name = "Ugali", price = "250"))
        viewModel.cancelEditing()
        advanceUntilIdle()

        assertNull(viewModel.state.value.draft)
        assertTrue(owner.menuItems.isEmpty())
    }

    @Test
    fun `a message can be dismissed`() = runTest(dispatcher) {
        owner.saveFailure = "No."
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.addDish()
        viewModel.updateDraft(MenuDraft(name = "Ugali", price = "250"))
        viewModel.saveDraft()
        advanceUntilIdle()
        viewModel.dismissMessage()

        assertNull(viewModel.state.value.message)
    }
}
