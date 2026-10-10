package com.example.kulapro.feature.admin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The two jobs of running the platform.
 *
 * Deciding who may manage a restaurant, and seeing whether the service is working. Neither
 * is about any one restaurant, which is why they are here and not on the restaurant portal's
 * bar: a platform admin who also runs a restaurant has two different jobs and two different
 * views of the app, and mixing them made both harder to read.
 */
enum class AdminSection(val label: String, val icon: ImageVector) {
    REQUESTS("Requests", Icons.Outlined.Inbox),
    METRICS("System", Icons.Outlined.QueryStats),
}
