package com.example.kulapro.feature.owner

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kulapro.ui.theme.Motion

/**
 * Switches between the diner and restaurant views.
 *
 * Only shown to users whose Auth claims name at least one restaurant, so it never appears
 * for ordinary diners and never implies access the rules would refuse.
 */
@Composable
fun PortalSwitcher(
    current: Portal,
    onSwitch: (Portal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = if (current == Portal.CUSTOMER) Portal.OWNER else Portal.CUSTOMER

    val background by animateColorAsState(
        targetValue = if (current == Portal.OWNER) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = Motion.smooth(),
        label = "portalBackground",
    )
    val content by animateColorAsState(
        targetValue = if (current == Portal.OWNER) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = Motion.smooth(),
        label = "portalContent",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PILL_CORNER_PERCENT),
        color = background,
        onClick = { onSwitch(target) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (target == Portal.OWNER) {
                    Icons.Outlined.Storefront
                } else {
                    Icons.Outlined.TravelExplore
                },
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Text(
                // Names the destination, not the current state: a control that says where it
                // takes you is read correctly the first time.
                text = if (target == Portal.OWNER) "Switch to hosting" else "Switch to booking",
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

private const val PILL_CORNER_PERCENT = 50
