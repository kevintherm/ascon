package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.Eyebrow
import com.ascon.core.designsystem.component.GroupedCard
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.RowInset
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import com.ascon.core.model.toChapterLabel
import com.ascon.feature.browser.web.displayHost
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A chapter page the user opened, for Browse's Recently visited. */
data class RecentPage(
    val title: String,
    val chapter: BigDecimal,
    val url: String,
    /** The site's name when it is one of the user's sites, else its domain. */
    val site: String,
    val monogram: String,
    val at: Instant
)

/** How many pages Recently visited lists, per notes.md's Browse idle. */
private const val RECENT_PAGES = 5

/**
 * The last chapter pages opened, newest first, from each chapter's saved address and the
 * time it last changed, so Browse needs no history of its own.
 */
fun recentPages(series: List<Series>, sites: List<Site>, limit: Int = RECENT_PAGES): List<RecentPage> =
    series.flatMap { s ->
        s.chapters.mapNotNull { chapter ->
            val url = chapter.openedUrl ?: return@mapNotNull null
            val at = chapter.updatedAt ?: return@mapNotNull null
            val domain = chapter.openedOnSourceId ?: displayHost(url)
            val site = sites.firstOrNull { it.domain == domain }
            RecentPage(
                s.title,
                chapter.number,
                url,
                site?.name ?: domain,
                site?.monogram ?: domain.take(2).uppercase(),
                at
            )
        }
    }.sortedByDescending { it.at }.take(limit)

private val MonthDay = DateTimeFormatter.ofPattern("MMM d")
private val MonthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
internal fun RecentlyVisited(pages: List<RecentPage>, today: LocalDate, zone: ZoneId, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(stringResource(R.string.browse_recently_visited))
        GroupedCard {
            pages.forEachIndexed { i, page ->
                if (i > 0) RowDivider(start = RowInset + 32.dp + 12.dp)
                RecentRow(page, dayLabel(page.at.atZone(zone).toLocalDate(), today)) { onOpen(page.url) }
            }
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.browse_today)
    today.minusDays(1) -> stringResource(R.string.browse_yesterday)
    else -> day.format(if (day.year == today.year) MonthDay else MonthDayYear)
}

@Composable
private fun RecentRow(page: RecentPage, day: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RowInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AsconColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(page.monogram, style = AsconType.Badge, color = AsconColors.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.browse_recent_title, page.title, page.chapter.toChapterLabel()),
                style = AsconType.RowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.browse_recent_detail, page.site, day),
                style = AsconType.Small,
                color = AsconColors.TextMuted,
                maxLines = 1
            )
        }
    }
}
