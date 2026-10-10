package com.ascon.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.Dot
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.AccountState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Card padding is 12, so the avatar's radius is the card's 20 minus 12. */
private val AvatarShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.Card, 12.dp))
internal val AvatarGradient = Brush.linearGradient(listOf(Color(0xFFF2D5C4), Color(0xFFC98F7A)))
internal val AvatarInk = Color(0xFF3A1A20)

/** The signed-out card's padding is 14; its avatar, note and button nest inside it. */
private const val SIGNED_OUT_PADDING = 14
private val SignedOutInner = RoundedCornerShape(AsconRadius.nested(AsconRadius.Card, SIGNED_OUT_PADDING.dp))

@Composable
internal fun AccountCard(account: AccountState, signIn: SignInStatus, now: Instant, actions: SettingsActions) {
    when (account) {
        is AccountState.SignedIn -> SignedInCard(account, now, actions.onAccount)
        AccountState.SignedOut -> SignedOutCard(signIn, actions.onSignIn)
    }
}

/** Settings header for a signed-in user, per Settings. It opens the account sheet. */
@Composable
private fun SignedInCard(account: AccountState.SignedIn, now: Instant, onClick: () -> Unit) {
    val label = stringResource(R.string.settings_account_label, account.displayName)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AsconRadius.Card))
            .background(AsconColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(AvatarShape).background(AvatarGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(account.displayName.take(1).uppercase(), style = AsconType.SectionTitle, color = AvatarInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.displayName, style = AsconType.ButtonLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Until sync is built there is no sync time, so the card names the account instead.
            val synced = account.lastSyncedAt
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (synced != null) Dot(AsconColors.Success)
                Text(
                    if (synced != null) syncedText(synced, now) else account.email.orEmpty(),
                    style = AsconType.Meta,
                    color = AsconColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(AsconIcons.ChevronRight, null, tint = AsconColors.TextSubtle, modifier = Modifier.size(20.dp))
    }
}

/** Per SettingsSignedOut, SettingsSigningIn and SettingsSignInFailed. */
@Composable
private fun SignedOutCard(signIn: SignInStatus, onSignIn: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AsconRadius.Card))
            .background(AsconColors.Surface)
            .padding(SIGNED_OUT_PADDING.dp),
        verticalArrangement = Arrangement.spacedBy(if (signIn is SignInStatus.Failed) 12.dp else 14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(SignedOutInner).background(AsconColors.SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(AsconIcons.Person, null, tint = AsconColors.TextMuted, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.settings_signed_out), style = AsconType.ButtonLarge)
                Text(
                    stringResource(R.string.settings_signed_out_body),
                    style = AsconType.Meta,
                    color = AsconColors.TextMuted
                )
            }
        }
        when (signIn) {
            SignInStatus.Idle -> SignInButton(onSignIn)
            SignInStatus.SigningIn -> SigningInButton()
            is SignInStatus.Failed -> {
                FailureNote(signIn.reason)
                TryAgainButton(onSignIn)
            }
        }
        Text(
            stringResource(R.string.settings_sign_in_explainer),
            style = AsconType.Meta.copy(lineHeight = 1.45.em),
            color = AsconColors.TextMuted,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun SignInButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(SignedOutInner)
            .border(BorderStroke(1.dp, AsconColors.Border), SignedOutInner)
            .background(AsconColors.Surface)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(AsconColors.Ground),
            contentAlignment = Alignment.Center
        ) {
            Text("G", style = AsconType.Badge.copy(fontSize = 14.sp), color = AsconColors.Ink)
        }
        Text(stringResource(R.string.settings_sign_in), style = AsconType.Button, color = AsconColors.Ink)
    }
}

@Composable
private fun SigningInButton() {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(SignedOutInner)
            .border(BorderStroke(1.dp, AsconColors.SurfaceSunken), SignedOutInner)
            .background(AsconColors.Ground)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = AsconColors.Ink,
            trackColor = AsconColors.Border,
            strokeWidth = 2.4.dp
        )
        Text(stringResource(R.string.settings_signing_in), style = AsconType.Button, color = AsconColors.TextMuted)
    }
}

@Composable
private fun FailureNote(reason: SignInFailure) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SignedOutInner)
            .background(AsconColors.DangerSoft)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            AsconIcons.Alert,
            null,
            tint = AsconColors.Danger,
            modifier = Modifier.padding(top = 1.dp).size(18.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.settings_sign_in_failed),
                style = AsconType.CardTitle,
                color = AsconColors.DangerStrong
            )
            Text(
                stringResource(
                    when (reason) {
                        SignInFailure.GoogleUnreachable -> R.string.settings_sign_in_google_unreachable
                        SignInFailure.ServerUnreachable -> R.string.settings_sign_in_server_unreachable
                        SignInFailure.NoGoogleAccount -> R.string.settings_sign_in_no_google_account
                        SignInFailure.Refused -> R.string.settings_sign_in_refused
                        SignInFailure.Unavailable -> R.string.settings_sign_in_unavailable
                    }
                ),
                style = AsconType.Meta.copy(lineHeight = 1.4.em),
                color = AsconColors.DangerBody
            )
        }
    }
}

@Composable
private fun TryAgainButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(SignedOutInner)
            .background(AsconColors.Ink)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AsconIcons.Reload, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_try_again), style = AsconType.Button, color = Color.White)
    }
}

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private val SyncDate = DateTimeFormatter.ofPattern("MMM d")

/** How long ago [at] was, as "Synced 2 min ago", or "2 min ago" when [short] for a row that names it. */
@Composable
internal fun syncedText(at: Instant, now: Instant, short: Boolean = false): String {
    val minutes = Duration.between(at, now).toMinutes().coerceAtLeast(0)
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        minutes < 1 -> stringResource(if (short) R.string.account_synced_now else R.string.settings_synced_now)
        hours < 1 -> pluralStringResource(
            if (short) R.plurals.account_synced_minutes else R.plurals.settings_synced_minutes,
            minutes.toInt(),
            minutes.toInt()
        )
        hours < HOURS_PER_DAY -> pluralStringResource(
            if (short) R.plurals.account_synced_hours else R.plurals.settings_synced_hours,
            hours.toInt(),
            hours.toInt()
        )
        short -> at.atZone(ZoneId.systemDefault()).format(SyncDate)
        else -> stringResource(R.string.settings_synced_on, at.atZone(ZoneId.systemDefault()).format(SyncDate))
    }
}
