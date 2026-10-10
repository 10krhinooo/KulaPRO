package com.example.kulapro.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.kulapro.ui.theme.LocalReduceMotion

/**
 * A placeholder block that shimmers while real content loads.
 *
 * Preferable to a lone spinner: it shows the shape of what is coming, so the layout does not
 * jump when data lands.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val reduceMotion = LocalReduceMotion.current
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface

    if (reduceMotion) {
        // A travelling highlight is exactly the kind of motion reduce-motion users switch
        // off, so fall back to a flat block rather than animating anyway.
        Box(modifier = modifier.clip(shape).background(base))
        return
    }

    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x = offset * 400f, y = 0f),
                    end = Offset(x = (offset + 1f) * 400f, y = 0f),
                ),
            ),
    )
}

/** Skeleton matching [RestaurantCard]'s footprint, so nothing shifts when data arrives. */
@Composable
fun RestaurantCardSkeleton(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(0.dp),
            )
            Column(modifier = Modifier.padding(16.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(SKELETON_TITLE_WIDTH_FRACTION)
                        .height(20.dp),
                )
                Box(Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(SKELETON_SUBTITLE_WIDTH_FRACTION)
                        .height(14.dp),
                )
            }
        }
    }
}

// Roughly the proportions of a restaurant name and its metadata row.
private const val SKELETON_TITLE_WIDTH_FRACTION = 0.6f
private const val SKELETON_SUBTITLE_WIDTH_FRACTION = 0.4f
