package com.example.kulapro.feature.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.Restaurant
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.PrimaryButton

/**
 * Lets an owner maintain their own listing.
 *
 * Until this existed, restaurant data could only be created by running a seed script by
 * hand, which is not a system anyone can operate.
 */
@Composable
fun EditRestaurantScreen(
    restaurant: Restaurant,
    onSave: (Restaurant) -> Unit,
    modifier: Modifier = Modifier,
    saving: Boolean = false,
) {
    var name by remember(restaurant.id) { mutableStateOf(restaurant.name) }
    var description by remember(restaurant.id) { mutableStateOf(restaurant.description) }
    var cuisine by remember(restaurant.id) { mutableStateOf(restaurant.cuisine) }
    var address by remember(restaurant.id) { mutableStateOf(restaurant.address) }
    var phone by remember(restaurant.id) { mutableStateOf(restaurant.phone) }
    var capacity by remember(restaurant.id) {
        mutableStateOf(restaurant.capacityPerSlot.toString())
    }
    var slotMinutes by remember(restaurant.id) {
        mutableStateOf(restaurant.slotDurationMinutes.toString())
    }
    var priceBand by remember(restaurant.id) { mutableStateOf(restaurant.priceBand.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your listing", style = MaterialTheme.typography.headlineSmall)

        KulaTextField(value = name, onValueChange = { name = it }, label = "Restaurant name")
        KulaTextField(
            value = description,
            onValueChange = { description = it },
            label = "Description",
        )
        KulaTextField(value = cuisine, onValueChange = { cuisine = it }, label = "Cuisine")
        KulaTextField(value = address, onValueChange = { address = it }, label = "Address")
        KulaTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone",
            keyboardType = KeyboardType.Phone,
        )

        Text("Capacity", style = MaterialTheme.typography.titleSmall)
        KulaTextField(
            value = capacity,
            onValueChange = { capacity = it.filter(Char::isDigit) },
            label = "Seats per sitting",
            helperText = "How many guests you can seat in one slot",
            keyboardType = KeyboardType.Number,
        )
        KulaTextField(
            value = slotMinutes,
            onValueChange = { slotMinutes = it.filter(Char::isDigit) },
            label = "Sitting length in minutes",
            helperText = "How long a table is held",
            keyboardType = KeyboardType.Number,
        )
        KulaTextField(
            value = priceBand,
            onValueChange = { priceBand = it.filter(Char::isDigit).take(1) },
            label = "Price band, 1 to 4",
            keyboardType = KeyboardType.Number,
        )

        PrimaryButton(
            text = "Save changes",
            loadingText = "Saving",
            loading = saving,
            enabled = name.isNotBlank() && capacity.isNotBlank() && slotMinutes.isNotBlank(),
            onClick = {
                onSave(
                    restaurant.copy(
                        name = name.trim(),
                        description = description.trim(),
                        cuisine = cuisine.trim(),
                        address = address.trim(),
                        phone = phone.trim(),
                        capacityPerSlot = capacity.toIntOrNull() ?: restaurant.capacityPerSlot,
                        slotDurationMinutes = slotMinutes.toIntOrNull()
                            ?: restaurant.slotDurationMinutes,
                        priceBand = priceBand.toIntOrNull()?.coerceIn(1, MAX_PRICE_BAND)
                            ?: restaurant.priceBand,
                    ),
                )
            },
        )
    }
}

private const val MAX_PRICE_BAND = 4
