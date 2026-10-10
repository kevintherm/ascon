package com.ascon.engine.detection

import com.ascon.core.data.RuleHealthCount
import com.ascon.core.data.fake.FakeDeviceToken
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpRuleBackendTest {
    /** Every request the fake server got, as method, path and query, auth, body. */
    private val seen = mutableListOf<List<String?>>()
    private val replies = ArrayDeque<Pair<Int, String>>()
    private val tokens = FakeDeviceToken()

    private val server = Interceptor { chain ->
        val request: Request = chain.request()
        val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }
        seen +=
            listOf(
                "${request.method} ${request.url.encodedPath}${request.url.query?.let {
                    "?$it"
                } ?: ""}",
                request.header("Authorization"),
                body
            )
        val (code, json) = replies.removeFirst()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }

    private val backend = HttpRuleBackend(
        baseUrl = "https://api.example/v1/".toHttpUrl(),
        client = OkHttpClient.Builder().addInterceptor(server).build(),
        tokens = tokens,
        appVersion = "0.1.0"
    )

    private val match = """{"matchedBy":"domain","rule":{"payload":"cGF5","signature":"c2ln","keyId":"dev"}}"""

    @Test
    fun `the first call registers the device, and later calls reuse its token`() = runTest {
        replies += 201 to """{"deviceId":"d","token":"tok-1"}"""
        replies += 200 to match
        replies += 404 to """{"title":"Not found"}"""

        assertEquals(RuleAnswer.Found(SignedRule("cGF5", "c2ln", "dev")), backend.lookup("a.example", null))
        assertEquals(RuleAnswer.Missing, backend.lookup("b.example", "00ff00ff00ff00ff"))

        assertEquals(listOf("POST /v1/devices", null, """{"appVersion":"0.1.0"}"""), seen[0])
        assertEquals(listOf("GET /v1/rules?domain=a.example", "Bearer tok-1", null), seen[1])
        assertEquals("GET /v1/rules?domain=b.example&fingerprint=00ff00ff00ff00ff", seen[2][0])
        assertEquals("tok-1", tokens.value)
    }

    @Test
    fun `a token the server no longer knows is replaced once`() = runTest {
        tokens.value = "old"
        replies += 401 to "{}"
        replies += 201 to """{"deviceId":"d","token":"new"}"""
        replies += 200 to match

        backend.lookup("a.example", null)
        assertEquals(listOf("Bearer old", null, "Bearer new"), seen.map { it[1] })
    }

    @Test(expected = IOException::class)
    fun `a server error is a failed call, not a missing rule`() = runTest {
        tokens.value = "tok"
        replies += 500 to "{}"
        backend.lookup("a.example", null)
    }

    @Test
    fun `health counts go up in one batch`() = runTest {
        tokens.value = "tok"
        replies += 202 to ""
        backend.sendHealth(listOf(RuleHealthCount("a.example", 2, 3, 1, 0)))
        assertEquals(
            listOf(
                "POST /v1/rules/health",
                "Bearer tok",
                """{"entries":[{"domain":"a.example","version":2,"successes":3,"emptyResults":1,"backwardJumps":0}]}"""
            ),
            seen.single()
        )
    }
}
