package com.example.kulapro.util

import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessageTest {

    @Test
    fun `passes through a message written for the user`() {
        val message = "That table was taken while you were choosing. Pick another one."

        assertEquals(message, userMessageFor(UserFacingException(message)))
    }

    @Test
    fun `explains a permission failure in terms of the account`() {
        val message = userMessageFor(
            FirebaseFirestoreException(
                "PERMISSION_DENIED: Missing or insufficient permissions.",
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
            ),
        )

        assertTrue(message.contains("sign in", ignoreCase = true))
        assertLeaksNothing(message)
    }

    @Test
    fun `tells an offline user to reconnect`() {
        val message = userMessageFor(IOException("Unable to resolve host"))

        assertTrue(message.contains("offline", ignoreCase = true))
        assertLeaksNothing(message)
    }

    @Test
    fun `tells a user whose write lost a race to retry`() {
        val message = userMessageFor(
            FirebaseFirestoreException("aborted", FirebaseFirestoreException.Code.ABORTED),
        )

        assertTrue(message.contains("try that again", ignoreCase = true))
    }

    @Test
    fun `says what to do next even when the cause is unknown`() {
        val message = userMessageFor(IllegalStateException("kotlinx.coroutines.JobCancelled"))

        assertTrue(message.contains("try again", ignoreCase = true))
        assertLeaksNothing(message)
    }

    @Test
    fun `handles a missing cause`() {
        assertLeaksNothing(userMessageFor(null))
    }

    @Test
    fun `never leaks internals for any firestore code`() {
        // Every code, not a sample: a new one appearing must not start showing raw text.
        // OK is excluded because Firestore refuses to construct an exception for it.
        FirebaseFirestoreException.Code.entries
            .filterNot { it == FirebaseFirestoreException.Code.OK }
            .forEach { code ->
            val message = userMessageFor(
                FirebaseFirestoreException("Status{code=$code, description=internal}", code),
            )

            assertLeaksNothing(message)
            assertTrue("$code produced an empty message", message.isNotBlank())
        }
    }

    @Test
    fun `tells a user with the wrong password what to do about it`() {
        val message = userMessageFor(
            FirebaseAuthException("ERROR_WRONG_PASSWORD", "The password is invalid."),
        )

        assertTrue(message.contains("reset your password", ignoreCase = true))
        assertLeaksNothing(message)
    }

    @Test
    fun `points an unknown email at creating an account`() {
        val message = userMessageFor(
            FirebaseAuthException("ERROR_USER_NOT_FOUND", "no user record"),
        )

        assertTrue(message.contains("create an account", ignoreCase = true))
    }

    @Test
    fun `points a taken email at signing in instead`() {
        val message = userMessageFor(
            FirebaseAuthException("ERROR_EMAIL_ALREADY_IN_USE", "already in use"),
        )

        assertTrue(message.contains("sign in instead", ignoreCase = true))
    }

    @Test
    fun `says what a strong password looks like`() {
        val message = userMessageFor(
            FirebaseAuthException("ERROR_WEAK_PASSWORD", "too weak"),
        )

        assertTrue(message.contains("eight characters", ignoreCase = true))
    }

    @Test
    fun `explains why a recent login is being asked for`() {
        val message = userMessageFor(
            FirebaseAuthException("ERROR_REQUIRES_RECENT_LOGIN", "recent login required"),
        )

        assertTrue(message.contains("current password", ignoreCase = true))
    }

    @Test
    fun `tells a rate limited user to wait`() {
        val message = userMessageFor(
            FirebaseAuthException("ERROR_TOO_MANY_REQUESTS", "blocked"),
        )

        assertTrue(message.contains("wait a few minutes", ignoreCase = true))
    }

    @Test
    fun `never leaks internals for any auth code we handle`() {
        listOf(
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_INVALID_LOGIN_CREDENTIALS",
            "ERROR_INVALID_EMAIL",
            "ERROR_USER_DISABLED",
            "ERROR_NETWORK_REQUEST_FAILED",
            "ERROR_OPERATION_NOT_ALLOWED",
            "ERROR_SOMETHING_WE_HAVE_NEVER_SEEN",
        ).forEach { code ->
            val message = userMessageFor(FirebaseAuthException(code, "raw backend detail"))

            assertLeaksNothing(message)
            assertTrue("$code produced an empty message", message.isNotBlank())
        }
    }

    /** No backend vocabulary, no exception text, no stack frames. */
    private fun assertLeaksNothing(message: String) {
        listOf(
            "PERMISSION_DENIED",
            "ERROR_",
            "Exception",
            "com.google",
            "kotlinx",
            "Status{",
            "code=",
            "null",
        ).forEach { leak ->
            assertFalse(
                "message leaked \"$leak\": $message",
                message.contains(leak, ignoreCase = false),
            )
        }
    }
}
