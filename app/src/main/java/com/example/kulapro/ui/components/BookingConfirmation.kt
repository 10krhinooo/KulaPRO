package com.example.kulapro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kulapro.ui.theme.LocalReduceMotion
import com.example.kulapro.ui.theme.Motion

/**
 * The success moment after a booking lands.
 *
 * Confirming a table is the point of the app, and it used to be a Toast. Drawing the tick
 * stroke by stroke gives the moment enough weight to register.
 */
@Composable
fun BookingConfirmation(
    restaurantName: String,
    whenLabel: String,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val tickColour = MaterialTheme.colorScheme.primary

    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = Motion.DURATION_LONG,
                    easing = Motion.EnterEasing,
                ),
            )
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val tick = Path().apply {
                moveTo(size.width * TICK_START_X, size.height * TICK_START_Y)
                lineTo(size.width * TICK_ELBOW_X, size.height * TICK_ELBOW_Y)
                lineTo(size.width * TICK_END_X, size.height * TICK_END_Y)
            }
            val measure = PathMeasure().apply { setPath(tick, false) }
            val drawn = Path()
            measure.getSegment(0f, measure.length * progress.value, drawn, true)

            drawPath(
                path = drawn,
                color = tickColour,
                style = Stroke(width = TICK_STROKE_WIDTH, cap = StrokeCap.Round),
            )
        }

        Text(
            text = "Table booked",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$restaurantName, $whenLabel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// Tick geometry as fractions of the canvas, so it scales with the icon size.
private const val TICK_START_X = 0.22f
private const val TICK_START_Y = 0.52f
private const val TICK_ELBOW_X = 0.42f
private const val TICK_ELBOW_Y = 0.72f
private const val TICK_END_X = 0.78f
private const val TICK_END_Y = 0.30f
private const val TICK_STROKE_WIDTH = 8f
