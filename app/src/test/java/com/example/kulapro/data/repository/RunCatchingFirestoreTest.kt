package com.example.kulapro.data.repository

import com.example.kulapro.util.UserFacingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RunCatchingFirestoreTest {

    @Test
    fun `wraps a returned value in success`() {
        val result = runCatchingFirestore { "booked" }

        assertTrue(result is Result.Success)
        assertEquals("booked", (result as Result.Success).data)
    }

    @Test
    fun `shows a message written for the user rather than the exception text`() {
        val result = runCatchingFirestore<String> {
            throw UserFacingException("That table was taken. Pick another one.")
        }

        assertTrue(result is Result.Failure)
        assertEquals("That table was taken. Pick another one.", (result as Result.Failure).message)
    }

    @Test
    fun `never puts raw exception text in front of the user`() {
        // Firestore messages read like "PERMISSION_DENIED: Missing or insufficient
        // permissions", which tells a diner nothing and exposes the inside of the system.
        val result = runCatchingFirestore<String> {
            error("PERMISSION_DENIED: Missing or insufficient permissions")
        }

        val message = (result as Result.Failure).message
        assertFalse(message.contains("PERMISSION_DENIED"))
        assertTrue(message.contains("try again", ignoreCase = true))
    }

    @Test
    fun `keeps the cause for logging even though it is not shown`() {
        val cause = IllegalArgumentException("internal detail")

        val result = runCatchingFirestore<String> { throw cause }

        assertEquals(cause, (result as Result.Failure).cause)
    }

    @Test
    fun `rethrows cancellation rather than reporting it`() {
        // Changing the booking date twice quickly cancels the first query. Catching that
        // cancellation showed the user an error for work they had already moved on from,
        // and left the cancelled coroutine believing it should carry on.
        assertThrows(CancellationException::class.java) {
            runCatchingFirestore<String> { throw CancellationException("left composition") }
        }
    }
}
