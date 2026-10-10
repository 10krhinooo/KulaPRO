package com.example.kulapro.util

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

/**
 * Turns a failure into something worth showing.
 *
 * Two rules. Nothing that describes the inside of the system goes on screen: not
 * "PERMISSION_DENIED", not "Missing or insufficient permissions", not a coroutine's name.
 * And nothing is answered with "something went wrong" when the cause is actually known,
 * because a message that does not say what to do next is only an apology.
 */
fun userMessageFor(error: Throwable?): String = when (error) {
    null -> GENERIC

    is UserFacingException -> error.message ?: GENERIC

    is FirebaseFirestoreException -> firestoreMessage(error)

    is FirebaseAuthException -> authMessage(error)

    // No network at all, below the Firebase layer.
    is IOException -> "You appear to be offline. Reconnect and try again."

    else -> GENERIC
}

private fun firestoreMessage(error: FirebaseFirestoreException): String =
    when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "You are not allowed to do that. If this is your booking, sign in with the " +
                "account you made it on and try again."

        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Your session has ended. Sign in again to pick up where you left off."

        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "We cannot reach KulaPro right now. Check your connection and try again."

        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "That is taking longer than it should. Check your connection and try again."

        FirebaseFirestoreException.Code.NOT_FOUND ->
            "We could not find that any more. It may have been removed, so go back and " +
                "pick it again."

        // Someone else wrote the same document first. Retrying is genuinely the answer.
        FirebaseFirestoreException.Code.ABORTED,
        FirebaseFirestoreException.Code.ALREADY_EXISTS,
        ->
            "Someone got there just before you. Try that again."

        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
            "KulaPro is busier than usual. Give it a moment and try again."

        FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "That could not be saved as it stands. Go back, check your details and try again."

        else -> GENERIC
    }

private fun authMessage(error: FirebaseAuthException): String =
    when (error.errorCode) {
        "ERROR_INVALID_CREDENTIAL",
        "ERROR_WRONG_PASSWORD",
        "ERROR_INVALID_LOGIN_CREDENTIALS",
        ->
            "That email and password do not match. Check them, or reset your password."

        "ERROR_USER_NOT_FOUND" ->
            "No account uses that email. Check it for a typo, or create an account."

        "ERROR_EMAIL_ALREADY_IN_USE" ->
            "That email already has an account. Sign in instead, or reset the password."

        "ERROR_INVALID_EMAIL" ->
            "That does not look like an email address. Check it and try again."

        "ERROR_WEAK_PASSWORD" ->
            "That password is too easy to guess. Use at least eight characters with a " +
                "number or a symbol."

        "ERROR_REQUIRES_RECENT_LOGIN" ->
            "For your security, enter your current password again before changing this."

        "ERROR_TOO_MANY_REQUESTS" ->
            "Too many attempts from this device. Wait a few minutes and try again."

        "ERROR_USER_DISABLED" ->
            "That account has been disabled. Get in touch and we will look into it."

        "ERROR_NETWORK_REQUEST_FAILED" ->
            "We could not reach KulaPro. Check your connection and try again."

        "ERROR_OPERATION_NOT_ALLOWED" ->
            "That way of signing in is not available. Try your email and password instead."

        else -> GENERIC
    }

/**
 * The last resort, for a cause we genuinely do not recognise.
 *
 * Still says what to do next, because an error the user can do nothing about is worse than
 * no error at all.
 */
private const val GENERIC =
    "Something went wrong at our end. Try again, and if it keeps happening give it a few " +
        "minutes."
