package com.ascon.core.model

/**
 * A series as AniList or MangaUpdates describes it, from the backend's metadata search.
 * [ref] is "anilist:<id>" or "mangaupdates:<id>"; [otherRef] is the same series on the
 * other service, when the backend knows it.
 */
data class SeriesMetadata(
    val ref: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    /** manga, manhwa, manhua, comic, novel or other; null when unknown. */
    val format: String? = null,
    val year: Int? = null,
    val coverUrl: String? = null,
    val otherRef: String? = null
) {
    val titles: List<String> get() = listOf(title) + altTitles

    /** The AniList id in [ref] or [otherRef]. */
    val aniListId: Long? get() = idOn("anilist")

    /** The MangaUpdates id in [ref] or [otherRef]. */
    val mangaUpdatesId: Long? get() = idOn("mangaupdates")

    private fun idOn(service: String): Long? = listOfNotNull(ref, otherRef)
        .firstNotNullOfOrNull { it.removePrefix("$service:").takeIf { id -> id != it }?.toLongOrNull() }
}

/**
 * The one search result a detected [title] links to by itself, or null when the user must
 * choose. Decided with the owner, strict: the title must equal one of the result's titles
 * by [titleKey], novels are skipped since a detected chapter has images, and exactly one
 * series may be left. Words such as Season 2 are never dropped, since they often name
 * another series.
 */
fun autoLinkMatch(title: String, results: List<SeriesMetadata>): SeriesMetadata? {
    val key = titleKey(title)
    if (key.isEmpty()) return null
    val exact = results.filter { result ->
        result.format != NOVEL && result.titles.any { titleKey(it) == key }
    }
    return exact.singleOrNull()
}

private const val NOVEL = "novel"
