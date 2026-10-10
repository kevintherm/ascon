package com.ascon.feature.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.component.Snackbar
import com.ascon.core.designsystem.component.SnackbarArea
import com.ascon.core.designsystem.component.closesSnackbarOnTapOutside
import com.ascon.core.designsystem.theme.AsconTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The shared ink snackbar closes early on a swipe or a tap outside it, per Improvements 1. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class SnackbarTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun show() = compose.setContent {
        val area = SnackbarArea()
        AsconTheme {
            Box(Modifier.fillMaxSize().closesSnackbarOnTapOutside(area) { calls += "dismiss" }) {
                Column {
                    Text("Elsewhere", Modifier.size(200.dp).clickable { calls += "elsewhere" })
                    Snackbar(
                        "Browser closed",
                        action = "Undo",
                        onAction = { calls += "undo" },
                        onDismiss = { calls += "dismiss" },
                        area = area
                    )
                }
            }
        }
    }

    @Test
    fun `Undo runs without closing it early`() {
        show()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(listOf("undo"), calls)
    }

    @Test
    fun `a tap outside closes it and still reaches what it landed on`() {
        show()
        compose.onNodeWithText("Elsewhere").performClick()
        assertEquals(listOf("dismiss", "elsewhere"), calls)
    }

    @Test
    fun `a swipe sideways closes it`() {
        show()
        compose.onNodeWithText("Browser closed").performTouchInput { swipeRight() }
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `a swipe down closes it`() {
        show()
        compose.onNodeWithText("Browser closed").performTouchInput {
            swipeDown(
                startY = centerY,
                endY =
                centerY + 48.dp.toPx()
            )
        }
        assertEquals(listOf("dismiss"), calls)
    }
}
