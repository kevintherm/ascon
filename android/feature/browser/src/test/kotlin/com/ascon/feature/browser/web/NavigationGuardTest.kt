package com.ascon.feature.browser.web

import com.ascon.engine.adblock.RequestFilter
import com.ascon.engine.adblock.RequestType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationGuardTest {
    // The last two labels; enough for these hosts. The app uses the public suffix list.
    // The ad filter names one ad domain, and checks that it is asked about documents only.
    private val ads =
        RequestFilter { url, _, type -> type == RequestType.Document && hostOf(url) == "popunder.example" }
    private val guard = NavigationGuard({ host -> host.split('.').takeLast(2).joinToString(".") }, ads)
    private val page = "https://mangafire.to/read/aztec/chapter-12"

    private fun check(
        to: String,
        from: String? = page,
        isMainFrame: Boolean = true,
        hasGesture: Boolean = false,
        isRedirect: Boolean = false,
        tappedLink: String? = null
    ) = guard.check(from, to, isMainFrame, hasGesture, isRedirect, tappedLink)

    @Test
    fun `blocks schemes that leave the browser`() {
        assertEquals(BlockReason.Scheme, check("intent://scan/#Intent;scheme=zxing;end", hasGesture = true))
        assertEquals(BlockReason.Scheme, check("market://details?id=com.fake.reader", hasGesture = true))
        assertEquals(BlockReason.Scheme, check("tel:123", hasGesture = true))
        assertEquals(BlockReason.Scheme, check("data:text/html,hi", hasGesture = true))
        assertEquals(BlockReason.Scheme, check("intent://x", isMainFrame = false))
        assertEquals(BlockReason.Scheme, check("not a url"))
    }

    @Test
    fun `frames may load their own documents`() {
        assertNull(check("about:blank", isMainFrame = false))
        assertNull(check("data:text/html,hi", isMainFrame = false))
        assertNull(check("https://ads.example/frame", isMainFrame = false))
    }

    @Test
    fun `a page cannot send the tab to another site on its own`() {
        assertEquals(BlockReason.NoGesture, check("https://ads.example/landing"))
    }

    @Test
    fun `a tapped link can go to another site`() {
        assertNull(
            check("https://mangadex.org/title/1", hasGesture = true, tappedLink = "https://mangadex.org/title/1")
        )
        // Sites rewrite links on the way out, such as tracking parameters; the site is what counts.
        assertNull(
            check(
                "https://www.mangadex.org/title/1?ref=x",
                hasGesture = true,
                tappedLink = "https://mangadex.org/title/1"
            )
        )
    }

    @Test
    fun `a tap elsewhere cannot send the tab to another site`() {
        // A click handler that hijacks a tap on the page or on a link to somewhere else.
        assertEquals(BlockReason.Hijack, check("https://ads.example/landing", hasGesture = true))
        assertEquals(
            BlockReason.Hijack,
            check(
                "https://ads.example/landing",
                hasGesture = true,
                tappedLink = "https://mangafire.to/read/aztec/chapter-13"
            )
        )
    }

    @Test
    fun `a tap moves freely within the site`() {
        assertNull(check("https://mangafire.to/read/aztec/chapter-13", hasGesture = true))
    }

    @Test
    fun `same site and redirects pass without a gesture`() {
        assertNull(check("https://mangafire.to/read/aztec/chapter-13"))
        assertNull(check("https://static.mangafire.to/x"))
        assertNull(check("https://login.example/callback", isRedirect = true))
        assertNull(check("https://ads.example/", from = null))
    }

    @Test
    fun `a main-frame navigation to an ad domain is blocked even when tapped`() {
        val ad = "https://popunder.example/go?id=1"
        assertEquals(BlockReason.Ad, check(ad, hasGesture = true, tappedLink = ad))
        assertEquals(BlockReason.Ad, check(ad, isRedirect = true))
        assertEquals(BlockReason.Ad, check(ad, from = null))
        assertNull(check("https://popunder.example/frame", isMainFrame = false))
    }
}
