package com.ascon.feature.browser

import com.ascon.core.model.ReadingProgress
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeTest {
    private val twelve = BigDecimal(12)
    private val url = "https://site.example/aztec/chapter-12"

    private fun progress(page: Int, pageCount: Int, offset: Float = 0f) =
        ReadingProgress(twelve, page, pageCount, "site.example", offset)

    @Test
    fun `the place inside a page carries over to a count with more pages`() {
        // Halfway down site page 3 of 10 is page 6 of 20 in the reader.
        assertEquals(6, resumePage(progress(3, 10, 0.5f), twelve, 20))
        assertEquals(5, resumePage(progress(3, 10, 0.4f), twelve, 20))
        assertEquals(3, resumePage(progress(3, 10), twelve, 10))
    }

    @Test
    fun `the site scrolls to the saved offset, even on the first page`() {
        assertEquals(ResumeScroll(url, 1, 10, 0.7f), resumeScroll(url, progress(1, 10, 0.7f), twelve))
        assertEquals(ResumeScroll(url, 4, 10, 0.25f), resumeScroll(url, progress(4, 10, 0.25f), twelve))
        assertNull(resumeScroll(url, progress(1, 10), twelve))
        assertNull(resumeScroll(url, progress(10, 10, 0.5f), twelve))
    }
}
