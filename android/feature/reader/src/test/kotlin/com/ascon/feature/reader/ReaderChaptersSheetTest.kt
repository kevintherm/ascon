package com.ascon.feature.reader

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.Chapter
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ReaderChaptersSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 10)

    private val state = ReaderUiState(
        url = "https://mangafire.to/read/aztec/chapter-12",
        title = "Aztec Turning of Heaven",
        chapter = BigDecimal(12),
        host = "mangafire.to",
        pages = (1..58).map { "https://cdn.example/$it.webp" },
        page = 34,
        next = null,
        previous = null,
        seriesId = "aztec",
        chapters = listOf(
            Chapter(BigDecimal(11), today.minusDays(9), read = true, readOnSourceId = "plus"),
            Chapter(BigDecimal(12), today.minusDays(9), read = false),
            Chapter(BigDecimal(13), today.minusDays(9), read = false, isNew = true),
            Chapter(BigDecimal(14), today, read = false, isNew = true)
        ),
        siteNames = mapOf("plus" to "MANGA Plus"),
        today = today
    )

    private val opened = mutableListOf<BigDecimal>()

    private fun show(state: ReaderUiState) = compose.setContent {
        AsconTheme {
            ReaderScreen(state, ReaderCommands(onOpenChapterNumber = { opened += it })) { index, _, modifier ->
                PendingPage("${index + 1}", modifier)
            }
        }
    }

    @Test
    fun `lists the chapters newest first with this one marked`() {
        show(state)
        compose.onNodeWithText("Chapters").performClick()

        compose.onNodeWithText("4 chapters · 2 new").assertExists()
        compose.onNodeWithText("READING · P. 34 / 58").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("via MANGA Plus").assertExists()
        compose.onNodeWithText("Chapter 14").performClick()
        assertEquals(listOf(BigDecimal(14)), opened)
    }

    @Test
    fun `go to chapter opens a chapter by its number`() {
        show(state)
        compose.onNodeWithText("Chapters").performClick()
        compose.onNodeWithContentDescription("Go to chapter").performTextInput("3")
        compose.onNodeWithContentDescription("Go to chapter").performImeAction()

        assertEquals(listOf(BigDecimal(3)), opened)
    }

    @Test
    fun `without the number in the address chapters can't be opened`() {
        show(state.copy(url = "https://mangafire.to/read/aztec/xk2p"))
        compose.onNodeWithText("Chapters").performClick()

        compose.onNodeWithText("Chapter 14").assertHasNoClickAction()
        compose.onAllNodesWithText("Chapter 12")[0].assertExists()
        compose.onNodeWithContentDescription("Go to chapter").performTextInput("9")
        compose.onNodeWithContentDescription("Go to chapter").performImeAction()
        compose.onNodeWithText("Ascon can't tell where chapter 9 is on mangafire.to.").assertExists()
        assertEquals(emptyList<BigDecimal>(), opened)
    }
}
