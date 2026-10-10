package com.example.kulapro.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneLinkTest {

    @Test
    fun `dials a plain international number`() {
        assertEquals("tel:+254700000001", dialUri("+254700000001"))
    }

    @Test
    fun `strips the punctuation a listing wraps its number in`() {
        // A tel: URI carrying brackets and spaces opens an empty dialer, so they go.
        assertEquals("tel:+254700000001", dialUri("+254 (700) 000-001"))
    }

    @Test
    fun `keeps an extension separator, which the dialer acts on`() {
        assertEquals("tel:+254700000001,123", dialUri("+254700000001,123"))
    }

    @Test
    fun `keeps a local number without a country code`() {
        assertEquals("tel:0700000001", dialUri("0700000001"))
    }

    @Test
    fun `keeps only a leading plus`() {
        // A plus in the middle is noise from a badly pasted number, not a country code.
        assertEquals("tel:+254700000001", dialUri("+254+700000001"))
    }

    @Test
    fun `has nothing to dial for an empty number`() {
        assertNull(dialUri(""))
    }

    @Test
    fun `has nothing to dial for text with no digits`() {
        assertNull(dialUri("call us on the website"))
    }

    @Test
    fun `refuses a number too short to be one`() {
        assertNull(dialUri("+"))
        assertNull(dialUri("12"))
    }
}
