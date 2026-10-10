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
    const val OWNER = "owner/{restaurantId}"
    const val ADMIN = "admin"
    const val RESTAURANT = "restaurant/{restaurantId}"
    const val CLAIM_RESTAURANT = "claim/{restaurantId}/{restaurantName}"
    const val LIST_RESTAURANT = "listRestaurant"
    const val SEARCH = "search"
    /** One booking in full. Named "booking" so it cannot collide with the tab route. */
    const val BOOKING = "booking/{reservationId}"
    const val SCANNER = "scanner?restaurantId={restaurantId}&menuItemId={menuItemId}"

    fun claimRestaurant(restaurantId: String, restaurantName: String): String =
        "claim/$restaurantId/${encode(restaurantName)}"

    fun restaurant(restaurantId: String): String = "restaurant/$restaurantId"

    fun booking(reservationId: String): String = "booking/$reservationId"

    /**
     * The restaurant side, for one restaurant.
     *
     * The id is required. Running the platform is a separate view with its own route, so
     * there is no longer such a thing as the restaurant portal without a restaurant.
     */
    fun owner(restaurantId: String): String = "owner/$restaurantId"

    /**
     * The scanner, optionally asking about a particular dish on a menu.
     *
     * With a dish named, a menu entry that already carries nutrition answers immediately
     * and nothing is sent anywhere. Without one, it is the camera.
     */
    fun scanner(restaurantId: String = "", menuItemId: String = ""): String =
        if (restaurantId.isBlank() || menuItemId.isBlank()) {
            "scanner"
        } else {
            "scanner?restaurantId=$restaurantId&menuItemId=$menuItemId"
        }

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
