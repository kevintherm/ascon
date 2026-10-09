package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// Sizes from BrowserV2Menu.
private val TileHeight = 64.dp
private val TileRadius = 16.dp
private val GroupRadius = 20.dp
private val RowHeight = 56.dp
private val RowIcon = 32.dp
private val RowIconRadius = 10.dp
private val CloseRadius = 16.dp
private val CloseIconFill = Color(0x1FFFFFFF)

/**
 * The browser menu, a sheet over the page: the site, page actions, the chapter's
 * actions when one is detected, and Back to Ascon at the bottom, which leaves the
 * browser and keeps the page loaded.
 */
@Composable
internal fun BrowserMenu(
    visible: Boolean,
    state: BrowserUiState,
    commands: BrowserCommands,
    onDismiss: () -> Unit,
    onProtection: () -> Unit = {}
) {
    fun run(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    val chapter = state.readerChapter?.chapter ?: state.card?.chapter
    val title = state.card?.title ?: state.readerChapter?.title
    val seriesId = state.card?.seriesId ?: state.readerChapter?.seriesId
    BottomSheet(visible = visible, onDismiss = onDismiss, spacing = 14.dp, bottomPadding = 24.dp) {
        SiteHeader(
            state.host,
            chapter?.let { c ->
                title?.let { stringResource(R.string.browser_menu_chapter_of, c.toChapterLabel(), it) }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(
                AsconIcons.Forward,
                stringResource(R.string.browser_forward),
                state.canGoForward,
                run(commands.onForward)
            )
            Tile(AsconIcons.Share, stringResource(R.string.browser_share_short), true, run(commands.onShare))
        }
        val reader = state.readerChapter
        if (reader != null || seriesId != null) {
            Group {
                if (reader != null) {
                    MenuRow(
                        icon = AsconIcons.Reader,
                        text = stringResource(R.string.browser_menu_open_reader),
                        onClick = run(commands.onOpenReader),
                        accent = true,
                        trailing = reader.chapter?.let {
                            stringResource(R.string.browser_menu_chapter_short, it.toChapterLabel())
                        },
                        divider = seriesId != null
                    )
                }
                if (seriesId != null) {
                    MenuRow(
                        icon = AsconIcons.Library,
                        text = stringResource(R.string.browser_menu_series),
                        onClick = run { commands.onOpenSeries(seriesId) },
                        chevron = true
                    )
                }
            }
        }
        Group {
            MenuRow(
                icon = AsconIcons.Shield,
                text = stringResource(R.string.browser_menu_protection),
                onClick = onProtection,
                trailing = state.blocked.total.takeIf { it > 0 }?.toString(),
                chevron = true,
                divider = true
            )
            MenuRow(
                AsconIcons.External,
                stringResource(R.string.browser_menu_other_browser),
                run(commands.onOpenElsewhere)
            )
        }
        CloseRow(run(commands.onCloseBrowser))
    }
}

@Composable
private fun SiteHeader(host: String, subtitle: String?) {
    Row(
        Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AsconColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(
                monogramOf(host),
                style = AsconType.ButtonSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = AsconColors.Ink
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                host,
                style = AsconType.RowTitle,
                color = AsconColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    it,
                    style = AsconType.Small,
                    color = AsconColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Two letters for a site without its own monogram: `mangafire.to` gives MF. */
internal fun monogramOf(host: String): String {
    val name = host.substringBefore('.')
    val parts = name.split('-', '_').filter { it.isNotEmpty() }
    val letters = if (parts.size > 1) parts.take(2).map { it.first() } else name.take(2).toList()
    return letters.joinToString("").uppercase()
}

@Composable
private fun RowScope.Tile(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) AsconColors.Ink else AsconColors.TextSubtle
    Column(
        Modifier
            .weight(1f)
            .height(TileHeight)
            .clip(RoundedCornerShape(TileRadius))
            .background(AsconColors.Ground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = AsconType.CaptionStrong, color = color)
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupRadius))
            .background(AsconColors.Ground),
        content = content
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    trailing: String? = null,
    chevron: Boolean = false,
    divider: Boolean = false
) {
    Column(Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowHeight)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(RowIcon)
                    .clip(RoundedCornerShape(RowIconRadius))
                    .background(if (accent) AsconColors.Accent else AsconColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (accent) Color.White else AsconColors.Ink,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text,
                style = if (accent) AsconType.RowTitle else AsconType.RowTitleRead,
                color = AsconColors.Ink,
                modifier = Modifier.weight(1f)
            )
            trailing?.let { Text(it, style = AsconType.Meta, color = AsconColors.TextMuted) }
            if (chevron) {
                Icon(
                    AsconIcons.ChevronRight,
                    contentDescription = null,
                    tint = AsconColors.TextSubtle,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (divider) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AsconColors.DividerOnGround)
            )
        }
    }
}

@Composable
private fun CloseRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .clip(RoundedCornerShape(CloseRadius))
            .background(AsconColors.Ink)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(RowIcon)
                .clip(RoundedCornerShape(RowIconRadius))
                .background(CloseIconFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(stringResource(R.string.browser_menu_close), style = AsconType.RowTitle, color = Color.White)
    }
}
