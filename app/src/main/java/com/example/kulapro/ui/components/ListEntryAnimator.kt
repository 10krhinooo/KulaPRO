package com.example.kulapro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/**
 * Remembers which rows have already made their entrance.
 *
 * A LazyColumn disposes rows that scroll out of view and composes them again on the way
 * back, so state remembered inside a row is not remembered for long. Without this, every
 * scroll replayed the entry animation, and a row five down waited out its stagger delay
 * before fading in: the list appeared to load late every single time it was scrolled.
 *
 * Hoisted above the list so it outlives any individual row, and keyed by the row's identity
 * rather than its index, because an index belongs to a position and not to a restaurant.
 */
@Stable
class ListEntryAnimator {
    private val seen = mutableSetOf<Any>()

    /** True the first time a row is seen, false forever after. */
    fun shouldAnimate(key: Any): Boolean = seen.add(key)
}

@Composable
fun rememberListEntryAnimator(): ListEntryAnimator = remember { ListEntryAnimator() }
