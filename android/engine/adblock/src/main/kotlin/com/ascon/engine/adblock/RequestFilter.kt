package com.ascon.engine.adblock

/** What a blocked request was, for the counts in the protection sheet. */
enum class BlockCategory { Ad, Tracker }

/** Decides which requests a page may make. */
fun interface RequestFilter {
    /** Whether to block a request for [url] made by the page at [sourceUrl]. */
    fun shouldBlock(url: String, sourceUrl: String, type: RequestType): Boolean

    /** What the request is blocked as, or null to let it through. Filters without categories say [BlockCategory.Ad]. */
    fun blockedAs(url: String, sourceUrl: String, type: RequestType): BlockCategory? =
        if (shouldBlock(url, sourceUrl, type)) BlockCategory.Ad else null

    companion object {
        /** Blocks nothing, for previews and tests. */
        val AllowAll = RequestFilter { _, _, _ -> false }
    }
}
