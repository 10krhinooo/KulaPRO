package com.example.kulapro.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.kulapro.Routes
import com.example.kulapro.data.settings.ThemeMode
import com.example.kulapro.feature.settings.SettingsViewModel
import com.example.kulapro.ui.components.SecondaryButton

/**
 * Settings that are actually settings.
 *
 * Every control here changes something and survives the app closing. The first version held
 * three switches in local state that were discarded the moment the screen was, which is
 * worse than having no settings at all: a control that lies teaches people to distrust the
 * ones that do not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    onSignOut: () -> Unit = {},
    isSignedIn: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        // Insets are owned by the navigation Scaffold; applying them again here would
        // double count the navigation bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                // Settings sits outside the tabs, so the bottom bar is hidden here and this
                // is the only way back other than the system gesture.
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Appearance")

            ThemeModeChooser(
                selected = settings.themeMode,
                onSelect = viewModel::setThemeMode,
            )

            ListItem(
                headlineContent = { Text("Use system colours") },
                supportingContent = {
                    Text("Take the palette from your wallpaper instead of KulaPro's")
                },
                trailingContent = {
                    Switch(
                        checked = settings.useDynamicColour,
                        onCheckedChange = viewModel::setDynamicColour,
                    )
                },
            )
            HorizontalDivider()

            SectionLabel("Reminders")

            ListItem(
                headlineContent = { Text("Booking reminders") },
                supportingContent = {
                    Text("A nudge on this device before a table you have booked")
                },
                trailingContent = {
                    Switch(
                        checked = settings.remindersEnabled,
                        onCheckedChange = viewModel::setRemindersEnabled,
                    )
                },
            )

            if (settings.remindersEnabled) {
                ReminderLeadChooser(
                    selected = settings.reminderLeadHours,
                    onSelect = viewModel::setReminderLeadHours,
                )
            }
            HorizontalDivider()

            SectionLabel("About")

            ListItem(
                headlineContent = { Text("About KulaPro") },
                supportingContent = { Text("Version, and what this app is for") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(Routes.ABOUT) },
            )
            HorizontalDivider()

            // Nothing to sign out of as a guest, and offering it would imply there is.
            if (isSignedIn) {
                SecondaryButton(
                    text = "Sign out",
                    onClick = onSignOut,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ThemeModeChooser(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
    }
}

@Composable
private fun ReminderLeadChooser(selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Remind me", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LEAD_CHOICES.forEach { hours ->
                FilterChip(
                    selected = hours == selected,
                    onClick = { onSelect(hours) },
                    label = { Text(if (hours == 1) "1 hour before" else "$hours hours before") },
                )
            }
        }
    }
}

/** Applies to bookings made from now on, since a reminder is scheduled when one is made. */
private val LEAD_CHOICES = listOf(1, 3, 24)
