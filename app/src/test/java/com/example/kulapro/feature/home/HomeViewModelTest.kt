package com.example.kulapro.feature.home

import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.SlotCount
import com.example.kulapro.data.model.UserProfile
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.FavouritesRepository
import com.example.kulapro.data.repository.ProfileRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.feature.booking.FakeReservationRepository
import com.example.kulapro.feature.booking.FakeRestaurantRepository
import com.example.kulapro.util.Clock
import io.mockk.every
import io.mockk.mockk
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

/** Favourites a test can set directly, with no Firestore and no signed in user. */
private class FakeFavouritesRepository(
    initial: Set<String> = emptySet(),
    var failure: String? = null,
) : FavouritesRepository {

    private val state = MutableStateFlow(initial)
    val writes = mutableListOf<Pair<String, Boolean>>()

    override fun favourites(): Flow<Set<String>> = state

    override suspend fun setFavourite(
        restaurantId: String,
        isFavourite: Boolean,
    ): Result<Unit> {
        writes += restaurantId to isFavourite
        failure?.let { return Result.Failure(it) }
        state.value =
            if (isFavourite) state.value + restaurantId else state.value - restaurantId
        return Result.Success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var restaurants: FakeRestaurantRepository
    private lateinit var reservations: FakeReservationRepository
    private lateinit var favourites: FakeFavouritesRepository
    private lateinit var profile: ProfileRepository
    private lateinit var auth: AuthRepository
    private lateinit var signedInUid: MutableStateFlow<String?>

    /**
     * Midday on a Wednesday, fixed.
     *
     * Every one of these assertions is about what is still bookable, and against the wall
     * clock they would pass all afternoon and fail after the evening's last sitting.
     */
    private val noon = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
    private val clock = Clock { noon }

    private val openAllHours = mapOf(
        "monday" to "00:00-23:59", "tuesday" to "00:00-23:59",
        "wednesday" to "00:00-23:59", "thursday" to "00:00-23:59",
        "friday" to "00:00-23:59", "saturday" to "00:00-23:59",
        "sunday" to "00:00-23:59",
    )

    private val bistro = Restaurant(
        id = "bistro",
        name = "The Bistro",
        cuisine = "European",
        capacityPerSlot = 40,
        slotDurationMinutes = 90,
        openingHours = openAllHours,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        restaurants = FakeRestaurantRepository(restaurant = bistro)
        reservations = FakeReservationRepository()
        favourites = FakeFavouritesRepository()
        signedInUid = MutableStateFlow<String?>("user-1")

        profile = mockk(relaxed = true)
        every { profile.profileFlow() } returns flowOf(UserProfile(displayName = "Victor Moruri"))

        auth = mockk(relaxed = true)
        every { auth.authState() } returns signedInUid
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        restaurantRepository = restaurants,
        reservationRepository = reservations,
        favouritesRepository = favourites,
        profileRepository = profile,
        authRepository = auth,
        clock = clock,
    )

    @Test
    fun `the list loads on open`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(listOf("bistro"), viewModel.state.value.restaurants.map { it.id })
    }

    @Test
    fun `the greeting uses the first name only`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals("Victor", viewModel.state.value.greetingName)
    }

    @Test
    fun `a guest is not greeted by name and is not offered the kept filter`() =
        runTest(dispatcher) {
            signedInUid.value = null

            val viewModel = viewModel()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isSignedIn)
            assertEquals("", viewModel.state.value.greetingName)
        }

    @Test
    fun `favourites load for a signed in user`() = runTest(dispatcher) {
        favourites = FakeFavouritesRepository(initial = setOf("bistro"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isFavourite("bistro"))
    }

    @Test
    fun `signing out drops the last user's kept list from the screen`() =
        runTest(dispatcher) {
            favourites = FakeFavouritesRepository(initial = setOf("bistro"))
            val viewModel = viewModel()
            advanceUntilIdle()

            signedInUid.value = null
            advanceUntilIdle()

            // Leaving them on screen would show one person another's list.
            assertTrue(viewModel.state.value.favouriteIds.isEmpty())
            assertFalse(viewModel.state.value.favouritesOnly)
        }

    @Test
    fun `keeping a restaurant writes it and fills the heart`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleFavourite(bistro)
        advanceUntilIdle()

        assertEquals(listOf("bistro" to true), favourites.writes)
        assertTrue(viewModel.state.value.isFavourite("bistro"))
    }

    @Test
    fun `the heart fills before the write lands, so a tap is never waiting on a network`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.toggleFavourite(bistro)

            assertTrue(viewModel.state.value.isFavourite("bistro"))
        }

    @Test
    fun `a write that fails puts the heart back and says why`() = runTest(dispatcher) {
        favourites = FakeFavouritesRepository(failure = "You are offline.")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleFavourite(bistro)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFavourite("bistro"))
        assertEquals("You are offline.", viewModel.state.value.message)
    }

    @Test
    fun `releasing a kept restaurant writes the removal`() = runTest(dispatcher) {
        favourites = FakeFavouritesRepository(initial = setOf("bistro"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleFavourite(bistro)
        advanceUntilIdle()

        assertEquals(listOf("bistro" to false), favourites.writes)
    }

    @Test
    fun `a guest is asked to sign in rather than silently ignored`() = runTest(dispatcher) {
        signedInUid.value = null
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleFavourite(bistro)
        advanceUntilIdle()

        assertTrue(favourites.writes.isEmpty())
        assertEquals("Sign in to keep a restaurant.", viewModel.state.value.message)
    }

    @Test
    fun `a restaurant open right now is marked open`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue("bistro" in viewModel.state.value.openNowIds)
    }

    @Test
    fun `a restaurant closed today is not marked open`() = runTest(dispatcher) {
        restaurants.restaurant = bistro.copy(openingHours = emptyMap())

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.openNowIds.isEmpty())
    }

    @Test
    fun `a restaurant with seats left later today has a table tonight`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            assertTrue("bistro" in viewModel.state.value.freeTonightIds)
        }

    @Test
    fun `a restaurant sold out for every remaining sitting does not`() = runTest(dispatcher) {
        // Every sitting from now to midnight filled to capacity.
        reservations.slotCounts = soldOutSlotsForToday(bistro)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.freeTonightIds.isEmpty())
    }

    @Test
    fun `losing tonight's availability does not lose the restaurants`() =
        runTest(dispatcher) {
            reservations.slotCountsFailure = "You are offline."

            val viewModel = viewModel()
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.restaurants.size)
            assertTrue(viewModel.state.value.freeTonightIds.isEmpty())
            // A filter failing is not a reason to shout at someone browsing a list.
            assertNull(viewModel.state.value.message)
        }

    @Test
    fun `a search is held in state so the list narrows as it is typed`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            viewModel.setQuery("bistro")

            assertEquals(1, viewModel.state.value.visible.size)
        }

    @Test
    fun `a chip tapped twice is a chip turned off`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.toggleCuisine("European")
        viewModel.toggleCuisine("European")

        assertTrue(viewModel.state.value.cuisines.isEmpty())
    }

    @Test
    fun `every filter can be turned on and off`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.togglePriceBand(2)
        viewModel.toggleOpenNow()
        viewModel.toggleFreeTonight()
        viewModel.toggleFavouritesOnly()

        val state = viewModel.state.value
        assertEquals(setOf(2), state.priceBands)
        assertTrue(state.openNowOnly)
        assertTrue(state.freeTonightOnly)
        assertTrue(state.favouritesOnly)
    }

    @Test
    fun `clearing filters clears all of them at once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.setQuery("bistro")
        viewModel.toggleCuisine("European")
        viewModel.togglePriceBand(2)
        viewModel.toggleOpenNow()
        viewModel.toggleFreeTonight()
        viewModel.toggleFavouritesOnly()

        viewModel.clearFilters()

        assertFalse(viewModel.state.value.hasFilters)
    }

    @Test
    fun `a message can be dismissed`() = runTest(dispatcher) {
        signedInUid.value = null
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.toggleFavourite(bistro)
        advanceUntilIdle()

        viewModel.dismissMessage()

        assertNull(viewModel.state.value.message)
    }

    /** Every sitting from now until midnight, filled to the restaurant's capacity. */
    private fun soldOutSlotsForToday(restaurant: Restaurant): Map<Long, SlotCount> {
        val calendar = Calendar.getInstance().apply {
            time = noon
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = Calendar.getInstance().apply {
            time = noon
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis

        val counts = mutableMapOf<Long, SlotCount>()
        // Every minute rather than every sitting, so the map covers whichever starts the
        // calculator generates without this test having to reimplement it.
        while (calendar.timeInMillis <= endOfDay) {
            val seconds = calendar.timeInMillis / 1000
            counts[seconds] = SlotCount(
                restaurantId = restaurant.id,
                startsAtSeconds = seconds,
                seatsTaken = restaurant.capacityPerSlot,
            )
            calendar.add(Calendar.MINUTE, 1)
        }
        return counts
    }
}
