package com.example.kulapro.util

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class ImageDownscalerTest {

    @Test
    fun `small images are not subsampled`() {
        assertEquals(1, ImageDownscaler.sampleSizeFor(100, 100))
        assertEquals(1, ImageDownscaler.sampleSizeFor(256, 128))
        assertEquals(1, ImageDownscaler.sampleSizeFor(511, 300))
    }

    @Test
    fun `subsampling halves while the result stays above the bound`() {
        // 512 halves to 256, which still meets the bound, so one halving is correct.
        assertEquals(2, ImageDownscaler.sampleSizeFor(512, 512))
        assertEquals(4, ImageDownscaler.sampleSizeFor(1024, 768))
        assertEquals(8, ImageDownscaler.sampleSizeFor(4000, 2000))
    }

    @Test
    fun `sample size is driven by the longest edge`() {
        assertEquals(
            ImageDownscaler.sampleSizeFor(2000, 100),
            ImageDownscaler.sampleSizeFor(100, 2000),
        )
    }

    @Test
    fun `scaling preserves aspect ratio and respects the bound`() {
        val wide = Bitmap.createBitmap(1000, 500, Bitmap.Config.ARGB_8888)
        val scaled = ImageDownscaler.scaleToBound(wide)

        assertEquals(ImageDownscaler.MAX_DIMENSION, scaled.width)
        assertEquals(ImageDownscaler.MAX_DIMENSION / 2, scaled.height)
    }

    @Test
    fun `images already within the bound are returned untouched`() {
        val small = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        assertTrue(ImageDownscaler.scaleToBound(small) === small)
    }

    @Test
    fun `a very thin image never scales to a zero dimension`() {
        // Naive rounding would take the short edge to 0 and crash createScaledBitmap.
        val sliver = Bitmap.createBitmap(4000, 1, Bitmap.Config.ARGB_8888)
        val scaled = ImageDownscaler.scaleToBound(sliver)

        assertEquals(ImageDownscaler.MAX_DIMENSION, scaled.width)
        assertTrue("height must stay positive", scaled.height >= 1)
    }

    @Test
    fun `an unreadable source yields null rather than throwing`() {
        assertNull(ImageDownscaler.toDataUri { null })
    }

    @Test
    fun `a valid image encodes to a jpeg data uri`() {
        val png = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }.toByteArray()

        val dataUri = ImageDownscaler.toDataUri { ByteArrayInputStream(png) }

        assertNotNull(dataUri)
        assertTrue(dataUri!!.startsWith("data:image/jpeg;base64,"))
        assertTrue("payload should not be empty", dataUri.substringAfter("base64,").isNotEmpty())
    }
}
