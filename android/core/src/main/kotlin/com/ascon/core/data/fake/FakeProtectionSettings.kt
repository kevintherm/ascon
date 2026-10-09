package com.ascon.core.data.fake

import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.model.ProtectionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In memory, for tests and previews. The app uses the DataStore one. */
class FakeProtectionSettings(initial: ProtectionSettings = ProtectionSettings()) : ProtectionSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: StateFlow<ProtectionSettings> = state

    override suspend fun load(): ProtectionSettings = state.value

    override suspend fun update(transform: (ProtectionSettings) -> ProtectionSettings) {
        state.update(transform)
    }
}
