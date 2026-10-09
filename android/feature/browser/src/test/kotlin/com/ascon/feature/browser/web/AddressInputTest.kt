package com.ascon.feature.browser.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressInputTest {
    @Test
    fun `addresses load as typed or with https`() {
        assertEquals("https://mangadex.org", addressToUrl(" mangadex.org "))
        assertEquals("https://mangadex.org/title/1?a=b", addressToUrl("mangadex.org/title/1?a=b"))
        assertEquals("http://192.168.1.4:8080/", addressToUrl("http://192.168.1.4:8080/"))
        assertEquals("https://localhost.test:8080", addressToUrl("localhost.test:8080"))
    }

    @Test
    fun `anything else is a search`() {
        assertEquals("https://duckduckgo.com/?q=solo+leveling", addressToUrl("solo leveling"))
        assertEquals("https://duckduckgo.com/?q=chapter+12.5", addressToUrl("chapter 12.5"))
        assertEquals("https://duckduckgo.com/?q=javascript%3Aalert%281%29", addressToUrl("javascript:alert(1)"))
        assertEquals("https://duckduckgo.com/?q=file%3A%2F%2F%2Fsdcard", addressToUrl("file:///sdcard"))
        assertNull(addressToUrl("   "))
    }

    @Test
    fun `the pill shows the host without www`() {
        assertEquals("mangafire.to", displayHost("https://www.mangafire.to/read/x"))
        assertEquals("about:blank", displayHost("about:blank"))
    }
}
