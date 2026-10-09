package com.ascon.engine.adblock

/** Decides which requests a page may make. */
fun interface RequestFilter {
    /** Whether to block a request for [url] made by the page at [sourceUrl]. */
    fun shouldBlock(url: String, sourceUrl: String, type: RequestType): Boolean

    companion object {
        /** Blocks nothing, for previews and tests. */
        val AllowAll = RequestFilter { _, _, _ -> false }
    }
}
