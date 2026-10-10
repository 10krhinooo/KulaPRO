package com.example.kulapro.util

/**
 * Field validation.
 *
 * The first version only checked isEmpty(), so "a" was an acceptable password and "not an
 * email" an acceptable address; the resulting failures surfaced as raw Firebase exception
 * text. Pure functions, so they are cheap to test exhaustively.
 */
object Validators {

    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    const val MIN_PASSWORD_LENGTH = 8

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Email is required"
        !EMAIL_PATTERN.matches(email.trim()) -> "Enter a valid email address"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "Password is required"
        password.length < MIN_PASSWORD_LENGTH ->
            "Password must be at least $MIN_PASSWORD_LENGTH characters"
        password.none { it.isDigit() } -> "Password must contain a number"
        password.none { it.isLetter() } -> "Password must contain a letter"
        else -> null
    }

    /** Sign-in only checks presence: an existing account's password rules may predate these. */
    fun signInPasswordError(password: String): String? =
        if (password.isEmpty()) "Password is required" else null

    fun partySizeError(raw: String): String? {
        if (raw.isBlank()) return "Number of guests is required"
        val value = raw.toIntOrNull() ?: return "Enter a whole number"
        return when {
            value < 1 -> "At least one guest is required"
            value > MAX_PARTY_SIZE -> "For parties over $MAX_PARTY_SIZE, call the restaurant"
            else -> null
        }
    }

    const val MAX_PARTY_SIZE = 20
}
