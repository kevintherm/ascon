package com.ascon.feature.browser.web

import com.ascon.core.model.ProtectionSettings
import com.ascon.engine.adblock.BlockCategory
import com.ascon.engine.adblock.RequestFilter
import com.ascon.engine.adblock.RequestType
import java.net.URI
import java.net.URISyntaxException

/** Why a navigation was stopped. */
enum class BlockReason {
    /** Not a web page, such as `intent://` or `market://`, which would leave the app. */
    Scheme,

    /** A page sent the tab to another site without the user touching anything. */
    NoGesture,

    /** A tap sent the tab to another site, but not through the link that was tapped. */
    Hijack,

    /** The page is on a filter list, such as a pop-under landing page. */
    Ad
}

/** Decides which site a host belongs to, such as `mangadex.org` for `api.mangadex.org`. */
fun interface SiteKey {
    fun siteOf(host: String): String
}

/**
 * The browser's navigation and request rules from AGENTS.md, as plain code so they can be
 * tested without a WebView. The user's [ProtectionSettings] apply in this order:
 *
 * 1. Other schemes, such as app links, are always blocked.
 * 2. A trusted site skips every other rule.
 * 3. With ad blocking off, the ad filter is not asked.
 * 4. With popups and redirects allowed, a page may send the tab to another site without
 *    a tap, and a click handler may send it somewhere other than the tapped link.
 *
 * The rules, with everything on:
 *
 * - The main frame only goes to http and https pages.
 * - A page cannot send the main frame to another site on its own. It needs a user
 *   gesture. Server redirects belong to the navigation that started them and pass.
 * - A gesture only counts for the site of the link the user tapped, so a click handler
 *   cannot turn a tap into a trip to an ad site.
 * - The main frame never goes to a page the ad filter blocks, tapped or not, so a
 *   hijacked link to a known ad domain is stopped too. Frames are left to [RequestFilter]
 *   checks on each request.
 * - Frames may also load `about:`, `data:` and `blob:` documents, which sites use for
 *   their own widgets. Every other scheme is blocked there too.
 */
class NavigationGuard(private val sites: SiteKey, private val ads: RequestFilter = RequestFilter.AllowAll) {
    /**
     * Returns null to allow the navigation, or why it was blocked. [tappedLink] is the
     * link under the user's last tap, if the tap was moments ago.
     */
    fun check(
        from: String?,
        to: String,
        isMainFrame: Boolean,
        hasGesture: Boolean,
        isRedirect: Boolean,
        tappedLink: String? = null,
        protection: ProtectionSettings = ProtectionSettings()
    ): BlockReason? {
        val scheme = schemeOf(to) ?: return BlockReason.Scheme
        val allowed = if (isMainFrame) WEB_SCHEMES else FRAME_SCHEMES
        return when {
            scheme !in allowed -> BlockReason.Scheme
            !isMainFrame || protection.trustsPage(from ?: to) -> null
            protection.adblockEnabled && ads.shouldBlock(to, from ?: to, RequestType.Document) -> BlockReason.Ad
            isRedirect || from == null || !protection.blockPopups -> null
            else -> crossSite(from, to, hasGesture, tappedLink)
        }
    }

    /**
     * What a request [pageUrl] makes for [url] is stopped as, or null to let it through.
     * Main-frame pages go through [check].
     */
    fun blockedAs(url: String, pageUrl: String, type: RequestType, protection: ProtectionSettings): BlockCategory? =
        if (filtersPage(pageUrl, protection)) ads.blockedAs(url, pageUrl, type) else null

    /** True when the filter lists apply to [pageUrl], for its requests and its hidden elements. */
    fun filtersPage(pageUrl: String, protection: ProtectionSettings): Boolean =
        protection.adblockEnabled && !protection.trustsPage(pageUrl)

    // Most users trust no site, so most requests skip the public suffix lookup.
    private fun ProtectionSettings.trustsPage(url: String): Boolean =
        trustedSites.isNotEmpty() && trusts(siteOfUrl(url))

    /** The main frame moving from [from] to [to], which is not a redirect. */
    private fun crossSite(from: String, to: String, hasGesture: Boolean, tappedLink: String?): BlockReason? {
        val site = siteOfUrl(to)
        return when {
            siteOfUrl(from) == site -> null
            !hasGesture -> BlockReason.NoGesture
            tappedLink == null || siteOfUrl(tappedLink) != site -> BlockReason.Hijack
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
