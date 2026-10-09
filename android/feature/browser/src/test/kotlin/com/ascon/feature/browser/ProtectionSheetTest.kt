package com.ascon.feature.browser

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.ProtectionSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ProtectionSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = BrowserUiState(
        url = "https://mangafire.to/read/aztec/chapter-12",
        blocked = BlockedCounts(ads = 19, trackers = 6, redirects = 2)
    )

    private fun show(protection: ProtectionUiState, commands: BrowserCommands) = compose.setContent {
        AsconTheme { BrowserScreen(state, commands, protection) { FakePage(it) } }
    }

    @Test
    fun `the shield opens the sheet with this page's counts and switches`() {
        val changes = mutableListOf<String>()
        show(
            ProtectionUiState(settings = ProtectionSettings(blockPopups = false), site = "mangafire.to"),
            BrowserCommands(onSetAdblock = { changes += "ads $it" }, onSetTrusted = { changes += "trust $it" })
        )
        compose.onNodeWithContentDescription("Protection, 27 blocked").performClick()

        compose.onNodeWithText("Partly protected").assertExists()
        compose.onNodeWithText("19").assertExists()
        compose.onNodeWithText("Redirects stopped").assertExists()
        compose.onNodeWithText("Block popups and redirects").assertIsOff()
        compose.onNodeWithText("Block ads and trackers").assertIsOn().performClick()
        compose.onNodeWithText("Trust this site").performClick()
        assertEquals(listOf("ads false", "trust true"), changes)
    }

    @Test
    fun `site looks broken asks before turning protection off`() {
        var turnedOff = 0
        show(ProtectionUiState(site = "mangafire.to"), BrowserCommands(onTurnOffProtection = { turnedOff++ }))
        compose.onNodeWithContentDescription("Protection, 27 blocked").performClick()
        compose.onNodeWithText("Site looks broken?").performClick()

        compose.onNodeWithText("Turn off protection for mangafire.to?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Block ads and trackers").assertExists()

        compose.onNodeWithText("Site looks broken?").performClick()
        compose.onNodeWithText("Turn off and reload").performClick()
        assertEquals(1, turnedOff)
        compose.onNodeWithText("Turn off and reload").assertDoesNotExist()
    }

    @Test
    fun `a trusted site shows protection off and its undo`() {
        var undone = 0
        show(
            ProtectionUiState(
                settings = ProtectionSettings(trustedSites = setOf("mangafire.to")),
                site = "mangafire.to",
                undo = TrustNotice("mangafire.to", 1)
            ),
            BrowserCommands(onUndoTrust = { undone++ })
        )
        compose.onNodeWithText("Protection off for mangafire.to").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(1, undone)

        compose.onNodeWithContentDescription("Protection, 27 blocked").performClick()
        compose.onNodeWithText("Protection off").assertExists()
        compose.onNodeWithText("Site looks broken?").assertDoesNotExist()
    }
}
