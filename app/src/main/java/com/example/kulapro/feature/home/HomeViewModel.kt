package com.example.kulapro.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.FavouritesRepository
import com.example.kulapro.data.repository.ProfileRepository
import com.example.kulapro.data.repository.ReservationRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.domain.AvailabilityCalculator
import com.example.kulapro.util.Clock
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Home: the list, what the diner has narrowed it to, and what they have kept.
 *
 * The four sources here are deliberately independent. Restaurants arrive whether or not
 * anyone is signed in, favourites only when someone is, tonight's availability on its own
 * schedule, and the greeting from the profile. Braiding them into one query would mean a
 * guest waited on a read they are not allowed to make.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val reservationRepository: ReservationRepository,
    private val favouritesRepository: FavouritesRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        observeRestaurants()
        observeSession()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun toggleCuisine(cuisine: String) = _state.update {
        it.copy(cuisines = it.cuisines.toggle(cuisine))
    }

    fun togglePriceBand(band: Int) = _state.update {
        it.copy(priceBands = it.priceBands.toggle(band))
    }

    fun toggleOpenNow() = _state.update { it.copy(openNowOnly = !it.openNowOnly) }

    fun toggleFreeTonight() = _state.update { it.copy(freeTonightOnly = !it.freeTonightOnly) }

    fun toggleFavouritesOnly() = _state.update {
        it.copy(favouritesOnly = !it.favouritesOnly)
    }

    /** Clears everything at once, which is what someone who has over-filtered wants. */
    fun clearFilters() = _state.update {
        it.copy(
            query = "",
            cuisines = emptySet(),
            priceBands = emptySet(),
            openNowOnly = false,
            freeTonightOnly = false,
            favouritesOnly = false,
        )
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /**
     * Keeps or releases a restaurant.
     *
     * The list is updated before the write lands, so the heart fills under the finger, and
     * put back if the write fails. Waiting on a round trip to acknowledge a tap makes the
     * app feel broken on a slow connection.
     */
    fun toggleFavourite(restaurant: Restaurant) {
        val current = _state.value
        if (!current.isSignedIn) {
            _state.update { it.copy(message = "Sign in to keep a restaurant.") }
            return
        }

        val wasFavourite = current.isFavourite(restaurant.id)
        _state.update {
            it.copy(favouriteIds = it.favouriteIds.toggle(restaurant.id))
        }

        viewModelScope.launch {
            val result = favouritesRepository.setFavourite(restaurant.id, !wasFavourite)
            if (result is Result.Failure) {
                _state.update {
                    it.copy(
                        favouriteIds = it.favouriteIds.toggle(restaurant.id),
                        message = result.message,
                    )
                }
            }
        }
    }

    private fun observeRestaurants() = viewModelScope.launch {
        restaurantRepository.restaurants().collect { restaurants ->
            _state.update {
                it.copy(
                    restaurants = restaurants,
                    isLoading = false,
                    openNowIds = openNowIds(restaurants, clock.now()),
                )
            }
            refreshTonight(restaurants)
        }
    }

    /**
     * Follows the signed in user, so signing in or out changes the list without a relaunch.
     *
     * Favourites and the greeting both hang off this. A guest gets neither, and gets them
     * the moment they sign in rather than the next time the app starts.
     */
    private fun observeSession() = viewModelScope.launch {
        authRepository.authState().collect { uid ->
            _state.update {
                it.copy(
                    isSignedIn = uid != null,
                    // Dropped rather than kept: leaving the last user's hearts on screen
                    // after a sign out would show one person another's list.
                    favouriteIds = if (uid == null) emptySet() else it.favouriteIds,
                    greetingName = if (uid == null) "" else it.greetingName,
                    favouritesOnly = if (uid == null) false else it.favouritesOnly,
                )
            }
            if (uid == null) return@collect
            observeFavourites()
            observeGreeting()
        }
    }

    private fun observeFavourites() = viewModelScope.launch {
        favouritesRepository.favourites().collect { ids ->
            _state.update { it.copy(favouriteIds = ids) }
        }
    }

    private fun observeGreeting() = viewModelScope.launch {
        profileRepository.profileFlow().collect { profile ->
            _state.update {
                it.copy(
                    greetingName = profile?.displayName.orEmpty().trim().substringBefore(' '),
                )
            }
        }
    }

    /**
     * Who still has a table today, asked once rather than once per restaurant.
     *
     * A failure here is silent. Tonight's availability is a filter, not the list: losing it
     * should narrow what the diner can ask for, never replace the restaurants with an error.
     */
    private suspend fun refreshTonight(restaurants: List<Restaurant>) {
        if (restaurants.isEmpty()) return
        val now = clock.now()
        val counts = reservationRepository.slotCountsForAll(
            from = Timestamp(now),
            to = Timestamp(endOfDay(now)),
        )
        if (counts !is Result.Success) return

        val takenByRestaurant = counts.data
            .groupBy { it.restaurantId }
            .mapValues { (_, slots) -> slots.associate { it.startsAtSeconds to it.seatsTaken } }

        val free = restaurants.filter { restaurant ->
            AvailabilityCalculator.hasTableLaterToday(
                restaurant = restaurant,
                now = now,
                seatsTaken = takenByRestaurant[restaurant.id].orEmpty(),
            )
        }.map { it.id }.toSet()

        _state.update { it.copy(freeTonightIds = free) }
    }
}

private fun openNowIds(restaurants: List<Restaurant>, now: Date): Set<String> =
    restaurants.filter { AvailabilityCalculator.isOpenAt(it, now) }.map { it.id }.toSet()

/** Adds what is missing and removes what is there, which is what every chip here does. */
private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

private fun endOfDay(day: Date): Date = Calendar.getInstance().apply {
    time = day
    set(Calendar.HOUR_OF_DAY, LAST_HOUR)
    set(Calendar.MINUTE, LAST_MINUTE)
    set(Calendar.SECOND, LAST_SECOND)
}.time

private const val LAST_HOUR = 23
private const val LAST_MINUTE = 59
private const val LAST_SECOND = 59
