package com.example.kulapro

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val LOGIN = "login?next={next}"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val HOME = "home"
    const val RESERVATIONS = "reservation"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val OWNER = "owner?restaurantId={restaurantId}"
    const val RESTAURANT = "restaurant/{restaurantId}"
    const val CLAIM_RESTAURANT = "claim/{restaurantId}/{restaurantName}"
    const val LIST_RESTAURANT = "listRestaurant"
    const val OWNERSHIP_REVIEW = "ownershipReview"

    fun claimRestaurant(restaurantId: String, restaurantName: String): String =
        "claim/$restaurantId/${encode(restaurantName)}"

    fun restaurant(restaurantId: String): String = "restaurant/$restaurantId"

    /**
     * The admin side, optionally for a particular restaurant.
     *
     * The id is optional because a platform reviewer who hosts nothing still has an admin
     * side: the ownership requests waiting on them.
     */
    fun owner(restaurantId: String): String =
        if (restaurantId.isBlank()) "owner" else "owner?restaurantId=$restaurantId"

    const val RESERVATION_FORM = "reservationForm/{restaurantId}/{restaurantName}"

    /**
     * Sign-in, optionally remembering where the user was heading.
     *
     * Guests browse freely and only meet the sign-in screen when they try to book, so the
     * screen has to be able to hand them back to what they were doing.
     */
    fun login(next: String? = null): String =
        if (next == null) "login" else "login?next=${encode(next)}"

    fun reservationForm(restaurantId: String, restaurantName: String): String {
        return "reservationForm/$restaurantId/${encode(restaurantName)}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
