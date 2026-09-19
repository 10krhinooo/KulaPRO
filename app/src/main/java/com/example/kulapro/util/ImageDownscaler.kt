package com.example.kulapro.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Turns a picked image into a small data URI suitable for storing inline.
 *
 * Avatars live in the user's Firestore document rather than in Cloud Storage. At
 * [MAX_DIMENSION] pixels a JPEG lands around 20KB, comfortably inside Firestore's 1 MiB
 * document limit, and it needs no Storage bucket, no bucket rules and no extra billing.
 * Larger imagery, such as restaurant photography, does not belong here.
 *
 * Takes stream factories rather than a Context and a Uri so the scaling behaviour can be
 * tested directly, without a ContentResolver.
 */
object ImageDownscaler {

    const val MAX_DIMENSION = 256
    private const val JPEG_QUALITY = 80
    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

    /**
     * @param openStream called twice: once to measure the image, once to decode it. Measuring
     *   first means a large photo is never fully decoded into memory.
     * @return a `data:` URI, or null if the image could not be read.
     */
    fun toDataUri(openStream: () -> InputStream?): String? {
        val bitmap = decodeScaled(openStream) ?: return null
        return try {
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            DATA_URI_PREFIX + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeScaled(openStream: () -> InputStream?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val decoded = openStream()?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return scaleToBound(decoded)
    }

    /** Scales [source] so its longest edge is at most [MAX_DIMENSION], preserving aspect. */
    internal fun scaleToBound(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_DIMENSION) return source

        val scale = MAX_DIMENSION.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== source) source.recycle()
        return scaled
    }

    /**
     * The power-of-two subsampling factor BitmapFactory should use.
     *
     * Halves while the result would still be at least [MAX_DIMENSION], so decoding never
     * undershoots the target and loses quality.
     */
    internal fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_DIMENSION) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
