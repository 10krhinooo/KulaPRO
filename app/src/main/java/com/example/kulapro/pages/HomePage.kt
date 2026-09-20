package com.example.kulapro.pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.kulapro.R
import com.example.kulapro.Routes
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.UserProfile
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.ProfileRepository
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.feature.owner.Portal
import com.example.kulapro.feature.owner.PortalSwitcher
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.rememberListEntryAnimator
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.RestaurantCard
import com.example.kulapro.ui.components.RestaurantCardSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    navController: NavController,
    onOpenRestaurant: (restaurantId: String) -> Unit,
    onSwitchToHosting: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSignedIn: Boolean = false,
    managedRestaurantId: String? = null,
    repository: RestaurantRepository = remember { RestaurantRepositoryFirestore() },
    appContext: android.content.Context = LocalContext.current.applicationContext,
    profileRepository: ProfileRepository = remember { AuthRepositoryFirebase(appContext) },
) {
    var isLoading by remember { mutableStateOf(true) }

    val restaurants by produceState(initialValue = emptyList<Restaurant>(), repository) {
        repository.restaurants().collect {
            value = it
            isLoading = false
        }
    }

    // Greet the user by name once we know it. A guest is never asked who they are, so the
    // heading falls back to the question rather than to an empty "Hello ,".
    val profile by produceState<UserProfile?>(null, isSignedIn) {
        value = null
        if (isSignedIn) profileRepository.profileFlow().collect { value = it }
    }
    val firstName = profile?.displayName.orEmpty().trim().substringBefore(' ')

    // Hoisted above the list, so a card that scrolls away and back does not replay its
    // entrance and appear to load late.
    val entryAnimator = rememberListEntryAnimator()

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_kula_mark),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text("KulaPro", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    // Only rendered when the user's claims name a restaurant, so the control
                    // never promises access the rules would refuse.
                    managedRestaurantId?.let { id ->
                        PortalSwitcher(
                            current = Portal.CUSTOMER,
                            onSwitch = { onSwitchToHosting(id) },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { navController.navigate(Routes.ABOUT) }) {
                        Icon(Icons.Rounded.Info, contentDescription = "About")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (firstName.isNotBlank()) {
                Text(
                    text = "Hello $firstName",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
                Text(
                    text = "Where are you eating?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            } else {
                Text(
                    text = "Where are you eating?",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                )
            }
            Text(
                text = "Real tables, real times, booked in seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )

            when {
                // Skeletons matching the real card, so the layout does not jump on load.
                isLoading -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(PLACEHOLDER_COUNT) { RestaurantCardSkeleton() }
                }

                restaurants.isEmpty() -> EmptyState(
                    title = "No restaurants yet",
                    description = "Once restaurants join KulaPro they will show up here.",
                    icon = Icons.Outlined.Restaurant,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Keyed so a row scrolled out and back is the same row, not a new one
                    // rebuilt from scratch.
                    itemsIndexed(
                        items = restaurants,
                        key = { _, restaurant -> restaurant.id },
                    ) { index, restaurant ->
                        AnimatedListItem(
                            index = index,
                            key = restaurant.id,
                            animator = entryAnimator,
                        ) {
                            RestaurantCard(
                                restaurant = restaurant,
                                onClick = { onOpenRestaurant(restaurant.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val PLACEHOLDER_COUNT = 3
