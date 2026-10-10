package com.example.kulapro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kulapro.ui.theme.LocalReduceMotion
import com.example.kulapro.ui.theme.Motion

/**
 * Star rating, tappable when [onRatingChange] is supplied.
 *
 * Selected stars pop briefly, so picking a rating feels like pressing something rather than
 * toggling a checkbox.
 */
@Composable
fun RatingBar(
    rating: Int,
    modifier: Modifier = Modifier,
    max: Int = MAX_RATING,
    starSize: Dp = 32.dp,
    onRatingChange: ((Int) -> Unit)? = null,
) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "Rating: $rating out of $max stars"
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (star in 1..max) {
            val filled = star <= rating
            val reduceMotion = LocalReduceMotion.current
            val scale by animateFloatAsState(
                targetValue = if (filled && !reduceMotion) 1.12f else 1f,
                animationSpec = Motion.bouncy(),
                label = "star$star",
            )
            val interactionSource = remember { MutableInteractionSource() }

            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (filled) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier
                    .size(starSize)
                    .scale(scale)
                    .then(
                        if (onRatingChange != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = null,
                            ) { onRatingChange(star) }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

const val MAX_RATING = 5
