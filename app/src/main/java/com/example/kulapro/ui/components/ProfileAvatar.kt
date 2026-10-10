package com.example.kulapro.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * A user's avatar: their photo if they have one, otherwise their initials.
 *
 * Initials mean the avatar never renders as a broken image or an empty grey circle, which is
 * what a photo-only design does before the first upload.
 */
@Composable
fun ProfileAvatar(
    photoUrl: String,
    fallbackText: String?,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    uploading: Boolean = false,
    onEditClick: (() -> Unit)? = null,
) {
    val initials = fallbackText
        ?.substringBefore('@')
        ?.filter { it.isLetterOrDigit() }
        ?.take(2)
        ?.uppercase()
        .orEmpty()
        .ifBlank { "?" }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(size),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            val inlineBitmap = remember(photoUrl) { decodeDataUri(photoUrl) }

            when {
                // Avatars are stored inline on the profile document as a data URI. Coil has
                // no fetcher for that scheme, so decode it here rather than render nothing.
                inlineBitmap != null -> Image(
                    bitmap = inlineBitmap,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )

                photoUrl.isNotBlank() -> AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )

                else -> Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        if (uploading) {
            Surface(
                modifier = Modifier.size(size),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        onEditClick?.let {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primary,
                onClick = it,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Change profile photo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private const val DATA_URI_PREFIX = "data:image"

/** Decodes an inline `data:` image, returning null for ordinary URLs or malformed input. */
private fun decodeDataUri(value: String): ImageBitmap? {
    if (!value.startsWith(DATA_URI_PREFIX)) return null
    val payload = value.substringAfter("base64,", missingDelimiterValue = "")
    if (payload.isEmpty()) return null
    return runCatching {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
