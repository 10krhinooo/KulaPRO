package com.example.kulapro.feature.owner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The working areas of the restaurant side.
 *
 * Named for what the person doing the job would call them rather than after the data they
 * happen to read. An owner opening the app is looking for "tonight", not for "reservations
 * filtered by date".
 *
 * Everything here is about one restaurant. Running the platform is a different job done by
 * different people, and it has its own console rather than an extra tab on this bar.
 */
enum class OwnerSection(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Insights),
    BOOKINGS("Bookings", Icons.AutoMirrored.Outlined.EventNote),
    MENU("Menu", Icons.Outlined.RestaurantMenu),
    SETUP("Setup", Icons.Outlined.TableRestaurant),
}
