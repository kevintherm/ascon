package com.ascon.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.model.ProtectionSettings
import com.ascon.core.model.SecureDns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [ProtectionSettings] in a Preferences DataStore. Make one per file, for the whole app. */
class DataStoreProtectionSettings(private val store: DataStore<Preferences>, scope: CoroutineScope) :
    ProtectionSettingsRepository {
    private val saved = store.data.map { it.toSettings() }

    override val settings: StateFlow<ProtectionSettings> =
        saved.stateIn(scope, SharingStarted.Eagerly, ProtectionSettings())

    override suspend fun load(): ProtectionSettings = saved.first()

    override suspend fun update(transform: (ProtectionSettings) -> ProtectionSettings) {
        store.edit { it.write(transform(it.toSettings())) }
    }

    companion object {
        fun open(context: Context, scope: CoroutineScope) = DataStoreProtectionSettings(
            PreferenceDataStoreFactory.create(scope = scope) {
                context.applicationContext.preferencesDataStoreFile("protection")
            },
            scope
        )

        private val AdblockEnabled = booleanPreferencesKey("adblock_enabled")
        private val BlockPopups = booleanPreferencesKey("block_popups")
        private val DisabledFilterLists = stringSetPreferencesKey("disabled_filter_lists")
        private val TrustedSites = stringSetPreferencesKey("trusted_sites")
        private val SecureDnsKey = stringPreferencesKey("secure_dns")

        private fun Preferences.toSettings(): ProtectionSettings {
            val defaults = ProtectionSettings()
            return ProtectionSettings(
                adblockEnabled = this[AdblockEnabled] ?: defaults.adblockEnabled,
                blockPopups = this[BlockPopups] ?: defaults.blockPopups,
                disabledFilterLists = this[DisabledFilterLists] ?: defaults.disabledFilterLists,
                trustedSites = this[TrustedSites] ?: defaults.trustedSites,
                secureDns = SecureDns.entries.firstOrNull { it.name.lowercase() == this[SecureDnsKey] }
                    ?: defaults.secureDns
            )
        }

        private fun MutablePreferences.write(settings: ProtectionSettings) {
            this[AdblockEnabled] = settings.adblockEnabled
            this[BlockPopups] = settings.blockPopups
            this[DisabledFilterLists] = settings.disabledFilterLists
            this[TrustedSites] = settings.trustedSites
            this[SecureDnsKey] = settings.secureDns.name.lowercase()
        }
    }
}
