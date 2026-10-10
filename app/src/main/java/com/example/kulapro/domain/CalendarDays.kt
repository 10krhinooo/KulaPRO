package com.example.kulapro.domain

import java.util.Calendar
import java.util.Date

/**
 * Midnight at the start of the day an instant falls in, in the device's own time zone.
 *
 * Wherever a day is a bucket rather than an amount of elapsed time, this is the boundary.
 * Counting elapsed hours instead is what made a booking seventy one hours away read as "in
 * 2 days" and one seventy three hours away as "in 3", when both are the same Saturday.
 */
fun startOfDay(date: Date): Date = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

/** Whole days between two instants, counted by the date rather than by elapsed hours. */
fun calendarDaysBetween(from: Date, to: Date): Int =
    ((startOfDay(to).time - startOfDay(from).time) / MILLIS_PER_DAY).toInt()

/** The hour of the day an instant falls in, 0 to 23. */
fun hourOfDay(date: Date): Int =
    Calendar.getInstance().apply { time = date }.get(Calendar.HOUR_OF_DAY)

internal const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
