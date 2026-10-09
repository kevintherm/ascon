package com.ascon.feature.browser

import androidx.lifecycle.SavedStateHandle
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.model.ReaderChapter
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
    fun `navigating clears the card and hiding keeps it hidden for the page`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        vm.hideCard()
        vm.onDetection(chapter())
        assertNull(vm.state.value.card)

        val next = "https://mangafire.to/read/aztec/chapter-15"
        vm.onDetection(chapter())
        vm.onHistoryChanged(next, canGoBack = true, canGoForward = false)
        vm.onDetection(chapter(url = next, number = "15"))
        assertEquals(BigDecimal(15), vm.state.value.card?.chapter)
        assertTrue(vm.state.value.canGoBack)
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
    fun `a hidden card still saves the page`() = runTest {
        val vm = viewModel()
        vm.onDetection(chapter())
        vm.hideCard()
        vm.onDetection(Detection.ReadingPosition(chapter14, page = 7, pageCount = 56))
        assertNull(vm.state.value.card)
        assertEquals(7, library.series("aztec-turning-of-heaven").first()?.progress?.page)
    }
}
