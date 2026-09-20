package com.example.kulapro.feature.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.domain.DayLoad
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.theme.Motion
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Whether the platform is working.
 *
 * Not a restaurant's dashboard and not a diner's: these are the questions only the person
 * running the service asks. How many places are listed, how many of them nobody runs yet,
 * how many bookings are actually being kept, and when the whole platform is busy.
 *
 * Every figure is computed from bookings rather than read from a counter, so there is
 * nothing here that can quietly drift away from what happened.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMetricsScreen(
    modifier: Modifier = Modifier,
    viewModel: SystemMetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("System") }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.error != null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::refresh,
                )

                else -> Metrics(state = state, onSelectWindow = viewModel::selectWindow)
            }
        }
    }
}

@Composable
private fun Metrics(
    state: SystemMetricsUiState,
    onSelectWindow: (MetricsWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricsWindow.entries.forEach { window ->
                FilterChip(
                    selected = window == state.window,
                    onClick = { onSelectWindow(window) },
                    label = { Text(window.label) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Covers",
                value = state.coverCount,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Bookings",
                value = state.bookingCount,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Restaurants",
                value = state.restaurantCount,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Diners",
                value = state.dinerCount,
                modifier = Modifier.weight(1f),
            )
        }

        // The two numbers that are a job rather than a measurement. A request nobody has
        // decided on and a listing nobody runs are both somebody waiting.
        WaitingOn(
            pendingRequests = state.pendingRequestCount,
            unclaimedRestaurants = state.unclaimedRestaurantCount,
        )

        if (state.hasNoBookings) {
            EmptyState(
                title = "No bookings in this window",
                description = "Nothing has been booked in the last " +
                    "${state.window.days} days, so there is nothing to chart yet.",
            )
            return@Column
        }

        Section(title = "What happens to a booking") {
            Rate(label = "Cancelled", share = state.cancellationRate)
            Rate(label = "No-show", share = state.noShowRate)
            Rate(label = "Walk-ins recorded by restaurants", share = state.walkInShare)
        }

        Section(
            title = "Covers by day",
            caption = dayRangeCaption(state.coversByDay),
        ) {
            BarChart(
                bars = state.coversByDay.map { Bar(value = it.covers, label = "") },
            )
        }

        Section(
            title = "When the platform is busy",
            caption = state.busiestHour?.let { "Busiest at ${hourLabel(it.hour)}" },
        ) {
            BarChart(
                bars = state.coversByHour.map {
                    Bar(value = it.covers, label = it.hour.toString())
                },
            )
        }
    }
}

@Composable
private fun WaitingOn(
    pendingRequests: Int,
    unclaimedRestaurants: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$pendingRequests waiting on a decision",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                // Said plainly rather than as a figure on its own: a listing with no owner
                // cannot confirm a booking, and that is the consequence worth reading.
                text = "$unclaimedRestaurants listings have nobody running them, so nothing " +
                    "booked there can be confirmed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                caption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun Rate(label: String, share: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = share.coerceIn(0f, 1f),
        animationSpec = Motion.smooth(),
        label = "rate_$label",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${(share * PERCENT).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

/** One column of a chart: how tall, and what to write under it. */
private data class Bar(val value: Int, val label: String)

/**
 * A bar per bucket, scaled to the tallest.
 *
 * Deliberately not a charting library. The only thing these charts have to do is show shape,
 * and a dependency that draws axes and legends would be more code in the APK than the whole
 * admin console.
 */
@Composable
private fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier) {
    val tallest = bars.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { bar ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // A day with nothing booked still gets a sliver, so an empty
                        // Tuesday reads as empty rather than as missing.
                        .fillMaxHeight(
                            (bar.value.toFloat() / tallest).coerceAtLeast(EMPTY_BAR_FRACTION),
                        )
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
                )
                if (bar.label.isNotEmpty()) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = Motion.smooth(),
        label = "metric_$label",
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = animated.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** "3 Aug to 1 Sep", so a chart with no room for per-bar labels still says when it is. */
@Composable
private fun dayRangeCaption(days: List<DayLoad>): String? {
    val format = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val first = days.firstOrNull() ?: return null
    val last = days.last()
    return "${format.format(first.day)} to ${format.format(last.day)}"
}

private fun hourLabel(hour: Int): String = "%02d:00".format(Locale.getDefault(), hour)

private val CHART_HEIGHT = 140.dp
private const val PERCENT = 100
private const val EMPTY_BAR_FRACTION = 0.02f
