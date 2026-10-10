package com.ascon.engine.detection

import com.ascon.core.data.AccountRefused
import com.ascon.core.data.SyncCursorExpired
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Stamped
import com.ascon.core.model.SyncRecord
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpSyncBackendTest {
    private val seen = mutableListOf<List<String?>>()
    private val replies = ArrayDeque<Pair<Int, String>>()

    private val server = Interceptor { chain ->
        val request = chain.request()
        seen += listOf(
            "${request.method} ${request.url.encodedPath}?${request.url.encodedQuery}",
            request.header("Authorization"),
            request.body?.let { Buffer().also(it::writeTo).readUtf8() }
        )
        val (code, json) = replies.removeFirst()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
            .body(json.toResponseBody("application/json".toMediaType())).build()
    }

    private val backend = HttpSyncBackend(
        "https://api.example/v1/".toHttpUrl(),
        OkHttpClient.Builder().addInterceptor(server).build()
    )

    private val at = Instant.parse("2026-10-10T08:00:00.123Z")

    private val progress = SyncRecord.Progress(
        id = "p-1",
        seriesId = Stamped("s-1", at),
        chapter = Stamped(BigDecimal("12.50"), at),
        page = Stamped(5, at),
        pageCount = Stamped(20, at),
        pageOffset = Stamped(0.5f, at),
        readAt = Stamped(at, at)
    )

    @Test
    fun `a push sends each field with its time`() = runTest {
        replies += 200 to """{"applied":7}"""
        backend.push(
            "acc-1",
            listOf(progress, SyncRecord.Entry("e-1", Stamped("s-1", at), Stamped(ReadingStatus.Plan, at)))
        )

        val (line, auth, body) = seen.single()
        assertEquals("POST /v1/sync/changes?null", line)
        assertEquals("Bearer acc-1", auth)
        val change = Json.parseToJsonElement(body!!).let { (it as JsonObject)["changes"].toString() }
        assertTrue(change, change.contains(""""chapter":{"value":"12.5","updatedAt":"2026-10-10T08:00:00.123Z"}"""))
        assertTrue(change, change.contains(""""pagePosition":{"value":0.225"""))
        assertTrue(change, change.contains(""""status":{"value":"planned""""))
    }

    @Test
    fun `a pull reads the page and the records in it`() = runTest {
        replies += 200 to """
            {"cursor":"c-2","hasMore":true,"changes":[
              {"entity":"progress","id":"p-1","fields":{"page":{"value":5,"updatedAt":"2026-10-10T08:00:00.123Z"}}},
              {"entity":"libraryEntry","id":"e-1","fields":{"status":{"value":"dropped","updatedAt":"2026-10-10T08:00:00.123Z"}}},
              {"entity":"series","id":"s-9","deleted":{"updatedAt":"2026-10-10T08:00:00Z"}},
              {"entity":"other","id":"x"}
            ]}
        """.trimIndent()

        val page = backend.pull("acc-1", "c-1")
        assertEquals("GET /v1/sync/changes?since=c-1&limit=500", seen.single()[0])
        assertEquals("c-2", page.cursor)
        assertTrue(page.hasMore)
        assertEquals(
            listOf(
                SyncRecord.Progress("p-1", null, null, Stamped(5, at), null, null, null),
                SyncRecord.Entry("e-1", null, Stamped(ReadingStatus.Paused, at)),
                SyncRecord.SeriesRecord("s-9", null)
            ),
            page.records
        )
    }

    @Test
    fun `a refused token and an old cursor are told apart`() = runTest {
        replies += 401 to ""
        replies += 410 to ""
        assertThrows(AccountRefused::class.java) { kotlinx.coroutines.runBlocking { backend.pull("acc-1", null) } }
        assertThrows(SyncCursorExpired::class.java) { kotlinx.coroutines.runBlocking { backend.pull("acc-1", "old") } }
    }
}
