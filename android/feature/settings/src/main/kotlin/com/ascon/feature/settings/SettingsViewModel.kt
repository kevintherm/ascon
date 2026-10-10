package com.ascon.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.AccountBackend
import com.ascon.core.data.AccountRefused
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.GoogleAccounts
import com.ascon.core.data.GoogleUnreachable
import com.ascon.core.data.NoGoogleAccount
import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.data.ReaderSettingsRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.data.SignInCancelled
import com.ascon.core.model.AccountState
import com.ascon.core.model.ProtectionSettings
import com.ascon.core.model.Quota
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.SettingsSummary
import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where signing in stands, per SettingsSignedOut, SettingsSigningIn and SettingsSignInFailed. */
sealed interface SignInStatus {
    data object Idle : SignInStatus

    data object SigningIn : SignInStatus

    data class Failed(val reason: SignInFailure) : SignInStatus
}

enum class SignInFailure {
    /** Google's account picker failed, usually for lack of a connection. */
    GoogleUnreachable,

    /** Ascon's server could not be reached. */
    ServerUnreachable,
    NoGoogleAccount,

    /** The server refused the Google sign-in. */
    Refused,

    /** This build has no backend or no Google client ID. */
    Unavailable
}

/** The account sheet's AI detection meter. */
sealed interface QuotaState {
    data object Loading : QuotaState

    data class Loaded(val quota: Quota) : QuotaState

    data object Failed : QuotaState
}

data class SettingsUiState(
    val loading: Boolean = true,
    val account: AccountState = AccountState.SignedOut,
    val signIn: SignInStatus = SignInStatus.Idle,
    val quota: QuotaState = QuotaState.Loading,
    /** The reader settings for all series. */
    val reader: ReaderSettings = ReaderSettings(),
    val protection: ProtectionSettings = ProtectionSettings(),
    val summary: SettingsSummary? = null,
    /** Sync times are shown relative to this moment. */
    val now: Instant = Instant.EPOCH
)

class SettingsViewModel(
    settings: SettingsRepository,
    private val protection: ProtectionSettingsRepository,
    private val reader: ReaderSettingsRepository,
    private val accounts: AccountRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** Null in builds without a backend, where signing in isn't available. */
    private val backend: AccountBackend? = null,
    private val google: GoogleAccounts? = null
) : ViewModel() {
    private val signIn = MutableStateFlow<SignInStatus>(SignInStatus.Idle)
    private val quota = MutableStateFlow<QuotaState>(QuotaState.Loading)

    val state: StateFlow<SettingsUiState> =
        combine(
            combine(reader.allSeries, protection.settings, settings.summary, ::Triple),
            accounts.account,
            signIn,
            quota
        ) { (reader, protection, summary), account, signIn, quota ->
            SettingsUiState(
                loading = false,
                account = account,
                signIn = signIn,
                quota = quota,
                reader = reader,
                protection = protection,
                summary = summary,
                now = clock.instant()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setBlockAds(enabled: Boolean) = updateProtection { it.copy(adblockEnabled = enabled) }

    fun setBlockPopups(enabled: Boolean) = updateProtection { it.copy(blockPopups = enabled) }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { reader.updateAllSeries { it.copy(keepScreenOn = enabled) } }
    }

    /**
     * Shows Google's account picker on the activity [context], then signs in to the backend
     * with the chosen account. Backing out of the picker leaves things as they were.
     */
    fun signIn(context: Context) {
        if (signIn.value == SignInStatus.SigningIn) return
        val backend = backend
        val google = google
        if (backend == null || google == null) {
            signIn.value = SignInStatus.Failed(SignInFailure.Unavailable)
            return
        }
        signIn.value = SignInStatus.SigningIn
        viewModelScope.launch {
            signIn.value = try {
                val picked = google.pick(context)
                val session = backend.signIn(picked.idToken)
                accounts.signedIn(session.copy(displayName = picked.displayName ?: picked.email, email = picked.email))
                SignInStatus.Idle
            } catch (_: SignInCancelled) {
                SignInStatus.Idle
            } catch (_: GoogleUnreachable) {
                SignInStatus.Failed(SignInFailure.GoogleUnreachable)
            } catch (_: NoGoogleAccount) {
                SignInStatus.Failed(SignInFailure.NoGoogleAccount)
            } catch (_: AccountRefused) {
                SignInStatus.Failed(SignInFailure.Refused)
            } catch (_: IOException) {
                SignInStatus.Failed(SignInFailure.ServerUnreachable)
            }
        }
    }

    /** Loads the AI detection meter when the account sheet opens. */
    fun loadQuota() {
        val token = accounts.token.value ?: return
        val backend = backend ?: return
        quota.value = QuotaState.Loading
        viewModelScope.launch {
            quota.value = try {
                QuotaState.Loaded(backend.quota(token))
            } catch (_: AccountRefused) {
                // The token was ended elsewhere, so this phone is signed out too.
                accounts.signedOut()
                QuotaState.Failed
            } catch (_: IOException) {
                QuotaState.Failed
            }
        }
    }

    /** Forgets the account here first, so signing out works offline, then ends the token. */
    fun signOut() {
        val token = accounts.token.value
        viewModelScope.launch {
            accounts.signedOut()
            quota.value = QuotaState.Loading
            signIn.value = SignInStatus.Idle
            if (token != null) {
                try {
                    backend?.signOut(token)
                } catch (_: IOException) {
                    // The token stays valid on the server but is gone from this phone.
                }
            }
        }
    }

    private fun updateProtection(transform: (ProtectionSettings) -> ProtectionSettings) {
        viewModelScope.launch { protection.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
