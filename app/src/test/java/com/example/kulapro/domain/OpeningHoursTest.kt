package com.example.kulapro.domain

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningHoursTest {

    @Test
    fun `a well formed range is read as minutes from midnight`() {
        val hours = OpeningHours.parseRange("12:00-22:30")

        assertEquals(OpeningHours(openMinutes = 720, closeMinutes = 1350), hours)
    }

    @Test
    fun `a range that closes before it opens is not a trading day`() {
        assertNull(OpeningHours.parseRange("22:00-12:00"))
    }

    @Test
    fun `a range that closes exactly when it opens is not a trading day`() {
        assertNull(OpeningHours.parseRange("12:00-12:00"))
    }

    @Test
    fun `a missing day is closed rather than an error`() {
        assertNull(OpeningHours.parseRange(null))
    }

    @Test
    fun `malformed input is refused rather than guessed at`() {
        listOf("", "12:00", "12:00-22:00-23:00", "noon-midnight", "25:00-26:00", "12:61-13:00")
            .forEach { assertNull("parsed \"$it\"", OpeningHours.parseRange(it)) }
    }

    @Test
    fun `a parsed range writes back to exactly what it was read from`() {
        val stored = "09:05-17:45"

        assertEquals(stored, OpeningHours.parseRange(stored)?.asStoredRange())
    }

    @Test
    fun `single digit hours are padded so the stored form sorts and compares`() {
        assertEquals("09:05", OpeningHours.formatMinutes(545))
        assertEquals("00:00", OpeningHours.formatMinutes(0))
    }

    @Test
    fun `a day key is the lowercase name the stored map uses`() {
        val monday = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 5)
        }.time

        assertEquals("monday", OpeningHours.dayKeyFor(monday))
    }

    @Test
    fun `every day of the week maps to one of the stored keys`() {
        val calendar = Calendar.getInstance()
        val keys = (0..6).map {
            val key = OpeningHours.dayKeyFor(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            key
        }

        assertEquals(OpeningHours.DAY_KEYS.sorted(), keys.sorted())
    }

    @Test
    fun `the week starts on Monday, the way a trading week is read`() {
        assertEquals("monday", OpeningHours.DAY_KEYS.first())
        assertEquals("sunday", OpeningHours.DAY_KEYS.last())
    }

    @Test
    fun `a day key is shown with a capital, not as it is stored`() {
        assertEquals("Monday", OpeningHours.displayName("monday"))
    }
}
