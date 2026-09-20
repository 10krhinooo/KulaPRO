package com.example.kulapro.feature.scanner

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kulapro.data.scanner.DishNutrition
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.ErrorState
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.theme.Motion
import com.example.kulapro.util.ImageDownscaler
import kotlin.math.roundToInt

/**
 * Point the camera at a dish, get an estimate of what is on the plate.
 *
 * The honesty is not a footnote here, it is the design. Every result says what serving it
 * was calculated against and how sure the model was, because portion size is the one thing
 * a single photograph genuinely cannot tell you. The screen never answers an allergen
 * question and never says whether a dish is safe for anyone: an image classifier is not an
 * allergen test, and someone acting on one could be hurt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The system photo picker rather than a live CameraX preview. It offers both taking a
    // photo and choosing one, it handles focus and exposure better than a first attempt
    // would, and it needs no camera permission at all, which is one fewer dialog between a
    // diner and the thing they wanted to do.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val encoded = ImageDownscaler.toScanBase64 {
            context.contentResolver.openInputStream(uri)
        }
        if (encoded == null) viewModel.reportUnreadableImage() else viewModel.scan(encoded)
    }

    fun pick() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Scan a dish") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !state.isAvailable -> EmptyState(
                    title = "Scanning is not set up",
                    description = "This build has no scanner configured, so there is " +
                        "nothing to send the photo to.",
                    icon = Icons.Outlined.PhotoCamera,
                )

                state.isScanning -> Scanning()

                state.errorMessage != null -> ErrorState(
                    message = state.errorMessage.orEmpty(),
                    onRetry = {
                        viewModel.dismissError()
                        pick()
                    },
                )

                state.isNotFood -> EmptyState(
                    title = "That does not look like food",
                    description = "Point the camera at a plate and try again.",
                    icon = Icons.Outlined.PhotoCamera,
                    actionLabel = "Try another photo",
                    onAction = { pick() },
                )

                state.hasResult -> ResultSheet(
                    state = state,
                    onPortionChange = viewModel::setPortion,
                    onScanAgain = {
                        viewModel.clear()
                        pick()
                    },
                )

                else -> EmptyState(
                    title = "What are you eating?",
                    description = "Take a photo of the dish and we will estimate what is " +
                        "on the plate.",
                    icon = Icons.Outlined.PhotoCamera,
                    actionLabel = "Take a photo",
                    onAction = { pick() },
                )
            }
        }
    }
}

@Composable
private fun Scanning(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text("Reading the plate", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "A few seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultSheet(
    state: ScannerUiState,
    onPortionChange: (Double) -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = state.shown ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(shown.dishName, style = MaterialTheme.typography.headlineSmall)
        shown.cuisine?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Before the numbers, not after them. What the estimate assumed is the first thing
        // that decides whether the numbers below mean anything to this diner.
        EstimateBasis(result = shown)

        CalorieHeadline(calories = shown.caloriesKcal)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Macro("Protein", shown.proteinG, Modifier.weight(1f))
            Macro("Carbs", shown.carbsG, Modifier.weight(1f))
            Macro("Fat", shown.fatG, Modifier.weight(1f))
            Macro("Fibre", shown.fibreG, Modifier.weight(1f))
        }

        Text("How much are you eating?", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PORTION_CHOICES.forEach { factor ->
                FilterChip(
                    selected = factor == state.portionFactor,
                    onClick = { onPortionChange(factor) },
                    label = { Text(labelFor(factor)) },
                )
            }
        }

        if (shown.likelyIngredients.isNotEmpty()) {
            HorizontalDivider()
            Text("Probably contains", style = MaterialTheme.typography.titleSmall)
            Text(
                text = shown.likelyIngredients.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (shown.healthNotes.isNotEmpty()) {
            Text("Worth knowing", style = MaterialTheme.typography.titleSmall)
            shown.healthNotes.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Disclaimer()

        PrimaryButton(
            text = "Scan another dish",
            onClick = onScanAgain,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What the estimate was calculated against, and how sure it is.
 *
 * Given its own surface rather than set as small print, because it is the difference
 * between a number that helps and a number that misleads.
 */
@Composable
private fun EstimateBasis(result: DishNutrition, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Estimate, ${result.confidence.label.lowercase()}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = result.confidence.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (result.assumedPortion.isNotBlank()) {
                Text(
                    text = "Calculated for ${result.assumedPortion}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            AnimatedVisibility(visible = result.wasCached) {
                Text(
                    text = "Someone has scanned this dish before, so this was instant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CalorieHeadline(calories: Double, modifier: Modifier = Modifier) {
    // Counts up rather than snapping, so changing the portion reads as the same number
    // moving rather than as a different answer appearing.
    val animated by animateFloatAsState(
        targetValue = calories.toFloat(),
        animationSpec = Motion.smooth(),
        label = "calories",
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = animated.roundToInt().toString(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "kcal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
}

@Composable
private fun Macro(label: String, grams: Double, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = grams.toFloat(),
        animationSpec = Motion.smooth(),
        label = "macro_$label",
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("${animated.roundToInt()}g", style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The line that has to be there.
 *
 * Allergens are the one question this feature must never answer. A photograph cannot show
 * what a kitchen put in a sauce, and someone with a real allergy acting on a guess could be
 * badly hurt, so the app says where the answer actually lives.
 */
@Composable
private fun Disclaimer(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = "These are estimates from a photograph, not measurements, and they are " +
                "not dietary advice. For allergens, ask the restaurant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun labelFor(factor: Double): String = when (factor) {
    HALF -> "Half"
    1.0 -> "All of it"
    else -> "One and a half"
}
