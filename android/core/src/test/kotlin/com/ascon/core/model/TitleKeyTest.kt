package com.ascon.core.model

import com.ascon.core.data.fake.FakeLibrary
import java.time.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleKeyTest {
    private val library = FakeLibrary.series(Clock.systemUTC())

    @Test
    fun `keys ignore case, accents and punctuation`() {
        assertEquals("salt iron kitchen", titleKey("  Salt & Iron Kitchen! "))
        assertEquals("pokemon", titleKey("Pokémon"))
        assertEquals(titleKey("The Last Lighthouse-Keeper"), titleKey("the last lighthouse keeper"))
    }

    @Test
    fun `matches a title or an alternate title`() {
        assertEquals("aztec-turning-of-heaven", matchSeries(library, "aztec turning of heaven")?.id)
        assertEquals("aztec-turning-of-heaven", matchSeries(library, "Aztec no Kaiten")?.id)
    }

    @Test
    fun `no match for unknown or empty titles`() {
        assertNull(matchSeries(library, "Aztec"))
        assertNull(matchSeries(library, " — "))
    }
}
