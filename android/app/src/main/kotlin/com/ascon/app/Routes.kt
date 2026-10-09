package com.ascon.app

import androidx.navigation3.runtime.NavKey
import com.ascon.core.model.ReaderChapter
import java.math.BigDecimal
import kotlinx.serialization.Serializable

/**
 * Destinations on the back stack. The tabs are not here: they live in [TabHost] under the
 * back stack, which holds only [Root] and the detail screens pushed over the tabs.
 */
sealed interface Route : NavKey {
    /** The tabs. Always at the bottom of the stack and drawn by [TabHost], not by an entry. */
    @Serializable
    data object Root : Route

    @Serializable
    data class Series(val id: String) : Route

    /** A browser tab opened at [url]. */
    @Serializable
    data class Browser(val url: String) : Route

    /** The native reader over the browser tab that found the chapter. [chapter] is a decimal string. */
    @Serializable
    data class Reader(
        val url: String,
        val title: String?,
        val chapter: String?,
        val seriesId: String?,
        val pages: List<String>,
        val next: String?,
        val previous: String?
    ) : Route {
        fun toChapter() = ReaderChapter(url, title, chapter?.let(::BigDecimal), seriesId, pages, next, previous)

        companion object {
            fun of(c: ReaderChapter) =
                Reader(c.url, c.title, c.chapter?.toPlainString(), c.seriesId, c.pages, c.next, c.previous)
        }
    }
}

/** The floating nav's tabs, in order. The order is also the direction tabs slide. */
enum class Tab { Home, Library, Browse, Settings }
