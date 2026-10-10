package com.example.kulapro.util

import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Uri.encode is an Android API, so these run under Robolectric rather than plain JVM. */
@RunWith(RobolectricTestRunner::class)
class MapLinkTest {

    @Test
    fun `drops a pin on the exact coordinates when there are any`() {
        val uri = mapUri(GeoPoint(-1.2649, 36.8038), "The Bistro", "Westlands, Nairobi")

        assertEquals("geo:-1.2649,36.8038?q=-1.2649,36.8038(The%20Bistro)", uri)
    }

    @Test
    fun `searches the written address when there are no coordinates`() {
        // Approximate, but a listing with an address and no coordinates is still findable,
        // and refusing to open anything would be worse.
        val uri = mapUri(null, "The Bistro", "Westlands, Nairobi")

        assertEquals("geo:0,0?q=Westlands%2C%20Nairobi", uri)
    }

    @Test
    fun `has nothing to offer when there is neither`() {
        assertNull(mapUri(null, "The Bistro", ""))
    }

    @Test
    fun `falls back to the address as the pin label when the name is missing`() {
        val uri = mapUri(GeoPoint(1.0, 2.0), "", "Ngong Road")

        assertTrue(uri!!.endsWith("(Ngong%20Road)"))
    }

    @Test
    fun `escapes a name that would otherwise break the uri`() {
        // Brackets close the label early and ampersands start a new parameter, so a
        // restaurant called "Fish & Chips (Karen)" would produce a malformed link.
        val uri = mapUri(GeoPoint(1.0, 2.0), "Fish & Chips (Karen)", "Karen")

        assertTrue(uri!!.contains("%26"))
        assertTrue(uri.contains("%28"))
        assertEquals(1, uri.count { it == '(' })
    }
}
