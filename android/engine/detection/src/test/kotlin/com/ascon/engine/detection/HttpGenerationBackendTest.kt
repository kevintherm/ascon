package com.ascon.engine.detection

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

class HttpGenerationBackendTest {
    /** Every request the fake server got, as method and path, auth, body. */
    private val seen = mutableListOf<List<String?>>()
    private val replies = ArrayDeque<Pair<Int, String>>()
    private var token: String? = "acc-1"

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

    private val backend = HttpGenerationBackend(
        "https://api.example/v1/".toHttpUrl(),
        OkHttpClient.Builder().addInterceptor(server).build()
    ) { token }

    private val snapshot = PageSnapshot("https://a.example/s/ch-1", "<p>1</p>")

    @Test
    fun `a request sends the samples with the account token and reads the candidate`() = runTest {
        replies += 202 to """{"candidateId":"c-1","domain":"a.example","status":"pending","retryAfter":4}"""
        replies += 200 to """{"candidateId":"c-1","domain":"a.example","status":"accepted",
            "rule":{"payload":"cGF5","signature":"c2ln","keyId":"dev"}}"""
        replies += 200 to """{"candidateId":"c-1","domain":"a.example","status":"rejected","reason":"no title"}"""

        assertEquals(
            CandidateState.Pending("c-1", 4),
            backend.request("a.example", "00ff00ff00ff00ff", listOf(snapshot))
        )
        assertEquals(CandidateState.Accepted(SignedRule("cGF5", "c2ln", "dev")), backend.candidate("c-1"))
        assertEquals(CandidateState.Rejected("no title"), backend.candidate("c-1"))

        assertEquals(
            listOf(
                "POST /v1/rule-candidates",
                "Bearer acc-1",
                """{"domain":"a.example","fingerprint":"00ff00ff00ff00ff",""" +
                    """"samples":[{"url":"https://a.example/s/ch-1","html":"<p>1</p>"}]}"""
            ),
            seen[0]
        )
        assertEquals(listOf("GET /v1/rule-candidates/c-1", "Bearer acc-1", null), seen[1])
    }

    @Test
    fun `an existing rule and a used-up quota are answers, not failures`() = runTest {
        replies += 409 to """{"title":"Conflict","status":409}"""
        replies += 429 to """{"title":"Quota","status":429,"quota":{"tier":"free","limit":10,"remaining":0,
            "resetsAt":"2026-11-01T00:00:00Z"}}"""

        assertEquals(CandidateState.RuleExists, backend.request("a.example", null, listOf(snapshot)))
        assertEquals(
            CandidateState.OutOfQuota(Instant.parse("2026-11-01T00:00:00Z")),
            backend.request("a.example", null, listOf(snapshot))
        )
    }

    @Test
    fun `a refused token or no account fails the call`() = runTest {
        replies += 401 to """{"title":"Unauthorized","status":401}"""
        assertTrue(runCatching { backend.candidate("c-1") }.exceptionOrNull() is IOException)

        token = null
        assertTrue(runCatching { backend.candidate("c-1") }.exceptionOrNull() is IOException)
        assertEquals(1, seen.size)
    }
}
