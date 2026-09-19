package com.example.kulapro

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val HOME = "home"
    const val RESERVATIONS = "reservation"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    const val RESERVATION_FORM = "reservationForm/{restaurantId}/{restaurantName}"

    fun reservationForm(restaurantId: String, restaurantName: String): String {
        val encodedName = URLEncoder.encode(restaurantName, StandardCharsets.UTF_8.name())
        return "reservationForm/$restaurantId/$encodedName"
    }
}
