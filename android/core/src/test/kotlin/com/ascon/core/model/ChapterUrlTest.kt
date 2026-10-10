package com.ascon.core.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterUrlTest {
    @Test
    fun `swaps the chapter number in the path`() {
        assertEquals(
            "https://site.example/manga/aztec/chapter-9/",
            chapterUrl("https://site.example/manga/aztec/chapter-14/", BigDecimal(14), BigDecimal(9))
        )
    }

    @Test
    fun `takes the last match, so a number in the title stays`() {
        assertEquals(
            "https://site.example/manga/14-days/chapter-15",
            chapterUrl("https://site.example/manga/14-days/chapter-14", BigDecimal(14), BigDecimal(15))
        )
    }

    @Test
    fun `handles decimal chapters both ways`() {
        assertEquals(
            "https://site.example/read/aztec/10.5",
            chapterUrl("https://site.example/read/aztec/11", BigDecimal(11), BigDecimal("10.5"))
        )
        assertEquals(
            "https://site.example/read/aztec/12",
            chapterUrl("https://site.example/read/aztec/10.5", BigDecimal("10.5"), BigDecimal(12))
        )
    }

    @Test
    fun `does not match part of a longer number`() {
        assertNull(chapterUrl("https://site.example/read/aztec/140", BigDecimal(14), BigDecimal(15)))
        assertNull(chapterUrl("https://site.example/read/aztec/14.5", BigDecimal(14), BigDecimal(15)))
    }

    @Test
    fun `gives nothing without a known chapter`() {
        assertNull(chapterUrl("https://site.example/read/aztec", null, BigDecimal(3)))
        assertNull(chapterUrl("https://site.example/read/aztec/abc", BigDecimal(3), BigDecimal(4)))
    }
}
