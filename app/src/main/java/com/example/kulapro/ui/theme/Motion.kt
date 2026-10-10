package com.example.kulapro.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens.
 *
 * Durations and easings live here rather than being sprinkled through screens, so the app
 * moves consistently and the whole feel can be tuned in one place.
 */
object Motion {

    /** Small state changes: a chip selecting, an icon swapping. */
    const val DURATION_SHORT = 150

    /** The default for most transitions. */
    const val DURATION_MEDIUM = 300

    /** Screen level changes and shared element transitions. */
    const val DURATION_LONG = 450

    /** Decelerate: things entering the screen. */
    val EnterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Accelerate: things leaving. */
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Standard in and out. */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Selection and press feedback, where a little overshoot reads as responsive. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Layout and size changes, where overshoot would look unstable. */
    fun <T> smooth(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Per item delay when staggering a list in. Kept small so long lists do not crawl. */
    const val STAGGER_STEP_MILLIS = 40

    /** Cap on stagger, so item 50 does not wait two seconds to appear. */
    const val STAGGER_MAX_ITEMS = 8
}

/**
 * True when the user has asked the system to reduce or remove animations.
 *
 * Honouring this is an accessibility requirement, not a nicety: motion can trigger nausea and
 * vertigo for people with vestibular disorders, and some users switch animations off simply to
 * make a slow device usable.
 */
val LocalReduceMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun rememberSystemReduceMotion(): Boolean {
    val context = LocalContext.current
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}

/**
 * A duration that collapses to zero when motion is reduced.
 *
 * Returning zero rather than skipping the animation call keeps every animated value landing on
 * its target state, so nothing is left half transitioned.
 */
@Composable
fun motionDuration(base: Int): Int = if (LocalReduceMotion.current) 0 else base

/** [tween] that respects the reduce motion setting. */
@Composable
fun <T> motionTween(
    durationMillis: Int = Motion.DURATION_MEDIUM,
    delayMillis: Int = 0,
    easing: Easing = Motion.StandardEasing,
): FiniteAnimationSpec<T> = tween(
    durationMillis = motionDuration(durationMillis),
    delayMillis = motionDuration(delayMillis),
    easing = easing,
)
