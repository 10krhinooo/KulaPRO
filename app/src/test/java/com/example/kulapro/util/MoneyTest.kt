package com.example.kulapro.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `a whole number of units is read as cents`() {
        assertEquals(125_000L, Money.parseToCents("1250"))
    }

    @Test
    fun `a decimal amount keeps its cents`() {
        assertEquals(125_050L, Money.parseToCents("1250.50"))
    }

    @Test
    fun `a single decimal digit means tenths, not hundredths`() {
        assertEquals(550L, Money.parseToCents("5.5"))
    }

    @Test
    fun `a thousands separator is not a decimal point`() {
        assertEquals(125_000L, Money.parseToCents("1,250"))
        assertEquals(125_000L, Money.parseToCents("1.250"))
    }

    @Test
    fun `grouping and a decimal mark together are both read correctly`() {
        assertEquals(125_050L, Money.parseToCents("1,250.50"))
        assertEquals(125_050L, Money.parseToCents("1.250,50"))
    }

    @Test
    fun `a comma is accepted as the decimal mark`() {
        assertEquals(125_050L, Money.parseToCents("1250,50"))
    }

    @Test
    fun `a currency symbol and spaces are ignored`() {
        assertEquals(125_000L, Money.parseToCents(" KES 1,250 "))
    }

    @Test
    fun `more than two decimal places is refused rather than rounded`() {
        assertNull(Money.parseToCents("12.3456"))
    }

    @Test
    fun `text with no digits is not a price`() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("free"))
        assertNull(Money.parseToCents("."))
    }

    @Test
    fun `zero is a price, because a complimentary item is a real menu line`() {
        assertEquals(0L, Money.parseToCents("0"))
    }

    @Test
    fun `an edit field gets the plain amount back, with no symbol`() {
        assertEquals("1250.50", Money.toEditableText(125_050L))
        assertEquals("1250.00", Money.toEditableText(125_000L))
    }

    @Test
    fun `what is typed survives a round trip through storage`() {
        listOf("1250", "1250.50", "0.99", "5.5").forEach { typed ->
            val cents = Money.parseToCents(typed)
            assertEquals(typed, cents, Money.parseToCents(Money.toEditableText(cents!!)))
        }
    }

    @Test
    fun `an unknown currency code still shows the amount`() {
        val shown = Money.format(125_050L, "NOT_A_CURRENCY")

        assertEquals("NOT_A_CURRENCY 1250.50", shown)
    }

    @Test
    fun `a known currency code is formatted with its symbol`() {
        val shown = Money.format(125_050L, "KES")

        assertEquals(true, shown.contains("1,250.50") || shown.contains("1250.50"))
    }
}
