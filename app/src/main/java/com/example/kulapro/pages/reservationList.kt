package com.example.kulapro.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ReservationScreen(navController: NavController) {
    // Sample reservations data
    val reservations = listOf(
        Reservation("The Bistro","2024-12-01", "7:00 PM", 4),
        Reservation("Sushi","2024-12-02", "6:00 PM", 2),
        Reservation("Grill","2024-12-03", "8:00 PM", 6)
    )

    // Scaffold for the Reservation Screen
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Your Reservations", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        ReservationList(reservations)
    }
}

@Composable
fun ReservationList(reservations: List<Reservation>) {
    LazyColumn {
        items(reservations) { reservation ->
            ReservationItem(reservation)
        }
    }
}

@Composable
fun ReservationItem(reservation: Reservation) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Date: ${reservation.restaurantName}")
        Text(text = "Date: ${reservation.reservationDate}")
        Text(text = "Time: ${reservation.timeSlot}")
        Text(text = "Number of people: ${reservation.numberOfPax}")
        Divider(modifier = Modifier.padding(top = 8.dp))
    }
}



