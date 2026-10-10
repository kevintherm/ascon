package com.ascon.engine.detection

import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * The JSON messages bridge.js exchanges with the app. See the comment at the top of
 * bridge.js for the flow. Everything from the page is untrusted: page scripts share the
 * frame, so a message can be forged by the site itself. A forged message can only lie
 * about that same page, and the host checks its origin before using it.
 */

/** Messages from the page. */
@Serializable
internal sealed interface PageMessage {
    @Serializable
    @SerialName("page")
    data class Opened(val url: String) : PageMessage

    @Serializable
    @SerialName("result")
    data class Result(val url: String, val via: DetectionSource, val result: Evaluation) : PageMessage

    @Serializable
    @SerialName("position")
    data class Position(val url: String, val page: Int, val pageCount: Int) : PageMessage

    /** [href] is the link under a tap, or null for a tap elsewhere. */
    @Serializable
    @SerialName("tap")
    data class Tap(val url: String, val href: String? = null) : PageMessage
}

/** The evaluator's result, as contracts/README.md defines it. */
@Serializable
internal data class Evaluation(
    val pageType: String,
    val series: String? = null,
    val title: String? = null,
    val chapterLabel: String? = null,
    val chapter: String? = null,
    val images: List<String> = emptyList(),
    val next: String? = null,
    val previous: String? = null,
    val chapters: List<WireChapterLink> = emptyList()
)

@Serializable
internal data class WireChapterLink(val url: String, val label: String? = null, val number: String? = null)

/** One rule the page may try, in lookup order. */
@Serializable
data class RuleCandidate(
    val via: DetectionSource,
    /** A selector that must match before the rule is tried, for theme rules. */
    @SerialName("when") val requires: String? = null,
    val rule: JsonObject
) {
    companion object {
        fun forDomain(rule: JsonObject) = RuleCandidate(DetectionSource.Rule, rule = rule)
        fun builtIn(rule: JsonObject, requires: String) = RuleCandidate(DetectionSource.BuiltIn, requires, rule)
    }
}

/** The app's reply to [PageMessage.Opened]. */
@Serializable
internal data class RulesMessage(val rules: List<RuleCandidate>, val type: String = "rules")

/** Asks a chapter page to bring page [page] of [pageCount] on screen. */
@Serializable
internal data class ScrollMessage(val page: Int, val pageCount: Int, val type: String = "scroll")

internal object BridgeProtocol {
    /** Larger messages are dropped. A long chapter's image list fits well under this. */
    const val MAX_MESSAGE_CHARS = 512 * 1024
    private const val MAX_IMAGES = 2000
    private const val MAX_CHAPTERS = 5000

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    fun decode(data: String): PageMessage? {
        if (data.length > MAX_MESSAGE_CHARS) return null
        return try {
            json.decodeFromString(PageMessage.serializer(), data)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun encodeRules(rules: List<RuleCandidate>): String =
        json.encodeToString(RulesMessage.serializer(), RulesMessage(rules))

    fun encodeScroll(page: Int, pageCount: Int): String =
        json.encodeToString(ScrollMessage.serializer(), ScrollMessage(page, pageCount))

    fun toDetection(message: PageMessage.Result): Detection {
        val url = message.url
        val r = message.result
        return when (r.pageType) {
            "chapter" -> Detection.ChapterPage(
                url = url,
                source = message.via,
                seriesSlug = r.series,
                title = r.title,
                chapterLabel = r.chapterLabel,
                chapter = r.chapter.toChapterNumber(),
                images = r.images.filter(::isWebUrl).take(MAX_IMAGES),
                // Some themes point a missing link at "#", which resolves to the page itself.
                next = r.next?.takeIf { isWebUrl(it) && it != url.withoutFragment() },
                previous = r.previous?.takeIf { isWebUrl(it) && it != url.withoutFragment() }
            )
            "series" -> Detection.SeriesPage(
                url = url,
                source = message.via,
                seriesSlug = r.series,
                title = r.title,
                chapters = r.chapters
                    .filter { isWebUrl(it.url) }
                    .take(MAX_CHAPTERS)
                    .map { ChapterLink(it.url, it.label, it.number.toChapterNumber()) }
            )
            else -> Detection.None(url)
        }
    }

    /** The tapped link, if it is a web page. */
    fun tappedLink(message: PageMessage.Tap): String? = message.href?.takeIf(::isWebUrl)

    /** Null for a page outside the chapter or a count no chapter has. */
    fun toPosition(message: PageMessage.Position): Detection.ReadingPosition? =
        if (message.pageCount in 1..MAX_IMAGES && message.page in 1..message.pageCount) {
            Detection.ReadingPosition(message.url, message.page, message.pageCount)
        } else {
            null
        }

    /**
     * The origin of [url] as WebView reports a message's source origin, such as
     * `https://example.com` or `http://example.com:8080`. Null for anything but http and https.
     */
    fun originOf(url: String): String? {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            null
        }
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host?.lowercase()
        if (host == null || (scheme != "http" && scheme != "https")) return null
        val defaultPort = if (scheme == "https") HTTPS_PORT else HTTP_PORT
        val port = if (uri.port == -1 || uri.port == defaultPort) "" else ":${uri.port}"
        return "$scheme://$host$port"
    }

    fun isWebUrl(url: String): Boolean = originOf(url) != null

    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
}

/** Drops a `#fragment`, which never changes which page a URL is. */
fun String.withoutFragment(): String = substringBefore('#')

private fun String?.toChapterNumber(): BigDecimal? = this?.toBigDecimalOrNull()
