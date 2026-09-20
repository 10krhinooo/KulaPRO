package com.example.kulapro.util

import java.util.Date

/**
 * The current time, as a thing that can be handed in.
 *
 * Anything asking `Date()` directly is untestable by definition: its behaviour depends on
 * when the test happens to run. That is not hypothetical here. Home decides which
 * restaurants still have a sitting left today, and a test of it passes all afternoon and
 * fails after the last booking of the evening.
 */
fun interface Clock {
    fun now(): Date
}

/** The real one. Bound in the DI graph; a test hands in a fixed instant instead. */
val SystemClock = Clock { Date() }
