package com.ascon.feature.browser

import androidx.lifecycle.SavedStateHandle
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.model.ReaderChapter
import com.ascon.engine.adblock.BlockCategory
import com.ascon.engine.detection.Detection
import com.ascon.engine.detection.DetectionSource
import com.ascon.feature.browser.web.BlockedKind
import com.ascon.feature.browser.web.LoadErrorKind
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
class BrowserViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val library = FakeLibraryRepository(FakeLibrary.series(clock))
    private val chapter14 = "https://mangafire.to/read/aztec/chapter-14"

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        BrowserViewModel(library, clock, saved, initialUrl = chapter14).also {
            it.onPageStarted(chapter14)
        }

    private fun chapter(
        url: String = chapter14,
        title: String? = "Aztec Turning of Heaven",
        number: String? = "14",
        images: List<String> = emptyList()
    ) = Detection.ChapterPage(
        url = url,
        source = DetectionSource.BuiltIn,
        seriesSlug = "aztec",
        title = title,
        chapterLabel = null,
        chapter = number?.let(::BigDecimal),
        images = images,
        next = if (images.isEmpty()) null else "$chapter14/next",
        previous = null
    )

    @Test
    fun `a chapter of a library series records progress and shows the card`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(title = "aztec turning of heaven"))

        val card = vm.state.value.card!!
        assertEquals("Aztec Turning of Heaven", card.title)
        assertEquals("aztec-turning-of-heaven", card.seriesId)
        assertTrue(card.saved)
        assertEquals(BigDecimal(14), library.series("aztec-turning-of-heaven").first()?.progress?.chapter)
    }

    @Test
    fun `progress is recorded once per page`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        library.recordChapterOpened("aztec-turning-of-heaven", BigDecimal(3), clock.instant().plusSeconds(1))
        vm.onDetection(chapter())
        assertEquals(clock.instant().plusSeconds(1), library.series("aztec-turning-of-heaven").first()?.lastReadAt)
    }

    @Test
    fun `an unknown series is added to the library with the site as its source`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(title = "Moonlit Ferry"))
        val card = vm.state.value.card!!
        assertTrue(card.saved)
        val series = library.series(card.seriesId!!).first()!!
        assertEquals("Moonlit Ferry", series.title)
        assertEquals("mangafire.to", series.progress?.sourceId)
        assertEquals(BigDecimal(14), series.progress?.chapter)
    }

    @Test
    fun `a chapter without a number adds nothing`() = runTest {
        val vm = viewModel()
        val before = library.series.value.size
        vm.onDetection(chapter(title = "Moonlit Ferry", number = null))
        assertFalse(vm.state.value.card!!.saved)
        assertNull(vm.state.value.card!!.seriesId)
        assertEquals(before, library.series.value.size)
    }

    @Test
    fun `results for another page are ignored`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(url = "https://mangafire.to/read/aztec/chapter-13"))
        assertNull(vm.state.value.card)
        vm.onDetection(chapter(url = "$chapter14#page-3"))
        assertEquals(chapter14, vm.state.value.card?.url)
    }

    @Test
    fun `a docked card stays docked until the chapter changes`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        assertFalse(vm.state.value.cardDocked)
        vm.dockCard()
        vm.onDetection(chapter())
        assertTrue(vm.state.value.cardDocked)
        assertEquals(BigDecimal(14), vm.state.value.card?.chapter)

        val next = "https://mangafire.to/read/aztec/chapter-15"
        vm.onHistoryChanged(next, canGoBack = true, canGoForward = false)
        vm.onDetection(chapter(url = next, number = "15"))
        assertEquals(BigDecimal(15), vm.state.value.card?.chapter)
        assertFalse(vm.state.value.cardDocked)
        assertTrue(vm.state.value.canGoBack)
    }

    @Test
    fun `scrolling one screen docks the card`() = runTest {
        val vm = viewModel()
        vm.onScrolled(scrollY = 300, viewportHeight = 800, toEnd = 2000)
        vm.onDetection(chapter())
        vm.onScrolled(scrollY = 900, viewportHeight = 800, toEnd = 2000)
        assertFalse(vm.state.value.cardDocked)
        vm.onScrolled(scrollY = 1100, viewportHeight = 800, toEnd = 2000)
        assertTrue(vm.state.value.cardDocked)
    }

    @Test
    fun `a page that is not a chapter has no card`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        vm.onDetection(Detection.None(chapter14))
        assertNull(vm.state.value.card)
    }

    @Test
    fun `errors, notices and the saved URL`() = runTest {
        val saved = SavedStateHandle()
        val vm = viewModel(saved)
        vm.onLoadError("https://www.mangadex.org/", LoadErrorKind.Unreachable)
        assertEquals(LoadError("mangadex.org", LoadErrorKind.Unreachable), vm.state.value.error)
        vm.retry()
        assertNull(vm.state.value.error)

        vm.onBlocked(BlockedKind.Redirect, "https://ads.example/x")
        val notice = vm.state.value.notice!!
        assertEquals("ads.example", notice.host)
        vm.dismissNotice(notice.id + 1)
        assertEquals(notice, vm.state.value.notice)
        vm.dismissNotice(notice.id)
        assertNull(vm.state.value.notice)

        assertEquals(
            chapter14,
            BrowserViewModel(library, clock, saved, initialUrl = "https://x.example/").state.value.url
        )
    }

    @Test
    fun `a chapter with pages opens the reader once per page`() = runTest {
        val vm = viewModel()
        val pages = listOf("https://cdn.example/1.webp", "https://cdn.example/2.webp", "https://cdn.example/3.webp")
        vm.onDetection(chapter(title = "aztec turning of heaven", images = pages))

        assertEquals(
            ReaderChapter(
                url = chapter14,
                title = "Aztec Turning of Heaven",
                chapter = BigDecimal(14),
                seriesId = "aztec-turning-of-heaven",
                pages = pages,
                next = "$chapter14/next",
                previous = null
            ),
            vm.state.value.reader
        )
        vm.readerOpened()
        assertNull(vm.state.value.reader)

        // Coming back from the reader shows the page again, and detection runs again.
        vm.onDetection(chapter(images = pages))
        assertNull(vm.state.value.reader)

        // Going back through the site's history to a page the reader showed stays on the site.
        val chapter15 = "https://mangafire.to/read/aztec/chapter-15"
        vm.onPageStarted(chapter15)
        vm.onDetection(chapter(url = chapter15, number = "15", images = pages))
        vm.readerOpened()
        vm.onPageStarted(chapter14)
        vm.onDetection(chapter(images = pages))
        assertNull(vm.state.value.reader)
    }

    @Test
    fun `a chapter without pages stays on the site`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        assertNull(vm.state.value.reader)
    }

    @Test
    fun `a chapter without pages shows the banner until dismissed`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        assertTrue(vm.state.value.readerUnavailable)

        vm.dismissReaderUnavailable()
        assertFalse(vm.state.value.readerUnavailable)
        vm.onDetection(chapter())
        assertFalse(vm.state.value.readerUnavailable)
    }

    @Test
    fun `a chapter with pages has no banner`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(images = listOf("https://cdn.example/1.webp")))
        assertFalse(vm.state.value.readerUnavailable)
    }

    @Test
    fun `the page on screen is saved and shown on the card`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        vm.onDetection(Detection.ReadingPosition(chapter14, page = 3, pageCount = 56))

        assertEquals(3, vm.state.value.card?.page)
        assertEquals(56, vm.state.value.card?.pageCount)
        val progress = library.series("aztec-turning-of-heaven").first()?.progress
        assertEquals(BigDecimal(14), progress?.chapter)
        assertEquals(3, progress?.page)
        assertEquals(56, progress?.pageCount)
    }

    @Test
    fun `a position before detection waits for the card`() = runTest {
        val vm = viewModel()
        vm.onDetection(Detection.ReadingPosition(chapter14, page = 3, pageCount = 56))
        assertNull(vm.state.value.card)
        vm.onDetection(chapter())
        assertEquals(3, vm.state.value.card?.page)
        assertEquals(3, library.series("aztec-turning-of-heaven").first()?.progress?.page)
    }

    @Test
    fun `a position for another page is ignored`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        vm.onDetection(Detection.ReadingPosition("https://mangafire.to/read/aztec/chapter-15", 3, 56))
        assertNull(vm.state.value.card?.page)
    }

    @Test
    fun `a docked card still saves the page`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        vm.dockCard()
        vm.onDetection(Detection.ReadingPosition(chapter14, page = 7, pageCount = 56))
        assertEquals(7, vm.state.value.card?.page)
        assertEquals(7, library.series("aztec-turning-of-heaven").first()?.progress?.page)
    }

    @Test
    fun `blocked requests are counted for the page that made them`() = runTest {
        val vm = viewModel()
        vm.onRequestBlocked(chapter14, BlockCategory.Ad)
        vm.onRequestBlocked(chapter14, BlockCategory.Ad)
        vm.onRequestBlocked(chapter14, BlockCategory.Tracker)
        vm.onRequestBlocked("https://mangafire.to/old-page", BlockCategory.Ad)
        vm.onBlocked(BlockedKind.Redirect, "https://ads.example/")
        vm.onBlocked(BlockedKind.AppDownload, "https://mangafire.to/app.apk")
        assertEquals(BlockedCounts(ads = 2, trackers = 1, redirects = 1), vm.state.value.blocked)
        assertEquals(4, vm.state.value.blocked.total)

        vm.onPageStarted("https://mangafire.to/read/aztec/chapter-15")
        assertEquals(BlockedCounts(), vm.state.value.blocked)
    }

    @Test
    fun `a chapter the reader showed can be opened in it again`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(images = listOf("https://cdn.example/1.webp")))
        val shown = vm.state.value.reader!!
        vm.readerOpened()
        assertNull(vm.state.value.reader)
        assertEquals(shown, vm.state.value.readerChapter)

        vm.openReader()
        assertEquals(shown, vm.state.value.reader)
    }

    @Test
    fun `the reader opens again at the page on screen`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(images = (1..20).map { "https://cdn.example/$it.webp" }))
        vm.readerOpened()
        vm.onDetection(Detection.ReadingPosition(chapter14, page = 11, pageCount = 20))
        vm.openReader()
        assertEquals(11, vm.state.value.reader?.startPage)

        // A site with more slots than the reader has pages, such as one with a banner slot.
        vm.onDetection(Detection.ReadingPosition(chapter14, page = 21, pageCount = 40))
        vm.openReader()
        assertEquals(11, vm.state.value.reader?.startPage)
    }

    @Test
    fun `a chapter page is kept on its source, so the library can open it again`() = runTest {
        viewModel().onDetection(chapter())
        val series = library.series("aztec-turning-of-heaven").first()!!
        assertEquals(chapter14, series.source("mangafire.to")?.lastOpened?.url)
    }

    @Test
    fun `a partly read chapter opens in the reader at its saved page`() = runTest {
        // Aztec is at page 34 of 58 in chapter 12; the reader finds 29 images.
        val chapter12 = "https://mangafire.to/read/aztec/chapter-12"
        val vm = viewModel().also { it.onPageStarted(chapter12) }
        vm.onDetection(chapter(url = chapter12, number = "12", images = (1..29).map { "https://cdn.example/$it.webp" }))
        assertEquals(17, vm.state.value.reader?.startPage)
    }

    @Test
    fun `a page the reader showed opens it again when asked for, not when gone back to`() = runTest {
        val vm = viewModel()
        val images = listOf("https://cdn.example/1.webp")
        vm.onDetection(chapter(images = images))
        vm.readerOpened()
        vm.onDetection(chapter(images = images))
        assertNull(vm.state.value.reader)

        vm.allowReader(chapter14)
        vm.onDetection(chapter(images = images))
        assertEquals(chapter14, vm.state.value.reader?.url)
    }

    @Test
    fun `a chapter not started opens at its first page`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(images = listOf("https://cdn.example/1.webp", "https://cdn.example/2.webp")))
        assertEquals(1, vm.state.value.reader?.startPage)
    }

    @Test
    fun `leaving the page forgets its chapter`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter(images = listOf("https://cdn.example/1.webp")))
        vm.onPageStarted("https://mangafire.to/home")
        assertNull(vm.state.value.readerChapter)
        vm.openReader()
        assertNull(vm.state.value.reader)
    }

    private fun BrowserViewModel.scrollTo(y: Int, toEnd: Int = 2000) =
        onScrolled(y, viewportHeight = 800, toEnd = toEnd)

    @Test
    fun `scrolling 24 dp down hides the toolbar and 24 dp up shows it`() = runTest {
        val vm = viewModel()
        vm.scrollTo(20)
        assertFalse(vm.state.value.toolbarHidden)
        vm.scrollTo(30)
        assertTrue(vm.state.value.toolbarHidden)
        vm.scrollTo(500)
        vm.scrollTo(480)
        assertTrue(vm.state.value.toolbarHidden)
        vm.scrollTo(470)
        assertFalse(vm.state.value.toolbarHidden)
        vm.scrollTo(490)
        assertFalse(vm.state.value.toolbarHidden)
        vm.scrollTo(500)
        assertTrue(vm.state.value.toolbarHidden)
    }

    @Test
    fun `a slow drag that wobbles never toggles the toolbar`() = runTest {
        val vm = viewModel()
        for (y in 0..400 step 4) {
            vm.scrollTo(y)
            vm.scrollTo(y - 2)
        }
        assertTrue(vm.state.value.toolbarHidden)
        var toggles = 0
        var last = true
        for (y in 400..800 step 4) {
            vm.scrollTo(y)
            vm.scrollTo(y - 3)
            if (vm.state.value.toolbarHidden != last) toggles++
            last = vm.state.value.toolbarHidden
        }
        assertEquals(0, toggles)
    }

    @Test
    fun `near the end the toolbar stays shown`() = runTest {
        val vm = viewModel()
        vm.scrollTo(400, toEnd = 100)
        vm.scrollTo(450, toEnd = 50)
        assertFalse(vm.state.value.toolbarHidden)
    }

    @Test
    fun `the end of the page or a new page shows the toolbar`() = runTest {
        val vm = viewModel()
        vm.scrollTo(400)
        vm.scrollTo(900, toEnd = 0)
        assertFalse(vm.state.value.toolbarHidden)

        vm.scrollTo(100)
        vm.scrollTo(200)
        assertTrue(vm.state.value.toolbarHidden)

        vm.onPageStarted("https://mangafire.to/read/aztec/chapter-15")
        assertFalse(vm.state.value.toolbarHidden)
    }

    @Test
    fun `ending the session starts the next page afresh`() = runTest {
        val vm = viewModel()
        vm.onRequestBlocked(vm.state.value.url, BlockCategory.Ad)
        vm.sessionEnded()

        assertEquals(BrowserUiState(url = ""), vm.state.value)
    }
}
