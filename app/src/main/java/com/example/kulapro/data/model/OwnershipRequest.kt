package com.example.kulapro.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * A user asking to be put in charge of a restaurant.
 *
 * Two shapes, one document. [RequestType.CLAIM] points at a restaurant already listed;
 * [RequestType.NEW_LISTING] carries the details of one that is not, and the restaurant is
 * only created if the request is approved. Keeping unapproved listings out of the
 * restaurants collection entirely means there is nothing for a diner to stumble across and
 * nothing to remember to filter out.
 *
 * [evidence] is what the reviewer actually decides on. Nothing here is verified
 * automatically, and it is not meant to be: an approval is a human saying they recognise
 * this person as the restaurant.
 */
data class OwnershipRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val userName: String = "",
    val type: String = RequestType.CLAIM.name,

    /** Set for a claim on an existing listing. */
    val restaurantId: String = "",
    val restaurantName: String = "",

    /** Set for a new listing, and copied onto the restaurant when approved. */
    val proposed: ProposedRestaurant? = null,

    /** How the requester says they are connected to the restaurant. */
    val role: String = "",
    val contactPhone: String = "",
    val evidence: String = "",

    val status: String = RequestStatus.PENDING.name,
    val createdAt: Timestamp = Timestamp.now(),
    val reviewedAt: Timestamp? = null,
    val reviewedBy: String = "",

    /** Shown back to the requester, so a rejection is never silent. */
    val reviewNote: String = "",
) {
    val typeEnum: RequestType
        get() = RequestType.entries.firstOrNull { it.name == type } ?: RequestType.CLAIM

    val statusEnum: RequestStatus
        get() = RequestStatus.entries.firstOrNull { it.name == status } ?: RequestStatus.PENDING

    /**
     * Someone to address, whatever the account has filled in.
     *
     * A user who signed up with email and password has no display name, and a reviewer
     * deciding on "()" learns nothing about who is asking.
     */
    val displayName: String
        get() = userName.ifBlank { userEmail.substringBefore('@').ifBlank { "A user" } }
}

/** The restaurant a new listing request is asking for. */
data class ProposedRestaurant(
    val name: String = "",
    val description: String = "",
    val cuisine: String = "",
    val address: String = "",
    val phone: String = "",
    val priceBand: Int = 2,
    val capacityPerSlot: Int = 0,
)

enum class RequestType { CLAIM, NEW_LISTING }

enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    ;

    /** A settled request cannot be reviewed again, and a new one must be raised instead. */
    val isOpen: Boolean get() = this == PENDING
}
