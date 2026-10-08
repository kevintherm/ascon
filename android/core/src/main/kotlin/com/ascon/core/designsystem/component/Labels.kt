package com.ascon.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ascon.core.R
import com.ascon.core.model.ReadingStatus

@get:StringRes
val ReadingStatus.labelRes: Int
    get() = when (this) {
        ReadingStatus.Reading -> R.string.status_reading
        ReadingStatus.Plan -> R.string.status_plan
        ReadingStatus.Paused -> R.string.status_paused
        ReadingStatus.Done -> R.string.status_done
    }

/** "Reading · 12" */
@Composable
fun statusChipLabel(status: ReadingStatus, count: Int): String =
    stringResource(R.string.status_chip, stringResource(status.labelRes), count)

/** "Ch. 12", or "Ch. 112–113" when [last] is set. */
@Composable
fun chapterLabel(first: String, last: String? = null): String = if (last == null || last == first) {
    stringResource(R.string.chapter_short, first)
} else {
    stringResource(R.string.chapter_range, first, last)
}
