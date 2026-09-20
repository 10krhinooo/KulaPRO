package com.example.kulapro.feature.owner

import com.example.kulapro.data.model.RestaurantTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityUiStateTest {

    @Test
    fun `seats have to be a number before anything can be saved`() {
        val state = CapacityUiState(isLoading = false, seatsPerSitting = "lots")

        assertNotNull(state.seatsError)
        assertFalse(state.canSave)
    }

    @Test
    fun `a sitting that seats nobody is refused`() {
        assertNotNull(CapacityUiState(seatsPerSitting = "0").seatsError)
    }

    @Test
    fun `an implausibly large room is flagged rather than accepted silently`() {
        assertNotNull(CapacityUiState(seatsPerSitting = "5000").seatsError)
    }

    @Test
    fun `a plain number of seats is accepted`() {
        assertNull(CapacityUiState(seatsPerSitting = "40").seatsError)
    }

    @Test
    fun `a day with times that do not read blocks the save`() {
        val state = CapacityUiState(
            seatsPerSitting = "40",
            days = listOf(DayHours("monday", isOpen = true, opens = "1", closes = "22:00")),
        )

        assertFalse(state.canSave)
    }

    @Test
    fun `saving is blocked while a save is already running`() {
        val state = CapacityUiState(seatsPerSitting = "40", isSaving = true)

        assertFalse(state.canSave)
    }

    @Test
    fun `seats on the floor is the sum of the mapped tables`() {
        val state = CapacityUiState(
            tables = listOf(
                RestaurantTable(id = "1", seats = 4),
                RestaurantTable(id = "2", seats = 2),
            ),
        )

        assertEquals(6, state.seatsOnTheFloor)
    }

    @Test
    fun `selling more seats than the floor plan holds is worth pointing out`() {
        val state = CapacityUiState(
            seatsPerSitting = "40",
            tables = listOf(RestaurantTable(id = "1", seats = 4)),
        )

        assertTrue(state.seatsExceedTables)
    }

    @Test
    fun `a restaurant that has mapped no tables is not warned about them`() {
        val state = CapacityUiState(seatsPerSitting = "40", tables = emptyList())

        assertFalse(state.seatsExceedTables)
    }

    @Test
    fun `open days are counted from the days that are actually switched on`() {
        val state = CapacityUiState(
            days = listOf(
                DayHours("monday", isOpen = true),
                DayHours("tuesday", isOpen = false),
                DayHours("wednesday", isOpen = true),
            ),
        )

        assertEquals(2, state.openDays)
    }

    @Test
    fun `a fresh state has one entry per day of the week`() {
        assertEquals(7, CapacityUiState().days.size)
        assertEquals("monday", CapacityUiState().days.first().key)
    }
}

class DayHoursTest {

    @Test
    fun `a closed day has no error however its times read`() {
        val day = DayHours("monday", isOpen = false, opens = "nonsense", closes = "also")

        assertNull(day.error)
        assertNull(day.asStoredRange())
    }

    @Test
    fun `closing before opening is an error a person can act on`() {
        val day = DayHours("monday", isOpen = true, opens = "22:00", closes = "12:00")

        assertNotNull(day.error)
        assertNull(day.asStoredRange())
    }

    @Test
    fun `a valid open day stores the range the availability calculation reads`() {
        val day = DayHours("monday", isOpen = true, opens = "12:00", closes = "22:00")

        assertNull(day.error)
        assertEquals("12:00-22:00", day.asStoredRange())
    }

    @Test
    fun `a stored range comes back into the form as an open day`() {
        val day = DayHours.of("friday", "09:30-23:00")

        assertTrue(day.isOpen)
        assertEquals("09:30", day.opens)
        assertEquals("23:00", day.closes)
    }

    @Test
    fun `a day with nothing stored comes back closed`() {
        val day = DayHours.of("friday", null)

        assertFalse(day.isOpen)
    }

    @Test
    fun `a day stored as nonsense comes back closed rather than half filled`() {
        val day = DayHours.of("friday", "not-hours")

        assertFalse(day.isOpen)
    }

    @Test
    fun `the label is the day name, not the stored key`() {
        assertEquals("Friday", DayHours("friday").label)
    }
}

class TableDraftTest {

    @Test
    fun `a table with no name cannot be saved`() {
        assertNotNull(TableDraft(label = " ", seats = "4").labelError)
        assertFalse(TableDraft(label = " ", seats = "4").canSave)
    }

    @Test
    fun `a table has to seat at least one person`() {
        assertNotNull(TableDraft(label = "T1", seats = "0").seatsError)
        assertNotNull(TableDraft(label = "T1", seats = "").seatsError)
    }

    @Test
    fun `an implausibly large table is flagged`() {
        assertNotNull(TableDraft(label = "T1", seats = "500").seatsError)
    }

    @Test
    fun `a named table with a sensible number of seats can be saved`() {
        assertTrue(TableDraft(label = "T1", seats = "4").canSave)
    }

    @Test
    fun `a new table is placed on the next free spot on the grid`() {
        val existing = List(5) { RestaurantTable(id = "t$it") }

        val table = TableDraft(label = "T6", seats = "2").toTable("r1", existing)

        assertEquals(1, table.row)
        assertEquals(1, table.column)
    }

    @Test
    fun `an edited table stays where diners already know to find it`() {
        val existing = listOf(RestaurantTable(id = "t1", row = 3, column = 2))

        val table = TableDraft(id = "t1", label = "T1", seats = "6").toTable("r1", existing)

        assertEquals(3, table.row)
        assertEquals(2, table.column)
        assertEquals(6, table.seats)
    }

    @Test
    fun `a blank area falls back to the main floor rather than being stored empty`() {
        val table = TableDraft(label = "T1", seats = "2", zone = "  ").toTable("r1", emptyList())

        assertEquals("Main floor", table.zone)
    }

    @Test
    fun `an existing table opens with its stored values back in the form`() {
        val draft = TableDraft.of(
            RestaurantTable(id = "t1", label = "T4", seats = 6, zone = "Terrace"),
        )

        assertEquals("t1", draft.id)
        assertEquals("T4", draft.label)
        assertEquals("6", draft.seats)
        assertEquals("Terrace", draft.zone)
        assertFalse(draft.isNew)
    }
}
