package com.example.kulapro.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.kulapro.R
import com.example.kulapro.ui.theme.LocalReduceMotion

/**
 * Shared chrome for the sign-in, register and password reset screens.
 *
 * Gives the auth flow a single identity: a warm brand header, then the form lifted onto a
 * surface card. Previously each of the three screens laid itself out independently and they
 * drifted apart in spacing and hierarchy.
 */
@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Brand header. The gradient runs from the primary container into the background so
        // the card below appears to lift off it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))

            val reduceMotion = LocalReduceMotion.current
            val float by rememberInfiniteTransition(label = "brandFloat").animateFloat(
                initialValue = 0f,
                targetValue = if (reduceMotion) 0f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "brandFloatValue",
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { translationY = -float * BRAND_FLOAT_DISTANCE },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_kula_mark),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "KulaPro",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.height(32.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    content()
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // Drawn after the scrolling column so it sits on top. Declared before it, the
        // full size column covered the button and swallowed every tap.
        onBack?.let {
            IconButton(
                onClick = it,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

    }
}

private val HEADER_HEIGHT = 260.dp
private const val BRAND_FLOAT_DISTANCE = 10f
