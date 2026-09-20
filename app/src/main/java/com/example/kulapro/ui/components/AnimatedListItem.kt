package com.example.kulapro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kulapro.ui.theme.Motion
import com.example.kulapro.ui.theme.motionTween

/**
 * Fades and lifts a list item into place, staggered by position, once.
 *
 * Under reduce motion the durations collapse to zero, so the item simply appears rather
 * than being skipped and left invisible.
 */
@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    key: Any = index,
    animator: ListEntryAnimator? = null,
    content: @Composable () -> Unit,
) {
    // A row that has already been seen is drawn directly, with no animation and no
    // AnimatedVisibility wrapper to pay for.
    val animate = remember(key) { animator?.shouldAnimate(key) ?: true }
    if (!animate) {
        content()
        return
    }

    val visibleState = remember(key) {
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
