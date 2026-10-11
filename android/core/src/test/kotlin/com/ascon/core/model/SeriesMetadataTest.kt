package com.ascon.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesMetadataTest {
    private val solo = SeriesMetadata(
        ref = "anilist:105398",
        title = "Na Honjaman Level Up",
        altTitles = listOf("Solo Leveling", "나 혼자만 레벨업"),
        format = "manhwa",
        otherRef = "mangaupdates:15180124327"
    )
    private val soloNovel = SeriesMetadata("anilist:101024", "Na Honjaman Level Up", listOf("Solo Leveling"), "novel")
    private val ragnarok = SeriesMetadata("anilist:179445", "Solo Leveling: Ragnarok", format = "manhwa")

    @Test
    fun `an exact title links, ignoring case, punctuation and accents`() {
        for (detected in listOf("Solo Leveling", "SOLO LEVELING!", "solo  leveling", "Sōlo Leveling", "나 혼자만 레벨업")) {
            assertEquals(detected, solo, autoLinkMatch(detected, listOf(soloNovel, ragnarok, solo)))
        }
    }

    @Test
    fun `full-width letters match their plain forms`() {
        assertEquals(solo, autoLinkMatch("Ｓｏｌｏ Ｌｅｖｅｌｉｎｇ", listOf(solo)))
    }

    @Test
    fun `a near title waits for the user`() {
        for (detected in listOf("Solo Leveling S2", "Solo Leveling Season 2", "Solo Levelling", "Solo")) {
            assertNull(detected, autoLinkMatch(detected, listOf(solo, ragnarok)))
        }
    }

    @Test
    fun `two series with the same title wait for the user`() {
        val remake = solo.copy(ref = "anilist:1", otherRef = null)
        assertNull(autoLinkMatch("Solo Leveling", listOf(solo, remake)))
    }

    @Test
    fun `a novel never links, even alone`() {
        assertNull(autoLinkMatch("Solo Leveling", listOf(soloNovel)))
    }

    @Test
    fun `ids come from either ref`() {
        assertEquals(105398L, solo.aniListId)
        assertEquals(15180124327L, solo.mangaUpdatesId)
        assertNull(ragnarok.mangaUpdatesId)
    }
}
