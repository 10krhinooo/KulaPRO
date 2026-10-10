package com.example.kulapro.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Opens the phone app with a restaurant's number ready to dial.
 *
 * ACTION_DIAL rather than ACTION_CALL on purpose. ACTION_CALL places the call the instant
 * it is fired and needs the CALL_PHONE permission to do it, which is a lot to ask for a
 * number the user can see. This fills in the dialer and leaves the decision with them, and
 * needs no permission at all.
 *
 * Returns false when there is nothing to dial or nothing to dial with, so the caller can
 * say so rather than appearing to ignore the tap.
 */
fun openDialer(context: Context, phone: String): Boolean {
    val uri = dialUri(phone) ?: return false
    return try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
        true
    } catch (e: ActivityNotFoundException) {
        // A tablet with no dialer. Nothing to recover from, but the caller must be told.
        Log.i(TAG, "No app on this device handles dialling", e)
        false
    }
}

/**
 * The tel: URI for a number, or null when there is no number worth dialling.
 *
 * Kept to the characters a dialer understands. Anything a listing has wrapped around its
 * number, brackets, dashes, the word "call", is stripped rather than passed through, since
 * a tel: URI carrying them opens an empty dialer.
 */
internal fun dialUri(phone: String): String? {
    val dialable = phone.filter { it.isDigit() || it in DIALABLE_SYMBOLS }
    // A leading plus is the country code and has to survive; one anywhere else is noise.
    val normalised = if (dialable.startsWith('+')) {
        "+" + dialable.drop(1).filter { it != '+' }
    } else {
        dialable.filter { it != '+' }
    }

    val digits = normalised.count { it.isDigit() }
    return if (digits >= MIN_DIALABLE_DIGITS) "tel:$normalised" else null
}

/** Characters beyond digits that a dialer acts on: extensions, country codes, menu keys. */
private const val DIALABLE_SYMBOLS = "+#*,;"

/** Short codes exist, but fewer digits than this is a typo rather than a phone number. */
private const val MIN_DIALABLE_DIGITS = 3

private const val TAG = "PhoneLink"
