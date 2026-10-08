package com.ascon.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.ascon.core.designsystem.component.Dot
import com.ascon.core.designsystem.component.Eyebrow
import com.ascon.core.designsystem.component.GroupedCard
import com.ascon.core.designsystem.component.ListRow
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.component.SwitchRow
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.AccountState
import com.ascon.core.model.ReadingMode
import com.ascon.core.model.Settings
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Immutable
data class SettingsActions(
    val onAccount: () -> Unit = {},
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
    SettingsScreen(
        state,
        actions.copy(
            onBlockAds = viewModel::setBlockAds,
            onBlockPopups = viewModel::setBlockPopups,
            onKeepScreenOn = viewModel::setKeepScreenOn
        ),
        bottomPadding
    )
}

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions, bottomPadding: Dp) {
    StatusBarIcons(darkIcons = true)
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
            AccountCard(state.account, state.now, actions.onAccount)
            Protection(state, actions)
            Reading(state.settings, actions)
            Library(state, actions)
            PlusCard(premium = (state.account as? AccountState.SignedIn)?.premium == true, onClick = actions.onPlus)
        }
        StatusBarScrim(visible = true)
    }
}

/** Card padding is 12, so the avatar's radius is the card's 20 minus 12. */
private val AvatarShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.Card, 12.dp))
private val AvatarGradient = Brush.linearGradient(listOf(Color(0xFFF2D5C4), Color(0xFFC98F7A)))
private val AvatarInk = Color(0xFF3A1A20)

@Composable
private fun AccountCard(account: AccountState, now: Instant, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AsconRadius.Card))
            .background(AsconColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val signedIn = account as? AccountState.SignedIn
        Box(
            Modifier
                .size(48.dp)
                .clip(AvatarShape)
                .then(
                    if (signedIn !=
                        null
                    ) {
                        Modifier.background(AvatarGradient)
                    } else {
                        Modifier.background(AsconColors.SurfaceMuted)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (signedIn != null) {
                Text(signedIn.displayName.take(1).uppercase(), style = AsconType.SectionTitle, color = AvatarInk)
            } else {
                Icon(AsconIcons.Person, null, tint = AsconColors.Ink, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (signedIn != null) {
                Text(signedIn.displayName, style = AsconType.ButtonLarge)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (signedIn.lastSyncedAt != null) Dot(AsconColors.Success)
                    Text(syncedText(signedIn.lastSyncedAt, now), style = AsconType.Meta, color = AsconColors.TextMuted)
                }
            } else {
                Text(stringResource(R.string.settings_sign_in), style = AsconType.ButtonLarge)
                Text(
                    stringResource(R.string.settings_sign_in_body),
                    style = AsconType.Meta,
                    color = AsconColors.TextMuted
                )
            }
        }
        Icon(AsconIcons.ChevronRight, null, tint = AsconColors.TextSubtle, modifier = Modifier.size(20.dp))
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private val SyncDate = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun syncedText(at: Instant?, now: Instant): String {
    if (at == null) return stringResource(R.string.settings_not_synced)
    val minutes = Duration.between(at, now).toMinutes().coerceAtLeast(0)
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        minutes < 1 -> stringResource(R.string.settings_synced_now)
        hours < 1 -> pluralStringResource(R.plurals.settings_synced_minutes, minutes.toInt(), minutes.toInt())
        hours < HOURS_PER_DAY -> pluralStringResource(R.plurals.settings_synced_hours, hours.toInt(), hours.toInt())
        else -> stringResource(R.string.settings_synced_on, at.atZone(ZoneId.systemDefault()).format(SyncDate))
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow(title, Modifier.padding(top = 4.dp))
        GroupedCard(contentPadding = PaddingValues(horizontal = 16.dp)) { content() }
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
            checked = state.settings.blockAds,
            onCheckedChange = actions.onBlockAds
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.settings_block_popups),
            subtitle = stringResource(R.string.settings_block_popups_body),
            checked = state.settings.blockPopups,
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
            value = state.settings.secureDnsProvider,
            onClick = actions.onSecureDns
        )
    }
}

@Composable
private fun Reading(settings: Settings, actions: SettingsActions) {
    Group(stringResource(R.string.settings_reading)) {
        ListRow(
            title = stringResource(R.string.settings_default_mode),
            value = stringResource(
                when (settings.readingMode) {
                    ReadingMode.LongStrip -> R.string.settings_mode_long_strip
                    ReadingMode.Paged -> R.string.settings_mode_paged
                }
            ),
            onClick = actions.onReadingMode
        )
        RowDivider()
        SwitchRow(
            title = stringResource(R.string.settings_keep_screen_on),
            checked = settings.keepScreenOn,
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

internal fun previewSettingsState(signedIn: Boolean = true): SettingsUiState {
    val now = Instant.parse("2026-10-08T12:00:00Z")
    return SettingsUiState(
        loading = false,
        account = if (signedIn) {
            AccountState.SignedIn("Kevin", premium = false, lastSyncedAt = now.minus(Duration.ofMinutes(2)))
        } else {
            AccountState.SignedOut
        },
        settings = Settings(),
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
