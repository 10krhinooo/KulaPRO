package com.example.kulapro.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `blank email is rejected`() {
        assertEquals("Email is required", Validators.emailError(""))
        assertEquals("Email is required", Validators.emailError("   "))
    }

    @Test
    fun `malformed emails are rejected`() {
        listOf(
            "not-an-email",
            "missing@tld",
            "@nolocal.com",
            "spaces in@example.com",
            "trailing@example.",
        ).forEach {
            assertEquals("Enter a valid email address", Validators.emailError(it))
        }
    }

    @Test
    fun `valid emails are accepted`() {
        listOf(
            "a@b.co",
            "victor.moruri@apeiro.digital",
            "user+tag@sub.domain.org",
        ).forEach { assertNull("expected $it to be valid", Validators.emailError(it)) }
    }

    @Test
    fun `surrounding whitespace does not invalidate an email`() {
        assertNull(Validators.emailError("  user@example.com  "))
    }

    @Test
    fun `password must meet every rule`() {
        assertEquals("Password is required", Validators.passwordError(""))
        assertEquals(
            "Password must be at least 8 characters",
            Validators.passwordError("ab1"),
        )
        assertEquals(
            "Password must contain a number",
            Validators.passwordError("abcdefghi"),
        )
        assertEquals(
            "Password must contain a letter",
            Validators.passwordError("123456789"),
        )
        assertNull(Validators.passwordError("correct1horse"))
    }

    @Test
    fun `sign in only requires a non-empty password`() {
        // An existing account's password may predate the current rules, so sign-in must not
        // apply them or it would lock out valid users.
        assertNull(Validators.signInPasswordError("old"))
        assertEquals("Password is required", Validators.signInPasswordError(""))
    }

    @Test
    fun `party size must be a sensible whole number`() {
        assertEquals("Number of guests is required", Validators.partySizeError(" "))
        assertEquals("Enter a whole number", Validators.partySizeError("two"))
        assertEquals("Enter a whole number", Validators.partySizeError("2.5"))
        assertEquals("At least one guest is required", Validators.partySizeError("0"))
        assertEquals("At least one guest is required", Validators.partySizeError("-3"))
        assertEquals(
            "For parties over 20, call the restaurant",
            Validators.partySizeError("21"),
        )
        assertNull(Validators.partySizeError("1"))
        assertNull(Validators.partySizeError("20"))
    }
}
