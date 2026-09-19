package com.example.kulapro.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kulapro.ui.theme.LocalReduceMotion

/**
 * Nudges an element side to side when [trigger] changes to a non-null value.
 *
 * Used on validation failures. A shake points at the offending field immediately, before the
 * user has read the message beneath it, which is faster than colour alone.
 */
@Composable
fun Modifier.shakeOnError(trigger: Any?): Modifier = composed {
    val offset = remember { Animatable(0f) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(trigger) {
        if (trigger != null && !reduceMotion) {
            offset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = SHAKE_DURATION
                    0f at 0
                    -SHAKE_DISTANCE at FIRST_SWING
                    SHAKE_DISTANCE at SECOND_SWING
                    -SHAKE_DISTANCE / 2 at THIRD_SWING
                    SHAKE_DISTANCE / 2 at FOURTH_SWING
                    0f at SHAKE_DURATION
                },
            )
        } else {
            offset.snapTo(0f)
        }
    }

    graphicsLayer { translationX = offset.value }
}

private const val SHAKE_DURATION = 360
private const val SHAKE_DISTANCE = 14f

// Four decaying swings, then settle back to centre.
private const val FIRST_SWING = SHAKE_DURATION / 6
private const val SECOND_SWING = SHAKE_DURATION / 3
private const val THIRD_SWING = SHAKE_DURATION / 2
private const val FOURTH_SWING = SHAKE_DURATION * 2 / 3
