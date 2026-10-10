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
    val number = current?.let { Regex("(?<![0-9.])${Regex.escape(it.toChapterLabel())}(?![0-9]|\\.[0-9])") }
    return number?.findAll(url)?.lastOrNull()?.let { url.replaceRange(it.range, target.toChapterLabel()) }
}
