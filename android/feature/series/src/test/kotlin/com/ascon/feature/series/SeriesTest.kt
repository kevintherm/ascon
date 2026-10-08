package com.ascon.feature.series

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.designsystem.theme.AsconTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val FixedClock: Clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
private val Today: LocalDate = LocalDate.now(FixedClock)

class SeriesUiStateTest {
    private val aztec = FakeLibrary.series(FixedClock).first { it.id == "aztec-turning-of-heaven" }

    @Test
    fun `a chapter in progress gives continue with the page`() {
        val state = seriesUiState(aztec, newestFirst = true, today = Today)
        assertEquals(PrimaryAction.Continue("12", 34, 58), state.primaryAction)
    }

    @Test
    fun `chapter rows carry their read state and trailing text`() {
        val rows = seriesUiState(aztec, newestFirst = true, today = Today).chapters.associateBy { it.number }
        assertEquals(ChapterState.New, rows.getValue("14").state)
        assertEquals(ChapterTrailing.Date(Today), rows.getValue("14").trailing)
        assertEquals(ChapterState.InProgress(34, 58), rows.getValue("12").state)
        assertEquals(ChapterTrailing.ReadVia("mangafire"), rows.getValue("11").trailing)
        assertEquals(ChapterTrailing.Downloaded, rows.getValue("10").trailing)
        assertEquals(ChapterState.Read, rows.getValue("1").state)
    }

    @Test
    fun `order flips between newest and oldest first`() {
        assertEquals("14", seriesUiState(aztec, newestFirst = true, today = Today).chapters.first().number)
        assertEquals("1", seriesUiState(aztec, newestFirst = false, today = Today).chapters.first().number)
    }

    @Test
    fun `a finished chapter offers to start the next one`() {
        val lighthouse = FakeLibrary.series(FixedClock).first { it.id == "last-lighthouse-keeper" }
        assertEquals(PrimaryAction.Start("48"), seriesUiState(lighthouse, true, Today).primaryAction)
    }

    @Test
    fun `a missing series is not found`() {
        assertTrue(seriesUiState(null, true, Today).notFound)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `selecting a source marks it and toggling flips the order`() = runTest {
        val vm =
            SeriesViewModel(
                FakeLibraryRepository(FakeLibrary.series(FixedClock)),
                "aztec-turning-of-heaven",
                FixedClock
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        vm.selectSource("mangafire")
        assertEquals("mangafire", vm.state.value.sources.single { it.selected }.id)

        vm.toggleOrder()
        assertEquals("1", vm.state.value.chapters.first().number)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class Screenshots {
    @Test
    fun series() = captureRoboImage("src/test/screenshots/series.png") {
        AsconTheme { SeriesScreen(previewSeriesState(FixedClock), SeriesActions()) }
    }
}
