package com.ascon.feature.reader

import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.data.fake.FakeReaderSettings
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
        val state = ReaderViewModel(library, clock, chapter(), readerSettings).state.value
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
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings)
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
        val vm = ReaderViewModel(library, clock, chapter(seriesId = null), readerSettings)
        vm.onPageShown(6)
        assertEquals(7, vm.state.value.page)
        assertEquals(before, library.series.value)
    }

    @Test
    fun `starts at the chapter's start page`() {
        val vm = ReaderViewModel(library, clock, chapter().copy(startPage = 7), readerSettings)
        assertEquals(7, vm.state.value.page)
    }

    @Test
    fun `a tap shows or hides the bars`() {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings)
        vm.toggleBars()
        assertFalse(vm.state.value.barsVisible)
        vm.toggleBars()
        assertTrue(vm.state.value.barsVisible)
    }

    @Test
    fun `the bars show again near the end of the chapter`() {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings)
        vm.toggleBars()
        vm.nearEnd()
        assertTrue(vm.state.value.barsVisible)
    }

    @Test
    fun `settings save for the series until all series is chosen`() = runTest {
        val vm = ReaderViewModel(library, clock, chapter(), readerSettings)
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
        val vm = ReaderViewModel(library, clock, chapter(seriesId = null), readerSettings)
        assertEquals(SettingsScope.AllSeries, vm.state.value.settingsScope)

        vm.updateSettings { it.copy(keepScreenOn = true) }
        assertTrue(readerSettings.allSeries.first().keepScreenOn)
    }
}
