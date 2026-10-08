package com.ascon.feature.library

import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.AccountState
import com.ascon.core.model.ReadingStatus
import com.ascon.feature.library.home.HomeActions
import com.ascon.feature.library.home.HomeScreen
import com.ascon.feature.library.home.homeUiState
import com.ascon.feature.library.home.previewHomeState
import com.ascon.feature.library.shelf.LibraryActions
import com.ascon.feature.library.shelf.LibraryScreen
import com.ascon.feature.library.shelf.libraryUiState
import com.ascon.feature.library.shelf.previewLibraryState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden screenshots at the mockups' 390×844. Record with ./gradlew recordRoborazziDebug. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class Screenshots {
    @Test
    fun home() = captureRoboImage("src/test/screenshots/home.png") {
        AsconTheme { HomeScreen(previewHomeState(clock = FixedClock), HomeActions(), 120.dp) }
    }

    @Test
    fun homeEmpty() = captureRoboImage("src/test/screenshots/home_empty.png") {
        AsconTheme { HomeScreen(homeUiState(emptyList(), emptyList(), AccountState.SignedOut), HomeActions(), 120.dp) }
    }

    @Test
    fun library() = captureRoboImage("src/test/screenshots/library.png") {
        AsconTheme { LibraryScreen(previewLibraryState(clock = FixedClock), {}, LibraryActions(), 120.dp) }
    }

    @Test
    fun libraryEmpty() = captureRoboImage("src/test/screenshots/library_empty.png") {
        AsconTheme { LibraryScreen(libraryUiState(emptyList(), ReadingStatus.Reading), {}, LibraryActions(), 120.dp) }
    }
}
