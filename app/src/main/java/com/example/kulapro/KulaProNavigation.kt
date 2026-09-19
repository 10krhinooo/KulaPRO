package com.example.kulapro

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.pages.AboutScreen
import com.example.kulapro.pages.ForgotPasswordScreen
import com.example.kulapro.pages.HomePage
import com.example.kulapro.pages.LoginPage
import com.example.kulapro.pages.ProfilePage
import com.example.kulapro.pages.RegisterPage
import com.example.kulapro.pages.ReservationFormScreen
import com.example.kulapro.pages.ReservationScreen
import com.example.kulapro.pages.SettingsScreen
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.RESERVATIONS, "Reservations", Icons.AutoMirrored.Filled.List),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.AccountCircle),
)

@Composable
fun KulaProNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository = remember { AuthRepositoryFirebase() },
) {
    // Start where the user actually is. The first version always began at the login screen,
    // so a signed-in user had to re-authenticate on every launch.
    val startDestination = remember {
        if (authRepository.currentUserId != null) Routes.HOME else Routes.LOGIN
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            // Derived from the back stack rather than held in local state, so the highlighted
            // tab can never drift from the screen actually being shown.
            if (tabs.any { it.route == currentRoute }) {
                BottomNavigationBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.LOGIN) { LoginPage(navController = navController) }
            composable(Routes.REGISTER) { RegisterPage(navController = navController) }
            composable(Routes.FORGOT) { ForgotPasswordScreen(navController = navController) }
            composable(Routes.HOME) { HomePage(navController = navController) }
            composable(Routes.RESERVATIONS) { ReservationScreen() }
            composable(Routes.PROFILE) { ProfilePage(navController = navController) }
            composable(Routes.ABOUT) { AboutScreen() }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    navController = navController,
                    onSignOut = {
                        authRepository.signOut()
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = Routes.RESERVATION_FORM,
                arguments = listOf(
                    navArgument("restaurantId") { type = NavType.StringType },
                    navArgument("restaurantName") { type = NavType.StringType },
                ),
            ) { entry ->
                // The restaurant travels with the route. The first version dropped it and
                // hardcoded every booking to "The Bistro".
                val restaurantId = entry.arguments?.getString("restaurantId").orEmpty()
                val encodedName = entry.arguments?.getString("restaurantName").orEmpty()
                ReservationFormScreen(
                    navController = navController,
                    restaurantId = restaurantId,
                    restaurantName = URLDecoder.decode(
                        encodedName,
                        StandardCharsets.UTF_8.name(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun BottomNavigationBar(navController: NavController, currentRoute: String?) {
    NavigationBar {
        tabs.forEach { tab ->
            NavigationBarItem(
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute == tab.route) return@NavigationBarItem
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}
