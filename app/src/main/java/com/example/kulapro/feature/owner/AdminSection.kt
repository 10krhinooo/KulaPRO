package com.example.kulapro.feature.owner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Inbox
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
 */
enum class AdminSection(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Outlined.Insights),
    BOOKINGS("Bookings", Icons.AutoMirrored.Outlined.EventNote),
    MENU("Menu", Icons.Outlined.RestaurantMenu),
    SETUP("Setup", Icons.Outlined.TableRestaurant),

    /** Platform reviewers only, and filtered out of the bar for everyone else. */
    REQUESTS("Requests", Icons.Outlined.Inbox),
}
