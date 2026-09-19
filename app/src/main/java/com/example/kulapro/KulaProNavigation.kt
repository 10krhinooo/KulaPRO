package com.example.kulapro

import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
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
import com.example.kulapro.data.repository.Result
import com.example.kulapro.feature.owner.OwnerPortal
import com.example.kulapro.pages.AboutScreen
import com.example.kulapro.pages.ForgotPasswordScreen
import com.example.kulapro.pages.HomePage
import com.example.kulapro.pages.LoginPage
import com.example.kulapro.pages.ProfilePage
import com.example.kulapro.pages.RegisterPage
import com.example.kulapro.pages.ReservationFormScreen
import com.example.kulapro.pages.ReservationScreen
import com.example.kulapro.pages.SettingsScreen
import com.example.kulapro.ui.theme.LocalReduceMotion
import com.example.kulapro.ui.theme.Motion
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
    appContext: android.content.Context = LocalContext.current.applicationContext,
    authRepository: AuthRepository = remember { AuthRepositoryFirebase(appContext) },
) {
    // Everyone starts on Home, signed in or not. Browsing restaurants needs no account, so
    // demanding one up front only costs the user a reason to leave.
    val startDestination = Routes.HOME

    val navDurationMillis = if (LocalReduceMotion.current) 0 else Motion.DURATION_MEDIUM

    // Ownership comes from signed Auth claims, refreshed when the signed-in user changes so
    // a newly granted restaurant appears without reinstalling the app.
    val signedInUserId = authRepository.currentUserId
    var managedRestaurants by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(signedInUserId) {
        managedRestaurants = when (val result = authRepository.managedRestaurantIds(true)) {
            is Result.Success -> result.data
            is Result.Failure -> emptyList()
        }
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
            // Defined once here so every destination moves the same way, rather than each
            // screen inventing its own transition.
            enterTransition = {
                slideInHorizontally(
                    animationSpec = tween(navDurationMillis, easing = Motion.EnterEasing),
                    initialOffsetX = { it / 5 },
                ) + fadeIn(animationSpec = tween(navDurationMillis, easing = Motion.EnterEasing))
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(navDurationMillis, easing = Motion.ExitEasing),
                    targetOffsetX = { -it / 5 },
                ) + fadeOut(animationSpec = tween(navDurationMillis, easing = Motion.ExitEasing))
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(navDurationMillis, easing = Motion.EnterEasing),
                    initialOffsetX = { -it / 5 },
                ) + fadeIn(animationSpec = tween(navDurationMillis, easing = Motion.EnterEasing))
            },
            popExitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(navDurationMillis, easing = Motion.ExitEasing),
                    targetOffsetX = { it / 5 },
                ) + fadeOut(animationSpec = tween(navDurationMillis, easing = Motion.ExitEasing))
            },
        ) {
            composable(
                route = Routes.LOGIN,
                arguments = listOf(
                    navArgument("next") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val next = entry.arguments?.getString("next")
                    ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                LoginPage(
                    navController = navController,
                    onSignedIn = {
                        if (next != null) {
                            // Hand the user back to the thing they were trying to do,
                            // replacing the sign-in screen so Back does not return to it.
                            navController.navigate(next) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable(Routes.REGISTER) { RegisterPage(navController = navController) }
            composable(Routes.FORGOT) { ForgotPasswordScreen(navController = navController) }
            composable(Routes.HOME) {
                HomePage(
                    navController = navController,
                    managedRestaurantId = managedRestaurants.firstOrNull(),
                    onSwitchToHosting = { restaurantId ->
                        navController.navigate(Routes.owner(restaurantId))
                    },
                    onBook = { restaurantId, restaurantName ->
                        val destination = Routes.reservationForm(restaurantId, restaurantName)
                        if (authRepository.currentUserId == null) {
                            navController.navigate(Routes.login(next = destination))
                        } else {
                            navController.navigate(destination)
                        }
                    },
                )
            }
            composable(Routes.RESERVATIONS) {
                ReservationScreen(
                    isSignedIn = authRepository.currentUserId != null,
                    onSignIn = {
                        navController.navigate(Routes.login(next = Routes.RESERVATIONS))
                    },
                )
            }
            composable(Routes.PROFILE) {
                ProfilePage(
                    navController = navController,
                    onSignIn = { navController.navigate(Routes.login(next = Routes.PROFILE)) },
                )
            }
            composable(Routes.ABOUT) { AboutScreen() }

            composable(
                route = Routes.OWNER,
                arguments = listOf(navArgument("restaurantId") { type = NavType.StringType }),
            ) { entry ->
                OwnerPortal(
                    restaurantId = entry.arguments?.getString("restaurantId").orEmpty(),
                    onSwitchToCustomer = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    },
                )
            }

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
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            // A small spring on the selected icon makes the bar feel responsive without
            // moving anything the user has to wait for.
            val scale by animateFloatAsState(
                targetValue = if (selected && !LocalReduceMotion.current) 1.15f else 1f,
                animationSpec = Motion.bouncy(),
                label = "tabIconScale",
            )
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.scale(scale),
                    )
                },
                label = { Text(tab.label) },
                selected = selected,
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
