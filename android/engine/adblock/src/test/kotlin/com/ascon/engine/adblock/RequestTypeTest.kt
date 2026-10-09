package com.ascon.engine.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestTypeTest {
    private fun type(url: String, mainFrame: Boolean = false, accept: String? = null) =
        RequestType.of(url, mainFrame, accept)

    @Test
    fun `the main frame is a document whatever it asks for`() {
        assertEquals(RequestType.Document, type("https://a.example/x.png", mainFrame = true, accept = "image/png"))
    }

    @Test
    fun `the accept header names frames, styles and images`() {
        assertEquals(
            RequestType.Subdocument,
            type("https://a.example/frame", accept = "text/html,application/xhtml+xml")
        )
        assertEquals(RequestType.Stylesheet, type("https://a.example/s", accept = "text/css,*/*;q=0.1"))
        assertEquals(RequestType.Image, type("https://a.example/i", accept = "image/avif,image/webp,*/*"))
    }

    @Test
    fun `without a telling header the extension decides`() {
        assertEquals(RequestType.Script, type("https://a.example/ads.js?v=2", accept = "*/*"))
        assertEquals(RequestType.Image, type("https://cdn.example/12/01.WEBP"))
        assertEquals(RequestType.Font, type("https://a.example/f.woff2"))
        assertEquals(RequestType.Media, type("https://a.example/v/master.m3u8"))
    }

    @Test
    fun `anything else is other`() {
        assertEquals(RequestType.Other, type("https://a.example/api/chapters/12", accept = "application/json"))
        assertEquals(RequestType.Other, type("not a url"))
    }
}
