package com.ascon.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.component.Eyebrow
import com.ascon.core.designsystem.component.GroupedCard
import com.ascon.core.designsystem.component.ListRow
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.component.SwitchRow
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.AccountState
import com.ascon.core.model.Quota
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.ReadingMode
import java.text.NumberFormat
import java.time.Instant

@Immutable
data class SettingsActions(
    /** Opens the account sheet. */
    val onAccount: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    /** Whether the account sheet or the sign-out dialog is open, so the app can hide its nav bar. */
    val onOverlay: (Boolean) -> Unit = {},
    val onBlockAds: (Boolean) -> Unit = {},
    val onBlockPopups: (Boolean) -> Unit = {},
    val onFilterLists: () -> Unit = {},
    val onSecureDns: () -> Unit = {},
    val onReadingMode: () -> Unit = {},
    val onKeepScreenOn: (Boolean) -> Unit = {},
    val onHiddenSeries: () -> Unit = {},
    val onDownloads: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onPlus: () -> Unit = {}
)

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, actions: SettingsActions, bottomPadding: Dp) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Google's account picker needs the activity.
    val context = LocalContext.current
    SettingsScreen(
        state,
        actions.copy(
            onAccount = viewModel::loadQuota,
            onSignIn = { viewModel.signIn(context) },
            onSignOut = viewModel::signOut,
            onBlockAds = viewModel::setBlockAds,
            onBlockPopups = viewModel::setBlockPopups,
            onKeepScreenOn = viewModel::setKeepScreenOn
        ),
        bottomPadding
    )
}

/** What is open over Settings. */
enum class AccountOverlay { None, Sheet, SignOut }

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    bottomPadding: Dp,
    initialOverlay: AccountOverlay = AccountOverlay.None
) {
    StatusBarIcons(darkIcons = true)
    var overlay by rememberSaveable { mutableStateOf(initialOverlay) }
    val signedIn = state.account as? AccountState.SignedIn
    // Signing out, here or because the token stopped working, closes what was open.
    if (signedIn == null) overlay = AccountOverlay.None
    val open = overlay != AccountOverlay.None
    DisposableEffect(open) {
        actions.onOverlay(open)
        onDispose { actions.onOverlay(false) }
    }
    Box(Modifier.fillMaxSize().background(AsconColors.Ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = AsconType.ScreenTitle,
                modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
            )
            AccountCard(
                state.account,
                state.signIn,
                state.now,
                actions.copy(
                    onAccount = {
                        overlay = AccountOverlay.Sheet
                        actions.onAccount()
                    }
                )
            )
            Protection(state, actions)
            Reading(state.reader, actions)
            Library(state, actions)
            PlusCard(premium = (state.account as? AccountState.SignedIn)?.premium == true, onClick = actions.onPlus)
        }
        StatusBarScrim(visible = true)
        AccountSheet(
            account = signedIn.takeIf { overlay == AccountOverlay.Sheet },
            quota = state.quota,
            onDismiss = { overlay = AccountOverlay.None },
            onSignOut = { overlay = AccountOverlay.SignOut }
        )
        SignOutDialog(
            visible = overlay == AccountOverlay.SignOut,
            onDismiss = { overlay = AccountOverlay.Sheet },
            onSignOut = {
                overlay = AccountOverlay.None
                actions.onSignOut()
            }
        )
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(title, Modifier.padding(top = 4.dp))
        GroupedCard { content() }
    }
}

@Composable
private fun Protection(state: SettingsUiState, actions: SettingsActions) {
    val summary = state.summary
    Group(stringResource(R.string.settings_protection)) {
        SwitchRow(
            title = stringResource(R.string.settings_block_ads),
            subtitle = summary?.let {
                stringResource(
                    R.string.settings_blocked_this_week,
                    NumberFormat.getIntegerInstance().format(it.blockedThisWeek)
                )
            },
            checked = state.protection.adblockEnabled,
            onCheckedChange = actions.onBlockAds
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.settings_block_popups),
            subtitle = stringResource(R.string.settings_block_popups_body),
            checked = state.protection.blockPopups,
            onCheckedChange = actions.onBlockPopups
        )
        RowDivider()
        ListRow(
            title = stringResource(R.string.settings_filter_lists),
            value = summary?.let {
                pluralStringResource(R.plurals.settings_filter_lists_active, it.activeFilterLists, it.activeFilterLists)
            },
            onClick = actions.onFilterLists
        )
        RowDivider()
        ListRow(
            title = stringResource(R.string.settings_secure_dns),
            value = state.protection.secureDns.label,
            onClick = actions.onSecureDns
        )
    }
}

