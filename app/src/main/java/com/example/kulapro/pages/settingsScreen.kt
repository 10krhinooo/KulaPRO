package com.example.kulapro.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/**
 * Settings.
 *
 * Phase 1 wires the screen and the sign-out action. The theme and notification switches are
 * held in memory only until Phase 3 adds a preferences store to persist them, so they are
 * labelled as taking effect for this session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
) {
    var useDynamicColour by remember { mutableStateOf(false) }
    var remindersEnabled by remember { mutableStateOf(true) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ListItem(
                headlineContent = { Text("Use system colours") },
                supportingContent = { Text("Match your wallpaper instead of the KulaPro palette") },
                trailingContent = {
                    Switch(
                        checked = useDynamicColour,
                        onCheckedChange = { useDynamicColour = it },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Booking reminders") },
                supportingContent = { Text("Get a notification before your table is held") },
                trailingContent = {
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { remindersEnabled = it },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("About KulaPro") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate("about") },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Sign out") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSignOut),
            )
        }
    }
}
