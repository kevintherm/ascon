package com.ascon.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ascon.core.data.AccountRepository
import com.ascon.core.model.AccountSession
import com.ascon.core.model.AccountState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The signed-in account, in app-private preferences. The app has backups turned off, so
 * the account token never leaves this phone.
 */
class DataStoreAccount(private val store: DataStore<Preferences>, scope: CoroutineScope) : AccountRepository {
    override val account: Flow<AccountState> = store.data.map { prefs ->
        if (prefs[Token] == null) {
            AccountState.SignedOut
        } else {
            AccountState.SignedIn(
                displayName = prefs[Name].orEmpty(),
                email = prefs[Email],
                premium = prefs[Premium] ?: false
            )
        }
    }

    override val token: StateFlow<String?> = store.data.map { it[Token] }.stateIn(scope, SharingStarted.Eagerly, null)

    override suspend fun signedIn(session: AccountSession) {
        store.edit {
            it[Token] = session.token
            it[Name] = session.displayName
            if (session.email == null) it.remove(Email) else it[Email] = session.email
            it[Premium] = session.premium
        }
    }

    override suspend fun signedOut() {
        store.edit { it.clear() }
    }

    companion object {
        fun open(context: Context, scope: CoroutineScope) = DataStoreAccount(
            PreferenceDataStoreFactory.create(scope = scope) {
                context.applicationContext.preferencesDataStoreFile("account")
            },
            scope
        )

        private val Token = stringPreferencesKey("token")
        private val Name = stringPreferencesKey("name")
        private val Email = stringPreferencesKey("email")
        private val Premium = booleanPreferencesKey("premium")
    }
}
