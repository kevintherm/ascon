package com.ascon.feature.reader

import com.ascon.core.model.toChapterLabel
import java.math.BigDecimal

/**
 * The URL of chapter [target] on the site, made from the chapter page [url] for chapter
 * [current] by swapping its number, as in `/chapter-14/` to `/chapter-9/`. Null when the
 * number isn't in the URL, so the reader can't tell where the chapter lives.
 */
internal fun chapterUrl(url: String, current: BigDecimal?, target: BigDecimal): String? {
    val number = current?.let { Regex("(?<![0-9.])${Regex.escape(it.toChapterLabel())}(?![0-9]|\\.[0-9])") }
    val match = number?.findAll(url)?.lastOrNull() ?: return null
    return url.replaceRange(match.range, target.toChapterLabel())
}
