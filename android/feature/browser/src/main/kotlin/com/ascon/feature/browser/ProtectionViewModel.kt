package com.ascon.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.model.ProtectionSettings
import com.ascon.feature.browser.web.OkHttpSiteKey
import com.ascon.feature.browser.web.SiteKey
import com.ascon.feature.browser.web.hostOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProtectionUiState(
    val settings: ProtectionSettings = ProtectionSettings(),
    /** The page's site, such as `mangafire.to`, the unit a user trusts. */
    val site: String = "",
    /** A change from the sheet saved; the page loads again so hidden elements and popups follow it. */
    val reload: Boolean = false,
    /** A site just trusted from "Site looks broken?", while its Undo shows. */
    val undo: TrustNotice? = null
) {
    val trusted: Boolean get() = settings.trusts(site)
}

data class TrustNotice(val site: String, val id: Long)

/**
 * The protection sheet: the user's protection settings, changed from the browser. Each
 * change reloads the page once it is saved, so the browser, which reads the settings on
 * every request, already sees it.
 */
class ProtectionViewModel(
    private val settings: ProtectionSettingsRepository,
    private val sites: SiteKey = OkHttpSiteKey
) : ViewModel() {
    private val _state = MutableStateFlow(ProtectionUiState(settings = settings.settings.value))
    val state: StateFlow<ProtectionUiState> = _state.asStateFlow()
    private var undoCount = 0L

    init {
        viewModelScope.launch { settings.settings.collect { saved -> _state.update { it.copy(settings = saved) } } }
    }

    /** Call when the page changes, so the sheet speaks of its site. */
    fun onPage(url: String) {
        val site = hostOf(url)?.let(sites::siteOf).orEmpty()
        _state.update { if (it.site == site) it else it.copy(site = site) }
    }

    fun setAdblock(enabled: Boolean) = change { it.copy(adblockEnabled = enabled) }

    fun setBlockPopups(enabled: Boolean) = change { it.copy(blockPopups = enabled) }

    fun setTrusted(trusted: Boolean) {
        val site = state.value.site.ifEmpty { return }
        change { it.copy(trustedSites = if (trusted) it.trustedSites + site else it.trustedSites - site) }
    }

    /** "Site looks broken?" confirmed: trusts the site, reloads, and offers Undo. */
    fun turnOffForSite() {
        val site = state.value.site.ifEmpty { return }
        setTrusted(true)
        _state.update { it.copy(undo = TrustNotice(site, ++undoCount)) }
    }

    fun undoTrust() {
        val site = state.value.undo?.site ?: return
        _state.update { it.copy(undo = null) }
        change { it.copy(trustedSites = it.trustedSites - site) }
    }

    fun undoShown(id: Long) {
        _state.update { if (it.undo?.id == id) it.copy(undo = null) else it }
    }

    fun reloaded() {
        _state.update { it.copy(reload = false) }
    }

    private fun change(transform: (ProtectionSettings) -> ProtectionSettings) {
        viewModelScope.launch {
            settings.update(transform)
            // Wait until the browser's copy holds the change, then reload.
            val saved = settings.load()
            settings.settings.first { it == saved }
            _state.update { it.copy(reload = true) }
        }
    }
}
