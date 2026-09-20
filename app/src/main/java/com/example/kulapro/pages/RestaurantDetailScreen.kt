package com.example.kulapro.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kulapro.data.model.MenuItem
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.data.model.Review
import com.example.kulapro.data.repository.RestaurantRepository
import com.example.kulapro.data.repository.RestaurantRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.data.repository.ReviewRepository
import com.example.kulapro.data.repository.ReviewRepositoryFirestore
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.RatingBar
import com.example.kulapro.ui.components.SecondaryButton
import com.example.kulapro.ui.components.ShimmerBox
import com.example.kulapro.ui.components.cuisinePhoto
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.util.openDialer
import com.example.kulapro.util.openMapPin
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Everything a diner wants before committing to a table.
 *
 * Tapping a card used to drop the user straight into a booking form, which asked them to
 * pick a time for a restaurant they had seen one photo of. This screen is the step that was
 * missing: what the place looks like, what it serves, what it costs, and what other diners
 * made of it, with booking as the action at the end rather than the only thing on offer.
 */
@Composable
fun RestaurantDetailScreen(
    restaurantId: String,
    onBack: () -> Unit,
    onBook: (restaurantId: String, restaurantName: String) -> Unit,
    onReview: () -> Unit,
    onClaim: (restaurantId: String, restaurantName: String) -> Unit,
    modifier: Modifier = Modifier,
    isSignedIn: Boolean = false,
    restaurantRepository: RestaurantRepository = remember { RestaurantRepositoryFirestore() },
    reviewRepository: ReviewRepository = remember { ReviewRepositoryFirestore() },
) {
    val messages = rememberMessageHostState()
    var restaurant by remember { mutableStateOf<Restaurant?>(null) }
    var menu by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(restaurantId) {
        isLoading = true
        when (val result = restaurantRepository.restaurant(restaurantId)) {
            is Result.Success -> restaurant = result.data
            is Result.Failure -> messages.showError(result.message)
        }
        when (val result = restaurantRepository.menu(restaurantId)) {
            is Result.Success -> menu = result.data
            // The menu failing is not worth blocking the page for: the address, the hours
            // and the booking button are all still useful without it.
            is Result.Failure -> messages.showError(result.message)
        }
        isLoading = false
    }

    val reviews by produceState(initialValue = emptyList<Review>(), restaurantId) {
        reviewRepository.reviews(restaurantId).collect { value = it }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MessageHost(messages) },
        bottomBar = {
            restaurant?.let { loaded ->
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$".repeat(loaded.priceBand.coerceIn(1, MAX_PRICE_BAND)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "${loaded.capacityPerSlot} seats a sitting",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PrimaryButton(
                            text = "Book a table",
                            onClick = { onBook(loaded.id, loaded.name) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (isLoading) {
            DetailSkeleton(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        val loaded = restaurant ?: return@Scaffold

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { DetailHero(restaurant = loaded, onBack = onBack) }
            item { DetailSummary(restaurant = loaded) }

            if (loaded.description.isNotBlank()) {
                item {
                    Section(title = "About") {
                        Text(
                            text = loaded.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Section(title = "Good to know") {
                    RestaurantFacts(
                        restaurant = loaded,
                        onNoMapApp = {
                            messages.showError(
                                "There is no map app on this device to open that in. The " +
                                    "address is above if you want to copy it.",
                            )
                        },
                        onCannotDial = {
                            messages.showError(
                                "This device cannot make calls. The number is above if " +
                                    "you want to ring from another phone.",
                            )
                        },
                    )
                }
            }

            item {
                Section(title = if (menu.isEmpty()) "Menu" else "Menu (${menu.size})") {
                    if (menu.isEmpty()) {
                        Text(
                            text = "This restaurant has not published its menu yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            itemsIndexed(menu, key = { _, item -> item.id }) { index, item ->
                AnimatedListItem(index = index) {
                    MenuRow(item = item, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            item {
                Section(
                    title = if (reviews.isEmpty()) {
                        "Reviews"
                    } else {
                        "Reviews (${reviews.size})"
                    },
                ) {
                    if (reviews.isEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RateReview,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "No reviews yet. Eat here and you can leave the first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Reviews are tied to a completed booking, so this sends the user to
                    // where that lives rather than opening a form they would be refused.
                    SecondaryButton(
                        text = if (isSignedIn) "Review a past visit" else "Leave a review",
                        onClick = onReview,
                    )
                    Text(
                        text = "You can review a restaurant once you have eaten there on a " +
                            "booking made through KulaPro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(reviews, key = { it.id }) { review ->
                ReviewRow(review = review, modifier = Modifier.padding(horizontal = 16.dp))
            }

            // Only offered on a listing nobody manages yet. Showing it on a restaurant that
            // already has an owner would invite a request that can only be declined.
            if (loaded.ownerUserId.isBlank()) {
                item {
                    Section(title = "Is this your restaurant?") {
                        Text(
                            text = "Tell us and we will put you in charge of the listing, " +
                                "so you can keep the menu, the hours and the tables right.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SecondaryButton(
                            text = "Claim this restaurant",
                            onClick = { onClaim(loaded.id, loaded.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(restaurant: Restaurant, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        // Falls back to a bundled photo of the cuisine when the restaurant has not published
        // one, so the page opens on food rather than on a placeholder.
        val fallback = cuisinePhoto(restaurant.cuisine, restaurant.name)
        AsyncImage(
            model = restaurant.imageUrl.ifBlank { fallback },
            placeholder = painterResource(fallback),
            error = painterResource(fallback),
            contentDescription = restaurant.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // A scrim under the title and the back button, so both stay legible whatever the
        // photo is doing behind them.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
                ),
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        Text(
            text = restaurant.name,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
}

@Composable
private fun DetailSummary(restaurant: Restaurant) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (restaurant.reviewCount > 0) {
            RatingBar(rating = restaurant.averageRating.toInt(), starSize = 18.dp)
            Text(
                text = "%.1f (${restaurant.reviewCount})".format(restaurant.averageRating),
                style = MaterialTheme.typography.labelLarge,
            )
        } else {
            Text(
                text = "Newly listed",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        if (restaurant.cuisine.isNotBlank()) {
            Text(
                text = restaurant.cuisine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestaurantFacts(
    restaurant: Restaurant,
    onNoMapApp: () -> Unit,
    onCannotDial: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (restaurant.address.isNotBlank() || restaurant.location != null) {
            val context = LocalContext.current
            Fact(
                icon = Icons.Rounded.Place,
                text = restaurant.address.ifBlank { "Show on the map" },
                // Hands the pin to the phone's map app, which already knows where the user
                // is and how they like to get about.
                onClick = {
                    val opened = openMapPin(
                        context = context,
                        location = restaurant.location,
                        label = restaurant.name,
                        address = restaurant.address,
                    )
                    if (!opened) {
                        onNoMapApp()
                    }
                },
            )
        }
        if (restaurant.phone.isNotBlank()) {
            val context = LocalContext.current
            Fact(
                icon = Icons.Rounded.Call,
                text = restaurant.phone,
                actionLabel = "Call",
                // Fills in the dialer rather than placing the call, so the user is the one
                // who decides to ring a restaurant.
                onClick = {
                    if (!openDialer(context, restaurant.phone)) {
                        onCannotDial()
                    }
                },
            )
        }
        Fact(
            Icons.Rounded.CalendarToday,
            "Sittings of ${restaurant.slotDurationMinutes} minutes",
        )
        if (restaurant.openingHours.isNotEmpty()) {
            Fact(Icons.Rounded.Schedule, "Opening hours")
            Column(
                modifier = Modifier.padding(start = 36.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DAY_ORDER.filter { it in restaurant.openingHours }.forEach { day ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = day.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = restaurant.openingHours.getValue(day),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionLabel: String = "Directions",
    onClick: (() -> Unit)? = null,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick == null) {
            Modifier
        } else {
            Modifier.fillMaxWidth().clickable(onClick = onClick)
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        // Only shown when the row does something, so a tappable row never looks like a
        // label and a label never looks tappable.
        if (onClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MenuRow(item: MenuItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.titleSmall)
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Items that already carry nutrition show it here, which is also what lets
                // the scanner read it from Firestore instead of paying for a model call.
                item.nutrition?.takeIf { it.caloriesKcal > 0 }?.let {
                    Text(
                        text = "%.0f kcal".format(it.caloriesKcal),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Text(
                text = formatPrice(item.priceCents, item.currency),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ReviewRow(review: Review, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RatingBar(rating = review.rating, starSize = 14.dp)
            Text(
                text = review.authorName.ifBlank { "A diner" },
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = DateFormat.getDateInstance(DateFormat.MEDIUM)
                    .format(review.createdAt.toDate()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (review.comment.isNotBlank()) {
            Text(text = review.comment, style = MaterialTheme.typography.bodyMedium)
        }
        if (review.ownerReply.isNotBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Response from the restaurant",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(text = review.ownerReply, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** Mirrors the real layout, so the page does not jump when the data lands. */
@Composable
private fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(HERO_HEIGHT))
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(TITLE_WIDTH_FRACTION).height(24.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(16.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            repeat(SKELETON_ROWS) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(64.dp))
            }
        }
    }
}

private fun formatPrice(priceCents: Long, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
    return runCatching {
        format.currency = Currency.getInstance(currencyCode)
        format.format(priceCents / CENTS_PER_UNIT)
    }.getOrElse {
        // An unknown currency code is bad data, not a reason to show a crash or a blank.
        "$currencyCode ${priceCents / CENTS_PER_UNIT}"
    }
}

private val HERO_HEIGHT = 260.dp
private const val MAX_PRICE_BAND = 4
private const val SKELETON_ROWS = 3

/** The placeholder title is half width, matching a typical restaurant name. */
private const val TITLE_WIDTH_FRACTION = 0.5f
private const val CENTS_PER_UNIT = 100.0
private val DAY_ORDER = listOf(
    "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
)
