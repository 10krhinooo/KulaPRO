package com.example.kulapro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One of the three places a diner moves between.
 *
 * Named here rather than inside the navigation graph because each of the three screens now
 * draws the bar itself. The bar used to sit on the navigation scaffold, where it stayed put
 * while a full screen destination slid over the top, so for the length of the transition
 * the diner's bar and the restaurant portal's were both on screen.
 */
data class NavigationTab(val route: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    NavigationTab(Routes.HOME, "Home", Icons.Filled.Home),
    NavigationTab(Routes.RESERVATIONS, "Reservations", Icons.AutoMirrored.Filled.List),
    NavigationTab(Routes.PROFILE, "Profile", Icons.Filled.AccountCircle),
)
