package com.example.kulapro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.ui.theme.LocalReduceMotion
import com.example.kulapro.ui.theme.Motion

/**
 * The app's primary content unit.
 *
 * Presents the things that actually decide where someone eats: what it looks like, what it
 * costs, what others thought, and whether there is a table tonight.
 */
@Composable
fun RestaurantCard(
    restaurant: Restaurant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    availabilityLabel: String? = null,
    /** Null for a guest, who has nowhere to keep a restaurant yet. */
    isFavourite: Boolean? = null,
    onToggleFavourite: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current

    // A small press-in gives the card physicality. Skipped entirely under reduce motion.
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reduceMotion) 0.98f else 1f,
        animationSpec = Motion.smooth(),
        label = "cardPressScale",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box {
            RestaurantImage(
                imageUrl = restaurant.imageUrl,
                fallback = cuisinePhoto(restaurant.cuisine, restaurant.name),
                wash = restaurantWash(restaurant.name),
                contentDescription = restaurant.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )

            // A scrim so the label stays legible whatever the photo underneath is doing.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            startY = 220f,
                        ),
                    ),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                availabilityLabel?.let { AvailabilityPill(label = it) }
                if (isFavourite != null && onToggleFavourite != null) {
                    FavouriteButton(
                        isFavourite = isFavourite,
                        restaurantName = restaurant.name,
                        onClick = onToggleFavourite,
                    )
                }
            }

            Text(
                text = restaurant.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (restaurant.reviewCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = "%.1f".format(restaurant.averageRating),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = "(${restaurant.reviewCount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "New",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            if (restaurant.cuisine.isNotBlank()) {
                CuisineChip(restaurant.cuisine)
            }

            Text(
                text = "$".repeat(restaurant.priceBand.coerceIn(1, 4)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestaurantImage(
    imageUrl: String,
    @DrawableRes fallback: Int,
    wash: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val isOwnPhoto = imageUrl.isNotBlank()

    Box(modifier = modifier) {
        // A restaurant that has published a photo shows it; everything else shows a bundled
        // photo of the food it serves, which reads as a restaurant in a way an icon does not.
        AsyncImage(
            model = imageUrl.ifBlank { fallback },
            placeholder = painterResource(fallback),
            error = painterResource(fallback),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )

        // Only over the stand-in. The whole reason for the wash is that a handful of bundled
        // photos have to stand for a dozen restaurants; a restaurant's own photograph is
        // already its own, and tinting it would be vandalism.
        if (!isOwnPhoto) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(wash.copy(alpha = RESTAURANT_WASH_ALPHA)),
            )
        }
    }
}

@Composable
private fun CuisineChip(cuisine: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = cuisine,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Surfaces tonight's availability on the card, so the answer comes before the tap. */
@Composable
fun AvailabilityPill(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PILL_CORNER_PERCENT),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Fades and lifts an item into place, with a per index delay capped so long lists stay fast. */
@Composable
fun staggerDelayMillis(index: Int): Int =
    if (LocalReduceMotion.current) {
        0
    } else {
        index.coerceAtMost(Motion.STAGGER_MAX_ITEMS) * Motion.STAGGER_STEP_MILLIS
    }

/** A fully rounded pill: the radius is half the height whatever the text length. */
private const val PILL_CORNER_PERCENT = 50

/**
 * Keeps a restaurant, or lets it go.
 *
 * Sits on the photo rather than in the row below it so the whole card stays one tap to
 * open. It carries its own contentDescription naming the restaurant, because a screen
 * reader moving down a list of hearts would otherwise hear the same word a dozen times.
 */
@Composable
private fun FavouriteButton(
    isFavourite: Boolean,
    restaurantName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current

    // A small pop on filling, and none on clearing. Keeping something is the moment worth
    // acknowledging; letting it go should be quiet.
    val scale by animateFloatAsState(
        targetValue = if (isFavourite && !reduceMotion) 1.1f else 1f,
        animationSpec = Motion.bouncy(),
        label = "favouriteScale",
    )

    Surface(
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isFavourite) {
                    Icons.Filled.Favorite
                } else {
                    Icons.Outlined.FavoriteBorder
                },
                contentDescription = if (isFavourite) {
                    "Remove $restaurantName from your list"
                } else {
                    "Keep $restaurantName"
                },
                tint = if (isFavourite) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(20.dp)
                    .scale(scale),
            )
        }
    }
}
