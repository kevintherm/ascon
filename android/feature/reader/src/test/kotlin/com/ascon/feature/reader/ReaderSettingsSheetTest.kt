package com.ascon.feature.reader

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.PageGap
import com.ascon.core.model.ReaderBackground
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
class ReaderSettingsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = ReaderUiState(
        url = "https://mangafire.to/read/aztec/chapter-13",
        title = "Aztec Turning of Heaven",
        chapter = BigDecimal(13),
        host = "mangafire.to",
        pages = (1..5).map { "https://cdn.example/$it.webp" },
        next = null,
        previous = null,
        seriesId = "aztec"
    )

    private var settings = ReaderSettings()
    private val scopes = mutableListOf<SettingsScope>()

    private fun show(state: ReaderUiState) = compose.setContent {
        AsconTheme {
            ReaderScreen(
                state,
                ReaderCommands(
                    onSettingsScope = { scopes += it },
                    onChangeSettings = { settings = it(settings) }
                )
            ) { index, _, modifier -> PendingPage("${index + 1}", modifier) }
        }
    }

    @Test
    fun `aa opens the settings and changes go to the view model`() {
        show(state)
        compose.onNodeWithContentDescription("Reader settings").performClick()

        compose.onNodeWithText("This series").assertIsSelected()
        compose.onNodeWithText("Auto").assertIsSelected()
        compose.onNodeWithText("None").performClick()
        compose.onNodeWithContentDescription("White").performClick()
        compose.onNodeWithText("Keep screen on").assertIsOff().performClick()
        compose.onNodeWithText("All series").performClick()

        assertEquals(
            ReaderSettings(gap = PageGap.None, background = ReaderBackground.White, keepScreenOn = true),
            settings
        )
        assertEquals(listOf(SettingsScope.AllSeries), scopes)
    }

    @Test
    fun `modes, crop borders and tap zones change the settings`() {
        show(state)
        compose.onNodeWithContentDescription("Reader settings").performClick()

        compose.onNodeWithText("Long strip").assertIsSelected()
        compose.onNodeWithText("Right to left").performClick()
        compose.onNodeWithText("Crop borders").performClick()
        compose.onNodeWithText("Show tap zones").performClick()
        assertEquals(
            ReaderSettings(mode = ReadingMode.RightToLeft, cropBorders = true, showTapZones = true),
            settings
        )
    }

    @Test
    fun `a chapter outside the library has no scope to choose`() {
        show(state.copy(seriesId = null, settingsScope = SettingsScope.AllSeries))
        compose.onNodeWithContentDescription("Reader settings").performClick()

        compose.onNodeWithText("Page gap").assertExists()
        compose.onNodeWithText("This series").assertDoesNotExist()
    }
}
