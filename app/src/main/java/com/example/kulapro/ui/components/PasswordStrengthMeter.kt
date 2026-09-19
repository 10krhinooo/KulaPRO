package com.example.kulapro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.ui.theme.Motion
import com.example.kulapro.util.Validators

/**
 * Live password strength, shown while typing.
 *
 * The bar and its colour animate between levels so improving a password feels like progress
 * rather than a label swapping out.
 */
@Composable
fun PasswordStrengthMeter(password: String, modifier: Modifier = Modifier) {
    val score = strengthScore(password)
    val scheme = MaterialTheme.colorScheme

    val targetColour = when {
        score >= STRONG -> scheme.primary
        score >= FAIR -> scheme.secondary
        else -> scheme.error
    }
    val label = when {
        score >= STRONG -> "Strong"
        score >= FAIR -> "Getting there"
        else -> "Too weak"
    }

    val progress by animateFloatAsState(
        targetValue = score / MAX_SCORE.toFloat(),
        animationSpec = Motion.smooth(),
        label = "strengthProgress",
    )
    val colour by animateColorAsState(
        targetValue = targetColour,
        animationSpec = Motion.smooth(),
        label = "strengthColour",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            color = colour,
            trackColor = scheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colour,
            )
            Text(
                text = "${password.length}/${Validators.MIN_PASSWORD_LENGTH}+",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** Counts the rules the password satisfies. Mirrors [Validators.passwordError]. */
private fun strengthScore(password: String): Int {
    var score = 0
    if (password.length >= Validators.MIN_PASSWORD_LENGTH) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { it.isLetter() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score
}

private const val MAX_SCORE = 4
private const val STRONG = 4
private const val FAIR = 3
