package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.SwitchRow
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType

// Sizes from BrowserShield and ShieldBroken.
private val BadgeSize = 48.dp
private val BadgeRadius = 14.dp
private val CountRadius = 16.dp
private val GroupRadius = 20.dp
private val ButtonHeight = 48.dp
private val ButtonRadius = 14.dp

/** The confirm card is radius 24 around 12 radius buttons with 12 padding. */
private val ConfirmPadding = 12.dp
private val ConfirmButtonRadius = 12.dp
private val ConfirmRadius = ConfirmButtonRadius + ConfirmPadding

/** Turn off and reload is 1.6 times as wide as Cancel. */
private const val TURN_OFF_WEIGHT = 1.6f

private val CountStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
private val ConfirmTitle = AsconType.SectionTitle.copy(fontSize = 17.sp)

/**
 * The protection sheet, from the shield in the address pill or the menu's Protection
 * row: what protection stopped on this page, the switches, and "Site looks broken?",
 * which asks before it turns protection off for the site.
 */
@Composable
internal fun ProtectionSheet(
    visible: Boolean,
    blocked: BlockedCounts,
    protection: ProtectionUiState,
    commands: BrowserCommands,
    onDismiss: () -> Unit,
    confirmingAtStart: Boolean = false
) {
    // Opening the sheet again starts from the switches.
    var confirming by remember(visible) { mutableStateOf(confirmingAtStart) }
    val site = protection.site
    BottomSheet(visible = visible, onDismiss = onDismiss) {
        Header(protection, site)
        Counts(blocked, accent = !confirming)
        if (confirming) {
            Confirm(
                site = site,
                onCancel = { confirming = false },
                onConfirm = {
                    onDismiss()
                    commands.onTurnOffProtection()
                }
            )
        } else {
            Switches(protection, site, commands)
            if (!protection.trusted) {
                SheetButton(stringResource(R.string.protection_broken)) { confirming = true }
            }
        }
    }
}

@Composable
private fun Header(protection: ProtectionUiState, site: String) {
    val settings = protection.settings
    val on = !protection.trusted && settings.adblockEnabled && settings.blockPopups
    val off = protection.trusted || (!settings.adblockEnabled && !settings.blockPopups)
    val title = when {
        on -> R.string.protection_on
        off -> R.string.protection_off
        else -> R.string.protection_partly
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(BadgeSize)
                .clip(RoundedCornerShape(BadgeRadius))
                .background(if (off) AsconColors.SurfaceMuted else AsconColors.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AsconIcons.Shield,
                contentDescription = null,
                tint = if (off) AsconColors.TextMuted else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = AsconType.SectionTitle, color = AsconColors.Ink)
            Text(
                site,
                style = AsconType.Meta,
                color = AsconColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Ads, trackers and redirects stopped on this page. Stopped redirects take the accent,
 * unless the confirm card is up.
 */
@Composable
private fun Counts(blocked: BlockedCounts, accent: Boolean) {
    // Tiles share the tallest one's height when a label wraps.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Count(blocked.ads, stringResource(R.string.protection_ads))
        Count(blocked.trackers, stringResource(R.string.protection_trackers))
        Count(
            blocked.redirects,
            stringResource(R.string.protection_redirects),
            if (accent && blocked.redirects > 0) AsconColors.Accent else AsconColors.Ink
        )
    }
}

@Composable
private fun RowScope.Count(count: Int, label: String, color: Color = AsconColors.Ink) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(CountRadius))
            .background(AsconColors.Ground)
            .semantics(mergeDescendants = true) {}
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(count.toString(), style = CountStyle, color = color)
        Text(label, style = AsconType.Small, color = AsconColors.TextMuted)
    }
}

@Composable
private fun Switches(protection: ProtectionUiState, site: String, commands: BrowserCommands) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadius))
            .background(AsconColors.Ground)
    ) {
        SwitchRow(
            title = stringResource(R.string.protection_block_ads),
            checked = protection.settings.adblockEnabled,
            onCheckedChange = commands.onSetAdblock
        )
        RowDivider(color = AsconColors.DividerOnGround)
        SwitchRow(
            title = stringResource(R.string.protection_block_popups),
            checked = protection.settings.blockPopups,
            onCheckedChange = commands.onSetBlockPopups
        )
        RowDivider(color = AsconColors.DividerOnGround)
        SwitchRow(
            title = stringResource(R.string.protection_trust),
            subtitle = stringResource(R.string.protection_trust_body, site),
            checked = protection.trusted,
            onCheckedChange = commands.onSetTrusted
        )
    }
}

@Composable
private fun Confirm(site: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ConfirmRadius))
            .background(AsconColors.Ground)
            .padding(ConfirmPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(stringResource(R.string.protection_confirm_title, site), style = ConfirmTitle, color = AsconColors.Ink)
            Text(
                stringResource(R.string.protection_confirm_body),
                style = AsconType.Body.copy(fontSize = 14.sp),
                color = AsconColors.TextMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConfirmButton(stringResource(R.string.protection_cancel), 1f, Color.White, AsconColors.Ink, onCancel)
            ConfirmButton(
                stringResource(R.string.protection_turn_off),
                TURN_OFF_WEIGHT,
                AsconColors.Ink,
                Color.White,
                onConfirm
            )
        }
    }
}

@Composable
private fun RowScope.ConfirmButton(text: String, weight: Float, fill: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(weight)
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ConfirmButtonRadius))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AsconType.Button, color = content)
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(AsconColors.Ground)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AsconType.ButtonSecondary, color = AsconColors.Ink)
    }
}

/** "Protection off for <site>" with Undo, after "Site looks broken?" trusted the site. */
@Composable
internal fun UndoPill(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val shape = CircleShape
    Row(
        modifier
            .clip(shape)
            .background(AsconColors.Glass)
            .border(1.dp, AsconColors.GlassBorder, shape)
            .padding(start = 16.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = AsconType.ButtonSecondary,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            Modifier
                .height(UndoHeight)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onUndo)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.protection_undo), style = AsconType.Button, color = Color.White)
        }
    }
}

private val UndoHeight: Dp = 44.dp
