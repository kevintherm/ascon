package com.ascon.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ascon.core.data.DeviceTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

/**
 * The backend's device token, in plain preferences. It is anonymous and only opens free
 * calls such as rule lookup, so it isn't worth the encrypted store the API suggests.
 */
class DataStoreDeviceToken(private val store: DataStore<Preferences>) : DeviceTokenStore {
    override suspend fun token(): String? = store.data.first()[Token]

    override suspend fun saveToken(token: String?) {
        store.edit { if (token == null) it.remove(Token) else it[Token] = token }
    }

    companion object {
        fun open(context: Context, scope: CoroutineScope) = DataStoreDeviceToken(
            PreferenceDataStoreFactory.create(scope = scope) {
                context.applicationContext.preferencesDataStoreFile("device")
            }
        )

        private val Token = stringPreferencesKey("token")
    }
}
