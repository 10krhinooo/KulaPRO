package com.example.kulapro.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.GeoPoint

/**
 * Opens a restaurant's pin in whatever map app the phone has.
 *
 * A geo: intent rather than an embedded map, because it needs no Maps API key, no billing
 * account and no key restriction to get wrong, and it hands the user to an app that already
 * knows where they are and how they like to travel. An embedded map is the right answer for
 * browsing many restaurants at once; for one address it is a worse version of this.
 *
 * Returns false when there is no map app to open, so the caller can say so rather than
 * having nothing happen.
 */
fun openMapPin(
    context: Context,
    location: GeoPoint?,
    label: String,
    address: String,
): Boolean {
    val uri = mapUri(location, label, address) ?: return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        true
    } catch (e: ActivityNotFoundException) {
        // A device with no map app at all. There is nothing to recover from and nothing
        // worth logging, but the caller has to be told so it can say so rather than
        // appearing to ignore the tap.
        Log.i(TAG, "No app on this device handles a map pin", e)
        false
    }
}

/**
 * The geo: URI for a pin.
 *
 * Coordinates are preferred because they land exactly on the restaurant. A listing with no
 * coordinates falls back to searching its written address, which is approximate but still
 * better than refusing. A listing with neither gets nothing, and the caller hides the
 * control rather than offering one that cannot work.
 */
internal fun mapUri(location: GeoPoint?, label: String, address: String): String? {
    // Uri.encode leaves brackets alone, but the label in a geo: URI is delimited by them,
    // so "Fish & Chips (Karen)" would close the label early and leave a stray bracket in
    // the query. They are escaped by hand for that reason.
    val encodedLabel = Uri.encode(label.ifBlank { address })
        .replace("(", "%28")
        .replace(")", "%29")
    return when {
        location != null -> {
            val lat = location.latitude
            val lng = location.longitude
            "geo:$lat,$lng?q=$lat,$lng($encodedLabel)"
        }

        address.isNotBlank() -> "geo:0,0?q=${Uri.encode(address)}"

        else -> null
    }
}

private const val TAG = "MapLink"
