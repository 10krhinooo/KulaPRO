package com.example.kulapro.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.kulapro.Routes
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.feature.owner.Portal
import com.example.kulapro.feature.owner.PortalSwitcher
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.RestaurantCard
import com.example.kulapro.ui.components.RestaurantCardSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    navController: NavController,
    onBook: (restaurantId: String, restaurantName: String) -> Unit,
    onSwitchToHosting: (String) -> Unit,
    modifier: Modifier = Modifier,
    managedRestaurantId: String? = null,
    repository: RestaurantRepository = remember { RestaurantRepositoryFirestore() },
) {
    var isLoading by remember { mutableStateOf(true) }

    val restaurants by produceState(initialValue = emptyList<Restaurant>(), repository) {
        repository.restaurants().collect {
            value = it
            isLoading = false
        }
    }

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("KulaPro", style = MaterialTheme.typography.titleLarge) },
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
            Text(
                text = "Where are you eating?",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
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
                    itemsIndexed(restaurants) { index, restaurant ->
                        AnimatedListItem(index = index) {
                            RestaurantCard(
                                restaurant = restaurant,
                                onClick = { onBook(restaurant.id, restaurant.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val PLACEHOLDER_COUNT = 3
