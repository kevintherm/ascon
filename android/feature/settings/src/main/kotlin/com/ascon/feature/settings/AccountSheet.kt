package com.ascon.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.component.ConfirmDialog
import com.ascon.core.designsystem.component.ProgressTrack
import com.ascon.core.designsystem.component.solidFill
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.AccountState
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The badge and sheet buttons share no corner with what holds them, so they keep the drawn radii. */
private val BadgeShape = RoundedCornerShape(9.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

private val GroupShape = RoundedCornerShape(AsconRadius.Card)

/** Quotas reset at 00:00 UTC on the 1st, so the date is read in UTC. */
private val ResetDate = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneOffset.UTC)

/**
 * The account sheet, per AccountSheet and AccountSheetFree. The Upgrade button, AI
 * translation and sync rows wait for billing, translation and sync.
 */
@Composable
internal fun AccountSheet(
    account: AccountState.SignedIn?,
    quota: QuotaState,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    BottomSheet(visible = account != null, onDismiss = onDismiss) {
        if (account == null) return@BottomSheet
        Row(
            Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(AvatarGradient),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    account.displayName.take(1).uppercase(),
                    style = AsconType.SectionTitle.copy(fontSize = 22.sp),
                    color = AvatarInk
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    account.displayName,
                    style = AsconType.SectionTitle.copy(fontSize = 19.sp),
                    color = AsconColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                account.email?.let {
                    Text(
                        it,
                        style = AsconType.Value,
                        color = AsconColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().clip(GroupShape).background(AsconColors.Ground).padding(horizontal = 16.dp)) {
            PlanRow(account.premium)
            Box(Modifier.fillMaxWidth().height(1.dp).background(AsconColors.DividerOnGround))
            DetectionsRow(quota)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(ButtonShape)
                .background(AsconColors.Ground)
                .clickable(role = Role.Button, onClick = onSignOut),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AsconIcons.SignOut, null, tint = AsconColors.Danger, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.account_sign_out), style = AsconType.Button, color = AsconColors.Danger)
        }
    }
}

@Composable
private fun PlanRow(premium: Boolean) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.account_plan), style = AsconType.RowTitleRead, modifier = Modifier.weight(1f))
        Row(
            Modifier
                .height(30.dp)
                .clip(BadgeShape)
                .background(if (premium) AsconColors.BrandGradient else solidFill(AsconColors.DividerOnGround))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ink = if (premium) Color.White else AsconColors.Ink
            if (premium) Icon(AsconIcons.Star, null, tint = ink, modifier = Modifier.size(14.dp))
            Text(
                stringResource(if (premium) R.string.account_premium else R.string.account_free),
                style = AsconType.Badge.copy(fontSize = 13.sp, letterSpacing = 0.02.em),
                color = ink
            )
        }
    }
}

@Composable
private fun DetectionsRow(quota: QuotaState) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.account_detections), style = AsconType.RowTitleRead)
        val loaded = (quota as? QuotaState.Loaded)?.quota
        val fraction = loaded?.let { if (it.limit > 0) it.remaining.toFloat() / it.limit else 0f } ?: 0f
        ProgressTrack(
            fraction,
            track = AsconColors.DividerOnGround,
            fill = solidFill(AsconColors.Ink),
            height = 8.dp
        )
        val meta = when (quota) {
            QuotaState.Loading -> stringResource(R.string.account_detections_loading)
            QuotaState.Failed -> stringResource(R.string.account_detections_failed)
            is QuotaState.Loaded -> null
        }
        if (loaded != null) {
            val left = stringResource(R.string.account_detections_left, loaded.remaining, loaded.limit)
            val resets = stringResource(R.string.account_detections_resets, ResetDate.format(loaded.resetsAt))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = AsconType.ButtonSmall.fontWeight, color = AsconColors.Ink)) {
                        append(left)
                    }
                    append(" · ")
                    append(resets)
                },
                style = AsconType.Meta,
                color = AsconColors.TextMuted
            )
        } else if (meta != null) {
            Text(meta, style = AsconType.Meta, color = AsconColors.TextMuted)
        }
    }
}

/** Asks before signing out, per AccountSignOut. */
@Composable
internal fun SignOutDialog(visible: Boolean, onDismiss: () -> Unit, onSignOut: () -> Unit) {
    ConfirmDialog(
        visible = visible,
        title = stringResource(R.string.account_sign_out_title),
        body = stringResource(R.string.account_sign_out_body),
        cancel = stringResource(R.string.account_sign_out_cancel),
        confirm = stringResource(R.string.account_sign_out),
        onDismiss = onDismiss,
        onConfirm = onSignOut,
        destructive = true
    )
}
