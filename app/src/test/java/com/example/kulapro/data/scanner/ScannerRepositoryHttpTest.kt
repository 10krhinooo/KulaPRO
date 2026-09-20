package com.example.kulapro.data.scanner

import com.example.kulapro.data.repository.Result
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GetTokenResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The proxy call, against a canned server.
 *
 * An interceptor rather than a mock web server: it needs no extra dependency and no port,
 * and it lets a test assert on the request that was actually built, which is where the
 * authorisation header either is or is not.
 */
@RunWith(RobolectricTestRunner::class)
class ScannerRepositoryHttpTest {

    private var lastRequest: okhttp3.Request? = null

    private fun clientReturning(code: Int, body: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    lastRequest = chain.request()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code < 400) "OK" else "Error")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                },
            )
            .build()

    private fun authWithToken(token: String? = "a-token"): FirebaseAuth {
        val user = mockk<FirebaseUser>()
        val result = mockk<GetTokenResult>()
        every { result.token } returns token
        every { user.getIdToken(any()) } returns Tasks.forResult(result)
        return mockk<FirebaseAuth>().also { every { it.currentUser } returns user }
    }

    private fun signedOutAuth(): FirebaseAuth =
        mockk<FirebaseAuth>().also { every { it.currentUser } returns null }

    @Test
    fun `a build with no proxy reports itself unavailable`() {
        val repository = ScannerRepositoryHttp("", signedOutAuth(), clientReturning(200, "{}"))

        assertEquals(false, repository.isAvailable)
    }

    @Test
    fun `a build with a proxy is available`() {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            signedOutAuth(),
            clientReturning(200, "{}"),
        )

        assertTrue(repository.isAvailable)
    }

    @Test
    fun `scanning without a proxy fails rather than calling nothing`() = runTest {
        val repository = ScannerRepositoryHttp("", authWithToken(), clientReturning(200, "{}"))

        val result = repository.scan("base64")

        assertTrue(result is Result.Failure)
    }

    @Test
    fun `a signed out user is asked to sign in, and nothing is sent`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            signedOutAuth(),
            clientReturning(200, "{}"),
        )

        val result = repository.scan("base64")

        assertTrue(result is Result.Failure)
        assertEquals(null, lastRequest)
    }

    @Test
    fun `a successful scan is read into a dish`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            authWithToken(),
            clientReturning(
                200,
                """{"is_food":true,"dish_name":"Ugali","calories_kcal":780.0}""",
            ),
        )

        val result = repository.scan("base64")

        assertTrue(result is Result.Success)
        assertEquals("Ugali", (result as Result.Success).data.dishName)
    }

    @Test
    fun `the caller's own token is sent, never a key of the app's`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            authWithToken("the-users-token"),
            clientReturning(200, """{"is_food":true}"""),
        )

        repository.scan("base64")

        assertEquals("Bearer the-users-token", lastRequest?.header("Authorization"))
    }

    @Test
    fun `a trailing slash on the proxy URL does not produce a double slash`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example/",
            authWithToken(),
            clientReturning(200, """{"is_food":true}"""),
        )

        repository.scan("base64")

        assertEquals("https://scanner.example/scan", lastRequest?.url.toString())
    }

    @Test
    fun `the proxy's own wording reaches the user`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            authWithToken(),
            clientReturning(429, LIMIT_REACHED_BODY),
        )

        val result = repository.scan("base64")

        assertEquals(
            "You have used all 25 scans for today.",
            (result as Result.Failure).message,
        )
    }

    @Test
    fun `an error the proxy did not shape still says something actionable`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            authWithToken(),
            clientReturning(502, "<html>Bad gateway</html>"),
        )

        val result = repository.scan("base64")

        assertTrue(result is Result.Failure)
        // Never the server's own body: a user cannot act on a gateway's HTML.
        assertEquals(
            "We could not read that photo. Try again.",
            (result as Result.Failure).message,
        )
    }

    @Test
    fun `a session that produced no token asks the user to sign in again`() = runTest {
        val repository = ScannerRepositoryHttp(
            "https://scanner.example",
            authWithToken(token = null),
            clientReturning(200, "{}"),
        )

        val result = repository.scan("base64")

        assertTrue(result is Result.Failure)
        assertEquals(null, lastRequest)
    }
}

private val LIMIT_REACHED_BODY = """
    {"error":{"code":"daily_limit_reached","message":"You have used all 25 scans for today."}}
""".trimIndent()
