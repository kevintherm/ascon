package com.ascon.core.data.fake

import com.ascon.core.model.Chapter
import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.SettingsSummary
import com.ascon.core.model.Site
import com.ascon.core.model.Source
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Sample data matching the approved mockups, until Room and detection fill the library.
 * Dates are relative to [Clock] so "Today" stays today.
 */
@Suppress("MagicNumber")
object FakeLibrary {
    val sites = listOf(
        Site("mangaplus.shueisha.co.jp", "MANGA Plus", "M+"),
        Site("mangadex.org", "MangaDex", "MD"),
        Site("mangafire.to", "mangafire", "MF")
    )

    val settingsSummary = SettingsSummary(
        blockedThisWeek = 1284,
        activeFilterLists = 4,
        hiddenSeriesLocked = true,
        downloadsBytes = 1_200_000_000
    )

    @Suppress("LongMethod") // One entry per sample series; splitting it would not help reading.
    fun series(clock: Clock): List<Series> {
        val today = LocalDate.now(clock)
        val now = clock.instant()
        return listOf(
            aztec(today, now),
            reading(
                id = "last-lighthouse-keeper",
                title = "The Last Lighthouse Keeper",
                cover = Cover.Placeholder(0xFF9FB6D8, 0xFF3C4F7A, 0xFF1C2235),
                sources = listOf(
                    Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(48)),
                    Source("comick", "comick.io", official = false, BigDecimal.ONE, BigDecimal(48))
                ),
                total = 48,
                at = 47,
                newFrom = 48,
                newOn = today,
                lastRead = now - Duration.ofDays(1),
                today = today
            ),
            reading(
                id = "salt-and-iron-kitchen",
                title = "Salt & Iron Kitchen",
                cover = Cover.Placeholder(0xFFD9D2B0, 0xFF8C7A4A, 0xFF2E2618),
                sources = listOf(Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(113))),
                total = 113,
                at = 111,
                newFrom = 112,
                newOn = today.minusDays(1),
                lastRead = now - Duration.ofDays(2),
                today = today
            ),
            reading(
                id = "paper-moth",
                title = "Paper Moth",
                cover = Cover.Placeholder(0xFFB9D3C2, 0xFF4E7A66, 0xFF182A22),
                sources = listOf(Source("webtoons", "WEBTOON", official = true, BigDecimal.ONE, BigDecimal(7))),
                total = 7,
                at = 6,
                newOn = today.minusDays(2),
                downloadedFrom = 7,
                lastRead = now - Duration.ofDays(3),
                today = today
            ),
            reading(
                id = "rust-belt-saints",
                title = "Rust Belt Saints",
                cover = Cover.Placeholder(0xFFE4C1A1, 0xFFA0613A, 0xFF2E1A10),
                sources = listOf(Source("mangafire", "mangafire.to", official = false, BigDecimal.ONE, BigDecimal(22))),
                total = 22,
                at = 9,
                lastRead = now - Duration.ofDays(4),
                today = today
            ),
            reading(
                id = "glass-orchard",
                title = "Glass Orchard",
                cover = Cover.Placeholder(0xFFCFE0E8, 0xFF5D8597, 0xFF1A2930),
                sources = listOf(Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(18))),
                total = 18,
                at = 3,
                lastRead = now - Duration.ofDays(5),
                today = today
            ),
            reading(
                id = "second-bell",
                title = "Second Bell",
                cover = Cover.Placeholder(0xFFE8D1D1, 0xFFA35B5B, 0xFF2D1515),
                sources = listOf(Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(60))),
                total = 60,
                at = 58,
                newFrom = 59,
                newOn = today.minusDays(8),
                lastRead = now - Duration.ofDays(6),
                today = today
            ),
            reading(
                id = "moth-and-lantern",
                title = "Moth & Lantern",
                cover = Cover.Placeholder(0xFFD6D6D6, 0xFF6B6B6B, 0xFF1E1E1E),
                sources = listOf(Source("mangafire", "mangafire.to", official = false, BigDecimal.ONE, BigDecimal(31))),
                total = 31,
                at = 15,
                lastRead = now - Duration.ofDays(9),
                today = today
            ),
            reading(
                id = "neon-shrine",
                title = "Neon Shrine",
                cover = Cover.Placeholder(0xFFC7B2E0, 0xFF6A4C93, 0xFF20152F),
                sources = listOf(Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(30))),
                total = 30,
                at = 30,
                lastRead = now - Duration.ofDays(20),
                today = today,
                status = ReadingStatus.Done
            ),
            reading(
                id = "quiet-atlas",
                title = "Quiet Atlas",
                cover = Cover.Placeholder(0xFFDCE3C8, 0xFF7C8A4E, 0xFF22281A),
                sources = listOf(Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(64))),
                total = 64,
                at = 64,
                lastRead = now - Duration.ofDays(40),
                today = today,
                status = ReadingStatus.Done
            ),
            reading(
                id = "iron-saint-requiem",
                title = "Iron Saint Requiem",
                cover = Cover.Placeholder(0xFFD0C4E4, 0xFF5E4A8C, 0xFF1A1428),
                sources = listOf(Source("mangafire", "mangafire.to", official = false, BigDecimal.ONE, BigDecimal(90))),
                total = 90,
                at = 41,
                lastRead = now - Duration.ofDays(60),
                today = today,
                status = ReadingStatus.Paused
            ),
            planned(
                id = "tidewater-saga",
                title = "Tidewater Saga",
                cover = Cover.Placeholder(0xFFBFDCE0, 0xFF3F7F8A, 0xFF132A2E),
                total = 26,
                today = today
            ),
            planned(
                id = "hollow-crown-courier",
                title = "Hollow Crown Courier",
                cover = Cover.Placeholder(0xFFE6CFB8, 0xFF9A6B45, 0xFF2B1D12),
                total = 12,
                today = today
            )
        )
    }

    private fun aztec(today: LocalDate, now: Instant): Series {
        val chapters = (1..14).map { n ->
            Chapter(
                number = BigDecimal(n),
                publishedOn = when (n) {
                    14 -> today
                    13 -> today.minusDays(7)
                    else -> today.minusWeeks(14L - n + 1)
                },
                read = n <= 11,
                isNew = n >= 13,
                downloaded = n == 10,
                readOnSourceId = if (n == 11) "mangafire" else null
            )
        }
        return Series(
            id = "aztec-turning-of-heaven",
            title = "Aztec Turning of Heaven",
            altTitles = listOf("Aztec no Kaiten"),
            cover = Cover.Placeholder(0xFFE9A27A, 0xFFB33A3A, 0xFF2B1530),
            status = ReadingStatus.Reading,
            linkedToAniList = true,
            sources = listOf(
                Source("mangaplus", "MANGA Plus", official = true, BigDecimal.ONE, BigDecimal(14)),
                Source("mangafire", "mangafire.to", official = false, BigDecimal.ONE, BigDecimal(13))
            ),
            chapters = chapters,
            progress = ReadingProgress(BigDecimal(12), page = 34, pageCount = 58, sourceId = "mangaplus"),
            lastReadAt = now - Duration.ofHours(1)
        )
    }

    @Suppress("LongParameterList")
    private fun reading(
        id: String,
        title: String,
        cover: Cover,
        sources: List<Source>,
        total: Int,
        at: Int,
        lastRead: Instant,
        today: LocalDate,
        newFrom: Int? = null,
        newOn: LocalDate? = null,
        downloadedFrom: Int? = null,
        status: ReadingStatus = ReadingStatus.Reading
    ): Series {
        val chapters = (1..total).map { n ->
            val fresh = newFrom != null && n >= newFrom
            Chapter(
                number = BigDecimal(n),
                publishedOn = when {
                    n > at && newOn != null -> newOn
                    else -> today.minusWeeks(total.toLong() - n + 2)
                },
                read = n <= at,
                isNew = fresh,
                downloaded = downloadedFrom != null && n >= downloadedFrom
            )
        }
        return Series(
            id = id,
            title = title,
            altTitles = emptyList(),
            cover = cover,
            status = status,
            linkedToAniList = true,
            sources = sources,
            chapters = chapters,
            progress = ReadingProgress(BigDecimal(at), page = 40, pageCount = 40, sourceId = sources.first().id),
            lastReadAt = lastRead
        )
    }

    private fun planned(id: String, title: String, cover: Cover, total: Int, today: LocalDate) = Series(
        id = id,
        title = title,
        altTitles = emptyList(),
        cover = cover,
        status = ReadingStatus.Plan,
        linkedToAniList = false,
        sources = listOf(Source("mangadex", "MangaDex", official = false, BigDecimal.ONE, BigDecimal(total))),
        chapters = (1..total).map { n ->
            Chapter(BigDecimal(n), today.minusWeeks(total.toLong() - n + 1), read = false)
        },
        progress = null,
        lastReadAt = null
    )
}
