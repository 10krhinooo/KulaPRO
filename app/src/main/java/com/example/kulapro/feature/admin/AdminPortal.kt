package com.example.kulapro.feature.admin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.kulapro.feature.ownership.OwnershipReviewScreen

/**
 * The platform side of the app.
 *
 * Its own view rather than an extra tab on the restaurant portal, because running the
 * platform and running a restaurant are different jobs. Someone who does both switches
 * between two views on Profile instead of reading one bar that mixes their restaurant's
 * bookings with everyone's ownership requests.
 *
 * Which section is open is remembered here rather than in a view model: neither section
 * needs anything fetched before it can be named, and a saveable survives a rotation, which
 * is the only thing that was ever going to lose it.
 */
@Composable
fun AdminPortal(onSwitchToCustomer: () -> Unit, modifier: Modifier = Modifier) {
    // Opens on requests: that is the one thing here with somebody waiting at the other end.
    var section by rememberSaveable { mutableStateOf(AdminSection.REQUESTS) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                AdminSection.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == section,
                        onClick = { section = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
                // The way back to browsing, always present, so the system back button is
                // never the only exit.
                NavigationBarItem(
                    selected = false,
                    onClick = onSwitchToCustomer,
                    icon = { Icon(Icons.Outlined.TravelExplore, contentDescription = null) },
                    label = { Text("Booking") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (section) {
                AdminSection.REQUESTS -> OwnershipReviewScreen()
                AdminSection.METRICS -> SystemMetricsScreen()
            }
        }
    }
}
