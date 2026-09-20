package com.example.kulapro.data.repository

import org.junit.Assert.assertEquals
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
    fun `reports a thrown error as failure with its message`() {
        val result = runCatchingFirestore<String> { error("Missing or insufficient permissions") }

        assertTrue(result is Result.Failure)
        assertEquals("Missing or insufficient permissions", (result as Result.Failure).message)
    }

    @Test
    fun `falls back to a readable message when the error carries none`() {
        // Firestore raises plenty of exceptions with a null message, and "null" is not
        // something to put in front of a diner.
        val result = runCatchingFirestore<String> { throw IllegalArgumentException() }

        assertEquals("Could not reach the server", (result as Result.Failure).message)
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
