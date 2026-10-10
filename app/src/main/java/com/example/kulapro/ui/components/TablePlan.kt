package com.example.kulapro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.RestaurantTable
import com.example.kulapro.ui.theme.LocalReduceMotion
import com.example.kulapro.ui.theme.Motion

/**
 * Pick a table the way you pick a cinema seat.
 *
 * Laid out on the coarse grid the restaurant maps its tables onto, grouped by zone so the
 * plan reads as a room rather than as a grid of buttons. The important property is that a
 * table the diner cannot have looks different from one they can, and says why: a full room
 * and a room full of two seaters are different problems with different answers.
 */
@Composable
fun TablePlan(
    tables: List<RestaurantTable>,
    takenTableIds: Set<String>,
    partySize: Int,
    selectedTableId: String?,
    onSelect: (RestaurantTable) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenMarker()

        tables.groupBy { it.zone }.toSortedMap().forEach { (zone, zoneTables) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = zone,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                zoneTables.groupBy { it.row }.toSortedMap().forEach { (_, rowTables) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowTables.sortedBy { it.column }.forEach { table ->
                            TableChip(
                                table = table,
                                availability = when {
                                    table.id in takenTableIds -> TableAvailability.TAKEN
                                    table.seats < partySize -> TableAvailability.TOO_SMALL
                                    else -> TableAvailability.FREE
                                },
                                isSelected = table.id == selectedTableId,
                                onSelect = { onSelect(table) },
                            )
                        }
                    }
                }
            }
        }

        TableLegend()
    }
}

/** The fixed point everyone orients from, the way a cinema plan shows the screen. */
@Composable
private fun ScreenMarker() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(topStartPercent = 40, topEndPercent = 40),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(ENTRANCE_WIDTH_FRACTION).height(ENTRANCE_HEIGHT),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Entrance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TableChip(
    table: RestaurantTable,
    availability: TableAvailability,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val selectable = availability == TableAvailability.FREE
    val scale by animateFloatAsState(
        targetValue = if (isSelected && !LocalReduceMotion.current) 1.08f else 1f,
        animationSpec = Motion.bouncy(),
        label = "tableScale",
    )

    val container = when {
        isSelected -> MaterialTheme.colorScheme.primary
        availability == TableAvailability.TAKEN -> MaterialTheme.colorScheme.surfaceVariant
        availability == TableAvailability.TOO_SMALL -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        availability == TableAvailability.FREE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isSelected -> MaterialTheme.colorScheme.primary
                availability == TableAvailability.TAKEN -> Color.Transparent
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        modifier = Modifier
            .size(width = TABLE_WIDTH, height = TABLE_HEIGHT)
            .scale(scale)
            .clickable(enabled = selectable, onClick = onSelect)
            .semantics {
                contentDescription = "Table ${table.label}, ${table.seats} seats, " +
                    when (availability) {
                        TableAvailability.FREE -> "available"
                        TableAvailability.TAKEN -> "already booked"
                        TableAvailability.TOO_SMALL -> "too small for your party"
                    }
            },
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = table.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
            Text(
                text = if (availability == TableAvailability.TAKEN) {
                    "taken"
                } else {
                    "${table.seats}p"
                },
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun TableLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LegendKey(MaterialTheme.colorScheme.primaryContainer, "Free")
        LegendKey(MaterialTheme.colorScheme.primary, "Yours")
        LegendKey(MaterialTheme.colorScheme.surfaceVariant, "Taken")
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = color,
            modifier = Modifier.size(LEGEND_SWATCH),
        ) { Spacer(Modifier.width(LEGEND_SWATCH)) }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val TABLE_WIDTH = 56.dp
private val TABLE_HEIGHT = 48.dp
private val ENTRANCE_HEIGHT = 24.dp
private val LEGEND_SWATCH = 12.dp
private const val ENTRANCE_WIDTH_FRACTION = 0.45f
