package com.ascon.engine.detection

import java.math.BigDecimal
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeProtocolTest {
    private val page = "https://lumen.example/glass-orchard-chapter-3/"

    @Test
    fun `reads a chapter result`() {
        val message = BridgeProtocol.decode(
            """
            {"type":"result","url":"$page","via":"builtin","result":{
              "pageType":"chapter","series":"glass-orchard","title":"Glass Orchard",
              "chapterLabel":"Glass Orchard Chapter 3","chapter":"3",
              "images":["https://img.lumen.example/3/1.jpg","javascript:alert(1)"],
              "next":"$page","previous":"https://lumen.example/glass-orchard-chapter-2/"}}
            """.trimIndent()
        ) as PageMessage.Result

        assertEquals(
            Detection.ChapterPage(
                url = page,
                source = DetectionSource.BuiltIn,
                seriesSlug = "glass-orchard",
                title = "Glass Orchard",
                chapterLabel = "Glass Orchard Chapter 3",
                chapter = BigDecimal("3"),
                images = listOf("https://img.lumen.example/3/1.jpg"),
                // A next link that points back at the page is a theme placeholder.
                next = null,
                previous = "https://lumen.example/glass-orchard-chapter-2/"
            ),
            BridgeProtocol.toDetection(message)
        )
    }

    @Test
    fun `reads a series result with decimal chapters`() {
        val message = BridgeProtocol.decode(
            """
            {"type":"result","url":"https://t.example/manga/a/","via":"rule","result":{
              "pageType":"series","series":"a","title":"A","chapters":[
                {"url":"https://t.example/manga/a/chapter-11-5/","label":"Chapter 11.5","number":"11.5"},
                {"url":"https://t.example/manga/a/extra/","label":"Extra","number":null}]}}
            """.trimIndent()
        ) as PageMessage.Result

        val detection = BridgeProtocol.toDetection(message) as Detection.SeriesPage
        assertEquals(DetectionSource.Rule, detection.source)
        assertEquals(listOf(BigDecimal("11.5"), null), detection.chapters.map { it.number })
    }

    @Test
    fun `anything else is no page`() {
        val message = BridgeProtocol.decode(
            """{"type":"result","url":"$page","via":"rule","result":{"pageType":"none"}}"""
        ) as PageMessage.Result
        assertEquals(Detection.None(page), BridgeProtocol.toDetection(message))
    }

    @Test
    fun `drops malformed and oversized messages`() {
        assertNull(BridgeProtocol.decode("not json"))
        assertNull(BridgeProtocol.decode("""{"type":"surprise"}"""))
        assertNull(BridgeProtocol.decode("""{"type":"page"}"""))
        assertNull(BridgeProtocol.decode("\"" + "x".repeat(BridgeProtocol.MAX_MESSAGE_CHARS) + "\""))
    }

    @Test
    fun `origins match what WebView reports`() {
        assertEquals("https://example.com", BridgeProtocol.originOf("https://Example.com/a?b#c"))
        assertEquals("https://example.com", BridgeProtocol.originOf("https://example.com:443/"))
        assertEquals("http://example.com:8080", BridgeProtocol.originOf("http://example.com:8080/x"))
        assertNull(BridgeProtocol.originOf("file:///sdcard/a.html"))
        assertNull(BridgeProtocol.originOf("data:text/html,hi"))
        assertNull(BridgeProtocol.originOf("not a url"))
    }

    @Test
    fun `a scroll names the page, how many pages it is counted against and how far down it`() {
        assertEquals(
            """{"page":9,"pageCount":20,"offset":0.5,"type":"scroll"}""",
            BridgeProtocol.encodeScroll(9, 20, 0.5f)
        )
    }

    @Test
    fun `rules go to the page with their marker under when`() {
        val rule = buildJsonObject { put("domain", JsonPrimitive("t.example")) }
        val encoded = BridgeProtocol.json.parseToJsonElement(
            BridgeProtocol.encodeRules(listOf(RuleCandidate.builtIn(rule, ".reader")))
        ).jsonObject

        assertEquals("rules", encoded["type"]!!.jsonPrimitive.content)
        assertEquals(
            """[{"via":"builtin","when":".reader","rule":{"domain":"t.example"}}]""",
            encoded["rules"].toString()
        )
    }
}

class PageMessagesTest {
    private val chapterUrl = "https://lumen.example/glass-orchard-chapter-3/"

    @Test
    fun `reads a reading position`() {
        val message = BridgeProtocol.decode("""{"type":"position","url":"$chapterUrl","page":4,"pageCount":56}""")
        assertEquals(
            Detection.ReadingPosition(chapterUrl, page = 4, pageCount = 56),
            BridgeProtocol.toPosition(message as PageMessage.Position)
        )
    }

    @Test
    fun `a position keeps how far down the page it is, and only from 0 up to 1`() {
        fun offset(value: String) = BridgeProtocol.toPosition(
            BridgeProtocol.decode(
                """{"type":"position","url":"$chapterUrl","page":4,"pageCount":56,"offset":$value}"""
            ) as PageMessage.Position
        )?.offset
        assertEquals(0.62f, offset("0.62"))
        assertEquals(0f, offset("1.5"))
        assertEquals(0f, offset("-0.2"))
    }

    @Test
    fun `reads a tap and keeps only web links`() {
        fun tap(href: String?) = BridgeProtocol.tappedLink(
            BridgeProtocol.decode(
                """{"type":"tap","url":"$chapterUrl","href":${href?.let { "\"$it\"" } ?: "null"}}"""
            ) as PageMessage.Tap
        )
        assertEquals("https://lumen.example/next/", tap("https://lumen.example/next/"))
        assertEquals(null, tap("javascript:void(0)"))
        assertEquals(null, tap(null))
    }

    @Test
    fun `a structure says how its chapter was found`() {
        val message = BridgeProtocol.decode(
            """{"type":"structure","url":"$chapterUrl","via":"heuristic","features":["a"]}"""
        ) as PageMessage.Structure
        assertEquals(DetectionSource.Heuristic, message.via)
    }

    @Test
    fun `a snapshot needs a chapter and fits the backend's limit`() {
        fun snapshot(chapter: String, html: String) = BridgeProtocol.toSnapshot(
            BridgeProtocol.decode(
                BridgeProtocol.json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", JsonPrimitive("snapshot"))
                        put("url", JsonPrimitive("$chapterUrl#top"))
                        put("chapter", JsonPrimitive(chapter))
                        put("html", JsonPrimitive(html))
                    }
                )
            ) as PageMessage.Snapshot
        )
        assertEquals(BigDecimal("3") to PageSnapshot(chapterUrl, "<p>x</p>"), snapshot("3", "<p>x</p>"))
        assertNull(snapshot("none", "<p>x</p>"))
        assertNull(snapshot("3", ""))
        assertNull(snapshot("3", "\u00e9".repeat(140_000)))
        assertEquals("""{"type":"snapshot"}""", BridgeProtocol.encodeSnapshotRequest())
    }

    @Test
    fun `drops a position outside the chapter`() {
        listOf(0 to 10, 11 to 10, 1 to 0, 1 to 100_000).forEach { (p, count) ->
            val message = BridgeProtocol.decode(
                """{"type":"position","url":"$chapterUrl","page":$p,"pageCount":$count}"""
            )
            assertEquals(null, BridgeProtocol.toPosition(message as PageMessage.Position))
        }
    }
}
