package com.ascon.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ascon.core.model.PageFit
import com.ascon.core.model.PageGap
import com.ascon.core.model.ReaderBackground
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.ReadingMode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreReaderSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun open(file: File, scope: CoroutineScope) =
        DataStoreReaderSettings(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun `a series follows all series until it gets its own`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = open(File(folder.root, "a.preferences_pb"), scope)
        store.updateAllSeries { it.copy(background = ReaderBackground.Gray) }
        assertNull(store.forSeries("aztec").first())

        store.updateSeries("aztec") { it.copy(gap = PageGap.None) }
        assertEquals(
            ReaderSettings(background = ReaderBackground.Gray, gap = PageGap.None),
            store.forSeries("aztec").first()
        )
        assertEquals(ReaderSettings(background = ReaderBackground.Gray), store.allSeries.first())

        store.clearSeries("aztec")
        assertNull(store.forSeries("aztec").first())
        scope.cancel()
    }

    @Test
    fun `settings are read back by a new store`() = runTest {
        val file = File(folder.root, "b.preferences_pb")
        val changed = ReaderSettings(
            mode = ReadingMode.RightToLeft,
            fit = PageFit.Screen,
            gap = PageGap.Small,
            background = ReaderBackground.White,
            cropBorders = true,
            keepScreenOn = true,
            volumeKeys = true,
            showTapZones = true
        )
        val first = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        open(file, first).updateSeries("aztec") { changed }
        first.cancel()

        val second = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        assertEquals(changed, open(file, second).forSeries("aztec").first())
        second.cancel()
    }

    @Test
    fun `the reader opens by itself on a site until it is turned off there, kept by a new store`() = runTest {
        val file = File(folder.root, "sites.preferences_pb")
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = open(file, scope)
        assertTrue(store.autoOpen("mangafire.to").first())

        store.setAutoOpen("www.MangaFire.to", on = false)
        assertFalse(store.autoOpen("mangafire.to").first())
        assertTrue(store.autoOpen("asurascans.com").first())
        scope.cancel()

        val reopened = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val again = open(file, reopened)
        assertFalse(again.autoOpen("mangafire.to").first())
        again.setAutoOpen("mangafire.to", on = true)
        assertTrue(again.autoOpen("mangafire.to").first())
        reopened.cancel()
    }

    @Test
    fun `unknown values fall back to the defaults`() {
        assertEquals(
            ReaderSettings(gap = PageGap.None),
            DataStoreReaderSettings.decode("mode=Sideways;gap=None;keep_screen_on=maybe;extra=1")
        )
    }

    @Test
    fun `the reading pace keeps the latest 200 images`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = open(folder.newFile("pace.preferences_pb").also { it.delete() }, scope)
        repeat(201) { store.recordSecondsPerImage(it.toFloat()) }
        val samples = store.secondsPerImage.first()
        assertEquals(200, samples.size)
        assertEquals(1f, samples.first())
        scope.cancel()
    }
}
