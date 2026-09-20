package com.example.kulapro.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.repository.Result
import kotlinx.coroutines.delay

/**
 * A transient message and whether it reports success or failure.
 *
 * [id] distinguishes two identical messages shown one after the other. Without it, retrying
 * a failing action would leave the first banner's timer running and the second would vanish
 * early.
 */
data class UiMessage(
    val text: String,
    val isError: Boolean = false,
    val id: Long = nextId(),
) {
    companion object {
        private var counter = 0L

        private fun nextId(): Long = ++counter

        fun success(text: String) = UiMessage(text, isError = false)

        fun error(text: String) = UiMessage(text, isError = true)
    }
}

/** How long a message stays on screen before it fades out. */
const val MESSAGE_DURATION_MILLIS = 5_000L

/**
 * Holds the one message a screen is currently showing.
 *
 * A screen reports outcomes through this rather than through a snackbar host, so success and
 * failure are told apart at a glance and every screen phrases them the same way.
 */
@Stable
class MessageHostState {
    var current: UiMessage? by mutableStateOf(null)
        private set

    fun showSuccess(text: String) {
        current = UiMessage.success(text)
    }

    fun showError(text: String) {
        current = UiMessage.error(text)
    }

    fun dismiss() {
        current = null
    }
}

/**
 * Reports a repository outcome.
 *
 * Success wording is supplied by the caller; a failure always shows the repository's own
 * message, so an error is never flattened into a generic "something went wrong".
 */
fun MessageHostState.report(result: Result<*>, successText: String) {
    when (result) {
        is Result.Success -> showSuccess(successText)
        is Result.Failure -> showError(result.message)
    }
}

@Composable
fun rememberMessageHostState(): MessageHostState = remember { MessageHostState() }

/** Drops into a Scaffold's message slot and renders whatever [state] is currently holding. */
@Composable
fun MessageHost(state: MessageHostState, modifier: Modifier = Modifier) {
    MessageBanner(
        message = state.current,
        onDismiss = state::dismiss,
        modifier = modifier,
    )
}

/**
 * Shows [message] for five seconds, then fades it out.
 *
 * Colour distinguishes success from failure, and an icon carries the same meaning for anyone
 * who cannot rely on colour alone.
 */
@Composable
fun MessageBanner(
    message: UiMessage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(message?.id) {
        if (message != null) {
            delay(MESSAGE_DURATION_MILLIS)
            onDismiss()
        }
    }

    // Kept after dismissal so the exit animation still has something to draw. Without this
    // the banner would disappear instantly instead of fading.
    var lastShown by remember { mutableStateOf<UiMessage?>(null) }
    if (message != null) lastShown = message
    val shown = message ?: lastShown

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        if (shown == null) return@AnimatedVisibility

        val container = if (shown.isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val content = if (shown.isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = container,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (shown.isError) {
                        Icons.Rounded.ErrorOutline
                    } else {
                        Icons.Rounded.CheckCircle
                    },
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = shown.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                )
            }
        }
    }
}
