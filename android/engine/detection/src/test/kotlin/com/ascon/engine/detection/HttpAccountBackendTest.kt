package com.ascon.engine.detection

import com.ascon.core.data.AccountRefused
import com.ascon.core.model.AccountSession
import com.ascon.core.model.Quota
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpAccountBackendTest {
    /** Every request the fake server got, as method and path, auth, body. */
    private val seen = mutableListOf<List<String?>>()
    private val replies = ArrayDeque<Pair<Int, String>>()

    private val server = Interceptor { chain ->
        val request = chain.request()
        seen += listOf(
            "${request.method} ${request.url.encodedPath}",
            request.header("Authorization"),
            request.body?.let { Buffer().also(it::writeTo).readUtf8() }
        )
        val (code, json) = replies.removeFirst()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }

    private val backend = HttpAccountBackend(
        "https://api.example/v1/".toHttpUrl(),
        OkHttpClient.Builder().addInterceptor(server).build()
    )

    @Test
    fun `signing in sends the ID token and reads the session`() = runTest {
        replies += 201 to """{"accountId":"a-1","token":"acc-1","tier":"premium"}"""

        assertEquals(AccountSession("acc-1", "", null, premium = true), backend.signIn("google-jwt"))
        assertEquals(listOf("POST /v1/sessions", null, """{"idToken":"google-jwt"}"""), seen.single())
    }

    @Test
    fun `quota and sign-out carry the account token`() = runTest {
        replies += 200 to """{"tier":"free","limit":10,"remaining":7,"resetsAt":"2026-11-01T00:00:00Z"}"""
        replies += 204 to ""

        assertEquals(Quota(10, 7, Instant.parse("2026-11-01T00:00:00Z"), premium = false), backend.quota("acc-1"))
        backend.signOut("acc-1")
        assertEquals(
            listOf(
                listOf("GET /v1/quota", "Bearer acc-1", null),
                listOf("DELETE /v1/sessions/current", "Bearer acc-1", "")
            ),
            seen
        )
    }

    @Test
    fun `refusals and failures are told apart`() = runTest {
        replies += 401 to """{"title":"Missing or invalid token"}"""
        replies += 503 to ""
        replies += 401 to ""
        replies += 401 to ""

        assertTrue(runCatching { backend.signIn("forged") }.exceptionOrNull() is AccountRefused)
        assertTrue(runCatching { backend.signIn("x") }.exceptionOrNull() is IOException)
        assertTrue(runCatching { backend.quota("old") }.exceptionOrNull() is AccountRefused)
        // A token the backend no longer knows is already signed out.
        backend.signOut("old")
    }
}
