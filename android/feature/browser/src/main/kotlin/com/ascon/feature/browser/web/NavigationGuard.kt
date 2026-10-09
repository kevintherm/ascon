package com.ascon.feature.browser.web

import java.net.URI
import java.net.URISyntaxException

/** Why a navigation was stopped. */
enum class BlockReason {
    /** Not a web page, such as `intent://` or `market://`, which would leave the app. */
    Scheme,

    /** A page sent the tab to another site without the user touching anything. */
    NoGesture
}

/** Decides which site a host belongs to, such as `mangadex.org` for `api.mangadex.org`. */
fun interface SiteKey {
    fun siteOf(host: String): String
}

/**
 * The browser's navigation rules from AGENTS.md, as plain code so they can be tested
 * without a WebView.
 *
 * - The main frame only goes to http and https pages.
 * - A page cannot send the main frame to another site on its own. It needs a user
 *   gesture. Server redirects belong to the navigation that started them and pass.
 * - Frames may also load `about:`, `data:` and `blob:` documents, which sites use for
 *   their own widgets. Every other scheme is blocked there too.
 */
class NavigationGuard(private val sites: SiteKey) {
    /** Returns null to allow the navigation, or why it was blocked. */
    fun check(from: String?, to: String, isMainFrame: Boolean, hasGesture: Boolean, isRedirect: Boolean): BlockReason? {
        val scheme = schemeOf(to) ?: return BlockReason.Scheme
        val allowed = if (isMainFrame) WEB_SCHEMES else FRAME_SCHEMES
        return when {
            scheme !in allowed -> BlockReason.Scheme
            !isMainFrame || hasGesture || isRedirect || from == null -> null
            siteOfUrl(from) != siteOfUrl(to) -> BlockReason.NoGesture
            else -> null
        }
    }

    private fun siteOfUrl(url: String): String? = hostOf(url)?.let(sites::siteOf)

    private companion object {
        val WEB_SCHEMES = setOf("http", "https")
        val FRAME_SCHEMES = WEB_SCHEMES + setOf("about", "data", "blob")
    }
}

internal fun schemeOf(url: String): String? = try {
    URI(url).scheme?.lowercase()
} catch (_: URISyntaxException) {
    null
}

/** The lowercase host of [url], or null if it has none. */
fun hostOf(url: String): String? = try {
    URI(url).host?.lowercase()
} catch (_: URISyntaxException) {
    null
}
