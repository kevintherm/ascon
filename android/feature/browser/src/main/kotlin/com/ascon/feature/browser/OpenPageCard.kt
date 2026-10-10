package com.ascon.feature.browser

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.Cover
import com.ascon.core.model.toChapterLabel
import com.ascon.feature.browser.web.displayHost
import java.math.BigDecimal

/** The page the browser kept when the user left it, for Browse's Open page card. */
data class OpenPage(
    val host: String,
    /** The detected series, else the page's own title, else null. */
    val title: String?,
    val chapter: BigDecimal? = null,
    /** The page on screen, when the chapter is read as the site shows it. */
    val page: Int? = null,
    val cover: Cover? = null
)

/** The kept page as Browse shows it, or null when the browser holds no page. */
fun BrowserUiState.openPage(): OpenPage? {
    if (url.isEmpty()) return null
    val card = card?.takeIf { it.url == url }
    return OpenPage(
        host = displayHost(url),
        title = card?.title ?: title,
        chapter = card?.chapter,
        page = card?.page,
        cover = card?.cover
    )
}

private val CardRadius = 28.dp
private val CardPadding = 14.dp

/**
 * Open page, per BrowseIdle: the page Back to Ascon or Keep browsing left loaded, with
 * Return to page, and an X that closes it like the menu's Close.
 */
@Composable
internal fun OpenPageCard(page: OpenPage, onReturn: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(AsconColors.Surface)
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PageArt(page)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(R.string.browse_open_page, page.host).uppercase(),
                    style = AsconType.Eyebrow,
                    color = AsconColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    page.title ?: page.host,
                    style = AsconType.ButtonLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = AsconColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                chapterText(page)?.let { Text(it, style = AsconType.Meta, color = AsconColors.TextMuted) }
            }
            ClosePageButton(onClose, Modifier.align(Alignment.Top))
        }
        // The card's radius is the button's plus the card's padding.
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(CardRadius - CardPadding))
                .background(AsconColors.Accent)
                .clickable(role = Role.Button, onClick = onReturn),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AsconIcons.ReturnTo, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.browse_return_to_page), style = AsconType.Button, color = Color.White)
        }
    }
}

@Composable
private fun chapterText(page: OpenPage): String? {
    val chapter = page.chapter?.toChapterLabel() ?: return null
    return page.page?.let { stringResource(R.string.browse_open_page_chapter_page, chapter, it) }
        ?: stringResource(R.string.browse_open_page_chapter, chapter)
}

@Composable
private fun PageArt(page: OpenPage) {
    val cover = page.cover
    if (cover != null) {
        CoverArt(cover, RoundedCornerShape(8.dp), Modifier.size(44.dp, 62.dp))
    } else {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AsconColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(page.host.take(2).uppercase(), style = AsconType.Badge, color = AsconColors.Ink)
        }
    }
}

@Composable
private fun ClosePageButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.browse_close_page)
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(AsconColors.Ground)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(AsconIcons.Close, contentDescription = null, tint = AsconColors.TextMuted, modifier = Modifier.size(15.dp))
    }
}
