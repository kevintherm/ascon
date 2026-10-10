package com.ascon.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.ReadingMode
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = ReaderUiState(
        url = "https://mangafire.to/read/aztec/chapter-13",
        title = "Aztec Turning of Heaven",
        chapter = BigDecimal(13),
        host = "mangafire.to",
        pages = (1..20).map { "https://cdn.example/$it.webp" },
        next = null,
        previous = "https://mangafire.to/read/aztec/chapter-12"
    )

    private fun show(state: ReaderUiState, commands: ReaderCommands = ReaderCommands()) = compose.setContent {
        AsconTheme { ReaderScreen(state, commands) { index, _, modifier -> PendingPage("${index + 1}", modifier) } }
    }

    @Test
    fun `shows the series, chapter, site and page`() {
        show(state.copy(page = 3))
        compose.onNodeWithText("Aztec Turning of Heaven").assertExists()
        compose.onNodeWithText("Chapter 13 · mangafire.to").assertExists()
        compose.onNodeWithText("3 / 20").assertExists()
    }

    @Test
    fun `chapter buttons follow the links the site has`() {
        var opened = ""
        show(state, ReaderCommands(onPrevious = { opened = "previous" }))
        compose.onNodeWithContentDescription("Next chapter").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous").assertIsEnabled().performClick()
        assertEquals("previous", opened)
    }

    @Test
    fun `a tap on the pages toggles the bars and the first page counts as shown`() {
        var toggles = 0
        val shown = mutableListOf<Int>()
        show(state, ReaderCommands(onToggleBars = { toggles++ }, onPageShown = { shown += it }))
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(center) }
        // A single tap waits to tell itself from a double tap.
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT)
        compose.waitForIdle()
        assertEquals(1, toggles)
        assertEquals(listOf(0), shown)
    }

    @Test
    fun `side taps scroll most of a screen and leave the bars`() {
        var toggles = 0
        val shown = mutableListOf<Int>()
        show(state, ReaderCommands(onToggleBars = { toggles++ }, onPageShown = { shown += it }))
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(centerRight - Offset(10f, 0f)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT)
        compose.waitForIdle()
        assertEquals(0, toggles)
        assertEquals(listOf(0, 1), shown)
        // Quick side taps each scroll, rather than making a double tap.
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(centerRight - Offset(10f, 0f)) }
        compose.waitForIdle()
        assertEquals(2, shown.last())
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(centerLeft + Offset(10f, 0f)) }
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT)
        compose.waitForIdle()
        assertEquals(1, shown.last())
    }

    @Test
    fun `a long press on a page offers to share it`() {
        var shared = -1
        show(state, ReaderCommands(onSharePage = { shared = it }))
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { longClick(center) }
        compose.onNodeWithText("Page 1").assertExists()
        compose.onNodeWithText("Share image").performClick()
        assertEquals(0, shared)
    }

    @Test
    fun `hidden bars leave only the pages`() {
        show(state.copy(barsVisible = false))
        compose.onNodeWithText("Aztec Turning of Heaven").assertDoesNotExist()
    }

    @Test
    fun `paged modes turn a page per side tap, mirrored right to left`() {
        val shown = mutableListOf<Int>()
        val ltr = state.copy(settings = ReaderSettings(mode = ReadingMode.LeftToRight))
        var current by mutableStateOf(ltr)
        compose.setContent {
            AsconTheme {
                ReaderScreen(current, ReaderCommands(onPageShown = { shown += it })) { index, _, modifier ->
                    PendingPage("${index + 1}", modifier)
                }
            }
        }
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(centerRight - Offset(10f, 0f)) }
        compose.waitForIdle()
        assertEquals(1, shown.last())

        current = ltr.copy(settings = ReaderSettings(mode = ReadingMode.RightToLeft), page = 2)
        compose.waitForIdle()
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(centerLeft + Offset(10f, 0f)) }
        compose.waitForIdle()
        assertEquals(2, shown.last())
    }

    @Test
    fun `tap zones name what each third does`() {
        show(state.copy(settings = ReaderSettings(mode = ReadingMode.RightToLeft, showTapZones = true)))
        compose.onNodeWithText("Next page").assertExists()
        compose.onNodeWithText("Menu").assertExists()
        compose.onNodeWithText("Previous page").assertExists()
    }
}

private const val DOUBLE_TAP_WAIT = 500L
