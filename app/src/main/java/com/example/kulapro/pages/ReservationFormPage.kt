package com.example.kulapro.pages


import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ReservationFormScreen(
    onReservationSuccess: Modifier, navController: NavController, // Pass a lambda for success handling
    database: FirebaseDatabase = FirebaseDatabase.getInstance(),

) {
    val reservationsRef = remember { database.getReference("reservations") }

    var reservationDate by remember { mutableStateOf("") }
    var numberOfPax by remember { mutableStateOf("") }
    var selectedTimeSlot by remember { mutableStateOf("") }
    val timeSlots = listOf(
        "12:00 PM", "12:30 PM", "1:00 PM", "1:30 PM", "2:00 PM", "2:30 PM",
        "3:00 PM", "3:30 PM", "4:00 PM", "4:30 PM", "5:00 PM", "5:30 PM",
        "6:00 PM", "6:30 PM", "7:00 PM", "7:30 PM", "8:00 PM", "8:30 PM", "9:00 PM"
    )
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
                reservationDate = sdf.format(selectedDate.time)
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFF4AAF42)),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "KulaPro",
            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = reservationDate,
            onValueChange = { reservationDate = it },
            label = { Text("Reservation Date", color = Color.White) },
            placeholder = { Text("Select Date") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Pick a date", tint = Color.White)
                }
            }
        )

        DropdownMenuWithLabel(
            label = "Reservation Time Slot",
            options = timeSlots,
            selectedOption = selectedTimeSlot,
            onOptionSelected = { selectedTimeSlot = it }
        )

        OutlinedTextField(
            value = numberOfPax,
            onValueChange = { numberOfPax = it },
            label = { Text("Number of Pax", color = Color.White) },
            placeholder = { Text("Enter Number of Pax") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )

        Button(
            onClick = {
                if (reservationDate.isNotEmpty() && selectedTimeSlot.isNotEmpty() && numberOfPax.isNotEmpty()) {
                    val reservationId = reservationsRef.push().key
                    val reservation = Reservation(
                        restaurantName = "The Bistro",
                        reservationDate = reservationDate,
                        timeSlot = selectedTimeSlot,
                        numberOfPax = numberOfPax.toInt()
                    )
                    if (reservationId != null) {
                        reservationsRef.child(reservationId).setValue(reservation)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Reservation Successful!", Toast.LENGTH_SHORT).show()
                                onReservationSuccess
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Failed to make reservation. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        navController.navigate("home")
                    }
                } else {
                    Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit", color = Color.Black)
        }
    }
}

@Composable
fun DropdownMenuWithLabel(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, color = Color.White)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            TextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

data class Reservation(
    val restaurantName: String,
    val reservationDate: String,
    val timeSlot: String,
    val numberOfPax: Int
)
