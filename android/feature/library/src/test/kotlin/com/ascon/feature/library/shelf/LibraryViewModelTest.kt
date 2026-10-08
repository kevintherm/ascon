package com.ascon.feature.library.shelf

import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.model.ReadingStatus
import com.ascon.feature.library.FixedClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(filter: ReadingStatus = ReadingStatus.Reading) =
        LibraryViewModel(FakeLibraryRepository(FakeLibrary.series(FixedClock)), filter)

    @Test
    fun `starts on the requested filter, most recent first`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(ReadingStatus.Reading, state.filter)
        assertEquals("aztec-turning-of-heaven", state.items.first().seriesId)
        assertTrue(state.items.all { it.currentChapter != null })
    }

    @Test
    fun `changing the filter changes the items`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.selectFilter(ReadingStatus.Plan)
        val state = vm.state.value
        assertEquals(ReadingStatus.Plan, state.filter)
        assertEquals(setOf("tidewater-saga", "hollow-crown-courier"), state.items.map { it.seriesId }.toSet())
        assertTrue(state.items.all { it.currentChapter == null })
    }

    @Test
    fun `an empty library is flagged so the empty state shows`() {
        assertTrue(libraryUiState(emptyList(), ReadingStatus.Reading).libraryEmpty)
    }

    @Test
    fun `progress is the current chapter over the latest`() {
        val item = libraryUiState(FakeLibrary.series(FixedClock), ReadingStatus.Reading).items
            .first { it.seriesId == "rust-belt-saints" }
        assertEquals(9f / 22f, item.progress, 0.0001f)
        assertEquals(0, item.newCount)
    }
}
