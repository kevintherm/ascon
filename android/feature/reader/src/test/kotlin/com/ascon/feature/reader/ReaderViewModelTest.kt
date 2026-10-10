package com.ascon.feature.reader

import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.data.fake.FakeReaderSettings
import com.ascon.core.data.fake.FakeReadingPace
import com.ascon.core.model.PageGap
import com.ascon.core.model.ReaderBackground
import com.ascon.core.model.ReaderChapter
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val readerSettings = FakeReaderSettings()
    private val pace = FakeReadingPace()

    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val library = FakeLibraryRepository(FakeLibrary.series(clock))
    private val pages = (1..20).map { "https://cdn.example/aztec/13/$it.webp" }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun chapter(seriesId: String? = "aztec-turning-of-heaven", next: String? = null) = ReaderChapter(
        url = "https://www.mangafire.to/read/aztec/chapter-13",
        title = "Aztec Turning of Heaven",
        chapter = BigDecimal(13),
        seriesId = seriesId,
        pages = pages,
        next = next,
        previous = "https://www.mangafire.to/read/aztec/chapter-12"
    )

    @Test
    fun `starts on the first page with the bars shown`() {
        val state = ReaderViewModel(library, clock, chapter(), readerSettings, pace).state.value
        assertEquals("Aztec Turning of Heaven", state.title)
        assertEquals("mangafire.to", state.host)
        assertEquals(1, state.page)
        assertEquals(20, state.pageCount)
        assertTrue(state.barsVisible)
        assertEquals("https://www.mangafire.to/read/aztec/chapter-12", state.previous)
        assertNull(state.next)
    }

    @Test
    fun `showing a page saves it for a library series`() = runTest {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings, pace)
        vm.onPageShown(6)

        assertEquals(7, vm.state.value.page)
        val progress = library.series("aztec-turning-of-heaven").first()?.progress
        assertEquals(BigDecimal(13), progress?.chapter)
        assertEquals(7, progress?.page)
        assertEquals(20, progress?.pageCount)
    }

    @Test
    fun `a chapter outside the library saves nothing`() = runTest {
        val before = library.series.value
        val vm = ReaderViewModel(library, clock, chapter(seriesId = null), readerSettings, pace)
        vm.onPageShown(6)
        assertEquals(7, vm.state.value.page)
        assertEquals(before, library.series.value)
    }

    @Test
    fun `starts at the chapter's start page`() {
        val vm = ReaderViewModel(library, clock, chapter().copy(startPage = 7), readerSettings, pace)
        assertEquals(7, vm.state.value.page)
    }

    @Test
    fun `a tap shows or hides the bars`() {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings, pace)
        vm.toggleBars()
        assertFalse(vm.state.value.barsVisible)
        vm.toggleBars()
        assertTrue(vm.state.value.barsVisible)
    }

    @Test
    fun `the bars show again near the end of the chapter`() {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings, pace)
        vm.toggleBars()
        vm.nearEnd()
        assertTrue(vm.state.value.barsVisible)
    }

    @Test
    fun `settings save for the series until all series is chosen`() = runTest {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings, pace)
        assertEquals(SettingsScope.Series, vm.state.value.settingsScope)

        vm.updateSettings { it.copy(gap = PageGap.None) }
        assertEquals(PageGap.None, vm.state.value.settings.gap)
        assertEquals(PageGap.Auto, readerSettings.allSeries.first().gap)

        vm.setSettingsScope(SettingsScope.AllSeries)
        assertEquals(PageGap.Auto, vm.state.value.settings.gap)
        vm.updateSettings { it.copy(background = ReaderBackground.White) }
        assertEquals(ReaderBackground.White, readerSettings.allSeries.first().background)
        assertEquals(ReaderBackground.White, vm.state.value.settings.background)
    }

    @Test
    fun `a chapter outside the library changes the settings for all series`() = runTest {
        val vm = ReaderViewModel(library, clock, chapter(seriesId = null), readerSettings, pace)
        assertEquals(SettingsScope.AllSeries, vm.state.value.settingsScope)

        vm.updateSettings { it.copy(keepScreenOn = true) }
        assertTrue(readerSettings.allSeries.first().keepScreenOn)
    }

    @Test
    fun `the time left shows after three images read at a pace and learns it`() {
        var now = Instant.parse("2026-10-08T12:00:00Z")
        val ticking = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = now
        }
        val vm = ReaderViewModel(library, ticking, chapter(), readerSettings, pace)
        vm.onPageShown(0)
        // A jump through the slider and a skim teach nothing.
        now = now.plusSeconds(10)
        vm.onPageShown(5)
        now = now.plusMillis(200)
        vm.onPageShown(6)
        assertNull(vm.state.value.minutesLeft)
        repeat(3) {
            now = now.plusSeconds(12)
            vm.onPageShown(7 + it)
        }
        // Page 10 of 20: 10 images left at 12 seconds is 2 minutes.
        assertEquals(listOf(12f, 12f, 12f), pace.secondsPerImage.value)
        assertEquals(2, vm.state.value.minutesLeft)
    }

    @Test
    fun `turning off opening by itself saves it for the site`() = runTest {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings, pace)
        assertTrue(vm.state.value.autoOpen)

        vm.setAutoOpen(false)
        assertFalse(vm.state.value.autoOpen)
        assertEquals(false, readerSettings.autoOpen(vm.state.value.host).first())
    }

    @Test
    fun `a chapter with no next or previous shows opening by itself off until the user chooses`() = runTest {
        val alone = chapter().copy(previous = null)
        val vm = ReaderViewModel(library, clock, alone, readerSettings, pace)
        assertFalse(vm.state.value.autoOpen)

        vm.setAutoOpen(true)
        assertTrue(vm.state.value.autoOpen)
    }
}
