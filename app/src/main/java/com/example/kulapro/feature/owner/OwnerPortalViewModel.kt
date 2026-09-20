package com.example.kulapro.feature.owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kulapro.data.model.Reservation
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.repository.ClaimsRepository
import com.example.kulapro.data.repository.OwnerRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.feature.booking.startOfDay
import com.example.kulapro.ui.components.UiMessage
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the admin portal needs before it can decide which sections to offer. */
data class OwnerPortalUiState(
    val section: AdminSection = AdminSection.TODAY,
    val restaurant: Restaurant? = null,
    val todaysBookings: List<Reservation> = emptyList(),
    val isReviewer: Boolean = false,
    /** Whether this portal was opened for a restaurant at all. */
    val hostsARestaurant: Boolean = true,
    val isEditingListing: Boolean = false,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val message: UiMessage? = null,
) {
    /**
     * The sections this user may actually open.
     *
     * Reviewing requests is a platform role rather than a restaurant one, so it appears only
     * for the handful of accounts that hold it. Showing a tab the rules would refuse is a
     * worse experience than not showing it at all.
     */
    val sections: List<AdminSection>
        get() = AdminSection.entries.filter { section ->
            when (section) {
                AdminSection.REQUESTS -> isReviewer
                else -> hostsARestaurant
            }
        }
}

/**
 * The restaurant side of the app, in one place.
 *
 * Reached through the portal switcher, and offered only to users whose Auth claims name a
 * restaurant, so a diner never sees a door they cannot open.
 */
@HiltViewModel
class OwnerPortalViewModel @Inject constructor(
    private val restaurantRepository: RestaurantRepository,
    private val ownerRepository: OwnerRepository,
    private val claimsRepository: ClaimsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val restaurantId: String =
        savedStateHandle.get<String>(OwnerBookingsViewModel.ARG_RESTAURANT_ID).orEmpty()

    private val _state = MutableStateFlow(
        OwnerPortalUiState(
            hostsARestaurant = restaurantId.isNotBlank(),
            // A reviewer who hosts nothing opens straight onto the only thing they can do
            // here, rather than onto a dashboard for a restaurant that does not exist.
            section = if (restaurantId.isBlank()) AdminSection.REQUESTS else AdminSection.TODAY,
        ),
    )
    val state: StateFlow<OwnerPortalUiState> = _state.asStateFlow()

    init {
        if (restaurantId.isNotBlank()) refresh()
        loadClaims()
    }

    fun selectSection(section: AdminSection) =
        _state.update { it.copy(section = section, isEditingListing = false) }

    fun editListing() = _state.update { it.copy(isEditingListing = true) }

    fun stopEditingListing() = _state.update { it.copy(isEditingListing = false) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun refresh() {
        _state.update { it.copy(loadError = null) }
        viewModelScope.launch {
            when (val result = restaurantRepository.restaurant(restaurantId)) {
                is Result.Success -> _state.update { it.copy(restaurant = result.data) }
                is Result.Failure -> _state.update { it.copy(loadError = result.message) }
            }
            loadToday()
        }
    }

    private suspend fun loadToday() {
        val dayStart = startOfDay(Date())
        when (
            val result = ownerRepository.bookingsFor(
                restaurantId = restaurantId,
                from = Timestamp(dayStart),
                to = Timestamp(Date(dayStart.time + DAY_MILLIS)),
            )
        ) {
            is Result.Success -> _state.update { it.copy(todaysBookings = result.data) }
            // The dashboard still has a restaurant to show, so this is a message rather than
            // an error screen: losing today's numbers should not lose the whole portal.
            is Result.Failure -> _state.update {
                it.copy(message = UiMessage.error(result.message))
            }
        }
    }

    private fun loadClaims() = viewModelScope.launch {
        val isReviewer = when (val result = claimsRepository.isPlatformAdmin()) {
            is Result.Success -> result.data
            is Result.Failure -> false
        }
        _state.update { it.copy(isReviewer = isReviewer) }
    }

    fun saveListing(restaurant: Restaurant) {
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = ownerRepository.updateRestaurant(restaurant)
            _state.update {
                when (result) {
                    is Result.Success -> it.copy(
                        restaurant = restaurant,
                        isEditingListing = false,
                        isSaving = false,
                        message = UiMessage.success("Listing updated"),
                    )

                    is Result.Failure -> it.copy(
                        isSaving = false,
                        message = UiMessage.error(result.message),
                    )
                }
            }
        }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