@Composable
private fun Reading(reader: ReaderSettings, actions: SettingsActions) {
    Group(stringResource(R.string.settings_reading)) {
        ListRow(
            title = stringResource(R.string.settings_default_mode),
            value = stringResource(
                when (reader.mode) {
                    ReadingMode.LongStrip -> R.string.settings_mode_long_strip
                    ReadingMode.LeftToRight -> R.string.settings_mode_left_to_right
                    ReadingMode.RightToLeft -> R.string.settings_mode_right_to_left
                }
            ),
            onClick = actions.onReadingMode
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.settings_keep_screen_on),
            checked = reader.keepScreenOn,
            onCheckedChange = actions.onKeepScreenOn
        )
    }
}

@Composable
private fun Library(state: SettingsUiState, actions: SettingsActions) {
    val summary = state.summary
    val context = LocalContext.current
    Group(stringResource(R.string.settings_library)) {
        ListRow(
            title = stringResource(R.string.settings_hidden_series),
            value = summary?.let {
                stringResource(
                    if (it.hiddenSeriesLocked) R.string.settings_hidden_locked else R.string.settings_hidden_unlocked
                )
            },
            onClick = actions.onHiddenSeries
        )
        RowDivider()
        ListRow(
            title = stringResource(R.string.settings_downloads),
            value = summary?.let { Formatter.formatShortFileSize(context, it.downloadsBytes) },
            onClick = actions.onDownloads
        )
        RowDivider()
        ListRow(
            title = stringResource(R.string.settings_import),
            value = stringResource(R.string.settings_import_sources),
            onClick = actions.onImport
        )
    }
}

private val PlusHeight = 64.dp
private val UpgradeHeight = 36.dp

/** The upgrade pill is centered in the 64 card, so its gap to the card edge is 14. */
private val UpgradeShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.Card, (PlusHeight - UpgradeHeight) / 2))

@Composable
private fun PlusCard(premium: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PlusHeight)
            .clip(RoundedCornerShape(AsconRadius.Card))
            .background(AsconColors.Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.settings_plus), style = AsconType.Button, color = Color.White)
            Text(
                stringResource(if (premium) R.string.settings_plus_active else R.string.settings_plus_body),
                style = AsconType.Small,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        if (!premium) {
            Box(
                Modifier
                    .height(UpgradeHeight)
                    .clip(UpgradeShape)
                    .background(AsconColors.BrandGradient)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.settings_upgrade), style = AsconType.ButtonSmall, color = Color.White)
            }
        }
    }
}

internal fun previewSettingsState(
    signedIn: Boolean = true,
    signIn: SignInStatus = SignInStatus.Idle,
    premium: Boolean = false
): SettingsUiState {
    val now = Instant.parse("2026-10-08T12:00:00Z")
    return SettingsUiState(
        loading = false,
        account = if (signedIn) {
            AccountState.SignedIn("Kevin", "kevin@example.com", premium = premium)
        } else {
            AccountState.SignedOut
        },
        signIn = signIn,
        quota = QuotaState.Loaded(
            if (premium) {
                Quota(limit = 200, remaining = 184, resetsAt = Instant.parse("2026-11-01T00:00:00Z"), premium = true)
            } else {
                Quota(limit = 10, remaining = 7, resetsAt = Instant.parse("2026-11-01T00:00:00Z"), premium = false)
            }
        ),
        reader = ReaderSettings(),
        summary = FakeLibrary.settingsSummary,
        now = now
    )
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsPreview() {
    AsconTheme { SettingsScreen(previewSettingsState(), SettingsActions(), bottomPadding = 120.dp) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettingsSignedOutPreview() {
    AsconTheme { SettingsScreen(previewSettingsState(signedIn = false), SettingsActions(), bottomPadding = 120.dp) }
}
