package com.example.kulapro.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Shrinks a picked or captured image before anything is done with it.
 *
 * Two callers with two very different bounds. An avatar at [AVATAR_DIMENSION] pixels lands
 * around 20KB, comfortably inside Firestore's 1 MiB document limit, so it needs no Storage
 * bucket, no bucket rules and no extra billing. A dish photographed for the scanner goes to
 * [SCAN_DIMENSION], which is where Claude's vision resolution tops out: anything larger
 * costs tokens without buying accuracy.
 *
 * Takes stream factories rather than a Context and a Uri so the scaling behaviour can be
 * tested directly, without a ContentResolver.
 */
object ImageDownscaler {

    /** Big enough to recognise a face in a list row, small enough to store in a document. */
    const val AVATAR_DIMENSION = 256

    /** Claude's effective vision resolution on the long edge. Beyond this is waste. */
    const val SCAN_DIMENSION = 1568

    private const val JPEG_QUALITY = 80
    private const val SCAN_JPEG_QUALITY = 85
    private const val DATA_URI_PREFIX = "data:image/jpeg;base64,"

    /**
     * @param openStream called twice: once to measure the image, once to decode it. Measuring
     *   first means a large photo is never fully decoded into memory.
     * @return a `data:` URI, or null if the image could not be read.
     */
    fun toDataUri(openStream: () -> InputStream?): String? {
        val bitmap = decodeScaled(openStream, AVATAR_DIMENSION) ?: return null
        return try {
            DATA_URI_PREFIX + Base64.encodeToString(compress(bitmap, JPEG_QUALITY), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Bare base64 rather than a data URI, because the scanner sends the bytes in a JSON
     * field and a URI prefix would be a hundred wasted characters on every scan.
     *
     * @return base64 JPEG, or null if the image could not be read.
     */
    fun toScanBase64(openStream: () -> InputStream?): String? {
        val bitmap = decodeScaled(openStream, SCAN_DIMENSION) ?: return null
        return try {
            Base64.encodeToString(compress(bitmap, SCAN_JPEG_QUALITY), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }

    private fun decodeScaled(openStream: () -> InputStream?, bound: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, bound)
        }
        val decoded = openStream()?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return scaleToBound(decoded, bound)
    }

    /** Scales [source] so its longest edge is at most [bound], preserving aspect. */
    internal fun scaleToBound(source: Bitmap, bound: Int = AVATAR_DIMENSION): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= bound) return source

        val scale = bound.toFloat() / longest
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
     * Halves while the result would still be at least [bound], so decoding never undershoots
     * the target and loses quality.
     */
    internal fun sampleSizeFor(width: Int, height: Int, bound: Int = AVATAR_DIMENSION): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= bound) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
