package com.example.kulapro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kulapro.ui.theme.Motion
import com.example.kulapro.ui.theme.motionTween

/**
 * Fades and lifts a list item into place, staggered by position.
 *
 * Uses a [MutableTransitionState] seeded as not-yet-visible so the entry animation runs on
 * first composition. Under reduce motion the durations collapse to zero, so the item simply
 * appears rather than being skipped and left invisible.
 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val visibleState = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    val delay = staggerDelayMillis(index)

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = motionTween(
                durationMillis = Motion.DURATION_MEDIUM,
                delayMillis = delay,
                easing = Motion.EnterEasing,
            ),
        ) + slideInVertically(
            animationSpec = motionTween(
                durationMillis = Motion.DURATION_MEDIUM,
                delayMillis = delay,
                easing = Motion.EnterEasing,
            ),
            initialOffsetY = { it / 6 },
        ),
    ) {
        content()
    }
}
