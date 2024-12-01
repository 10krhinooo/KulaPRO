package com.example.kulapro

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.kulapro.pages.AboutScreen
import com.example.kulapro.pages.ForgotPasswordScreen
import com.example.kulapro.pages.HomePage
import com.example.kulapro.pages.LoginPage
import com.example.kulapro.pages.ProfilePage
import com.example.kulapro.pages.RegisterPage
import com.example.kulapro.pages.ReservationFormScreen
import com.example.kulapro.pages.ReservationScreen
import com.example.kulapro.pages.SettingsScreen

@Composable
fun KulaProNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController() // Initialize the NavController
    var selectedItem by remember { mutableStateOf("home") } // Track selected tab item

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val backStackEntry = navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry.value?.destination?.route
            if (currentRoute != "login" && currentRoute != "register" && currentRoute != "forgot") { // Exclude login and register pages
                BottomNavigationBar(navController = navController, selectedItem = selectedItem) { item ->
                    selectedItem = item
                    navController.navigate(item) {
                        // Avoid stacking the same destination multiple times
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { paddingValues ->
        // Setup NavHost with composable destinations
        NavHost(
            navController = navController,
            startDestination = "login", // Starting screen
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("login") {
                LoginPage(modifier, navController)
            }
            composable("register") {
                RegisterPage(modifier, navController)
            }
            composable("home") {
                HomePage(navController)
            }
            composable("profile") {
                ProfilePage(navController)
            }
            composable("reservationForm") {
                ReservationFormScreen(modifier, navController)
            }
            composable("reservation") {
                ReservationScreen( navController)
            }
            composable("forgot") {
                ForgotPasswordScreen(navController)
            }
            composable("settings") {
                SettingsScreen(param1 = "ExampleParam1", param2 = "ExampleParam2")
            }
            composable("about") {
                AboutScreen(param1 = "ExampleParam1", param2 = "ExampleParam2")
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = selectedItem == "home",
            onClick = { onItemSelected("home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.List, contentDescription = "Reservations") },
            label = { Text("Reservations") },
            selected = selectedItem == "reservation",
            onClick = { onItemSelected("reservation") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "Profile") },
            label = { Text("Profile") },
            selected = selectedItem == "profile",
            onClick = { onItemSelected("profile") }
        )
    }
}
