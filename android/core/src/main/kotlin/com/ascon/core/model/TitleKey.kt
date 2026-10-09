package com.ascon.core.model

import java.text.Normalizer

private val Marks = Regex("\\p{M}+")
private val NotAlphanumeric = Regex("[^\\p{L}\\p{N}]+")

/**
 * A title reduced for comparison: accents dropped, case folded, punctuation and spacing
 * collapsed. "Salt & Iron Kitchen!" and "salt iron kitchen" give the same key.
 * Exact key matches only; fuzzy matching through AniList and MangaUpdates comes later.
 */
fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
    .replace(Marks, "")
    .lowercase()
    .replace(NotAlphanumeric, " ")
    .trim()

/** The series in [library] whose title or an alternate title has the same key as [title]. */
fun matchSeries(library: List<Series>, title: String): Series? {
    val key = titleKey(title)
    if (key.isEmpty()) return null
    return library.firstOrNull { series -> (listOf(series.title) + series.altTitles).any { titleKey(it) == key } }
}
