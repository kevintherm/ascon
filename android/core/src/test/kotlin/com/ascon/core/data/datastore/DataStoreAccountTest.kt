package com.ascon.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ascon.core.model.AccountSession
import com.ascon.core.model.AccountState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAccountTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the session survives a restart until sign-out`() = runTest {
        val file = File(folder.root, "account.preferences_pb")
        val first = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val store = DataStoreAccount(PreferenceDataStoreFactory.create(scope = first) { file }, first)
        assertEquals(AccountState.SignedOut, store.account.first())
        assertNull(store.token.value)

        store.signedIn(AccountSession("tok", "Kevin", "kevin@example.com", premium = false))
        assertEquals("tok", store.token.value)
        first.cancel()

        val second = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val reopened = DataStoreAccount(PreferenceDataStoreFactory.create(scope = second) { file }, second)
        assertEquals(
            AccountState.SignedIn("Kevin", "kevin@example.com", premium = false),
            reopened.account.first()
        )
        assertEquals("tok", reopened.token.value)

        reopened.signedOut()
        assertEquals(AccountState.SignedOut, reopened.account.first())
        assertNull(reopened.token.value)
        second.cancel()
    }
}
