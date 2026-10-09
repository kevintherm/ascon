package com.ascon.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ascon.core.model.ProtectionSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreProtectionSettingsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun open(file: File, scope: CoroutineScope) =
        DataStoreProtectionSettings(PreferenceDataStoreFactory.create(scope = scope) { file }, scope)

    @Test
    fun `starts with the defaults`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        assertEquals(ProtectionSettings(), open(File(folder.root, "a.preferences_pb"), scope).load())
        scope.cancel()
    }

    @Test
    fun `changes are saved and read back by a new store`() = runTest {
        val file = File(folder.root, "b.preferences_pb")
        val changed = ProtectionSettings(
            adblockEnabled = false,
            blockPopups = false,
            disabledFilterLists = setOf("easyprivacy"),
            trustedSites = setOf("example.com")
        )
        val first = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val repo = open(file, first)
        repo.update { changed }
        assertEquals(changed, repo.settings.value)
        // DataStore allows one store per file at a time.
        first.cancel()

        val second = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        assertEquals(changed, open(file, second).load())
        second.cancel()
    }
}
