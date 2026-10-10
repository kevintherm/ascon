package com.ascon.core.model

import java.math.BigDecimal

/**
 * The URL of chapter [target] on the site, made from the chapter page [url] for chapter
 * [current] by swapping its number, as in `/chapter-14/` to `/chapter-9/`. Null when the
 * number isn't in the URL, so the reader can't tell where the chapter lives. The chapter
 * the page shows is the page itself, even when its URL carries an id instead of a number.
 */
fun chapterUrl(url: String, current: BigDecimal?, target: BigDecimal): String? {
    if (current != null && current.compareTo(target) == 0) return url
    return current?.let { numberIn(url, it) }?.let { url.replaceRange(it, target.toChapterLabel()) }
}

/**
 * The chapter number [link] goes to, read from where the chapter page [url] for chapter
 * [current] has its number, as `/chapter-2/` from `/chapter-1/`. Null when [link] differs
 * from [url] anywhere else, or the number isn't in the URL.
 */
fun chapterOf(link: String, url: String, current: BigDecimal): BigDecimal? {
    val at = numberIn(url, current) ?: return null
    val prefix = url.substring(0, at.first)
    val suffix = url.substring(at.last + 1)
    val fits = link.startsWith(prefix) && link.endsWith(suffix) && link.length > prefix.length + suffix.length
    return if (fits) link.substring(prefix.length, link.length - suffix.length).toBigDecimalOrNull() else null
}

/** Where [url] has [number], the last time it does. */
private fun numberIn(url: String, number: BigDecimal): IntRange? =
    Regex("(?<![0-9.])${Regex.escape(number.toChapterLabel())}(?![0-9]|\\.[0-9])").findAll(url).lastOrNull()?.range
