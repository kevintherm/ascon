package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.PillButton
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.feature.browser.web.LoadErrorKind

/** A page that failed to load, per the SiteError screen. */
@Composable
internal fun LoadErrorPage(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val (title, body) = when (error.kind) {
        LoadErrorKind.NotFound -> R.string.error_not_found_title to R.string.error_not_found_body
        LoadErrorKind.Unreachable -> R.string.error_unreachable_title to R.string.error_unreachable_body
        LoadErrorKind.Insecure -> R.string.error_insecure_title to R.string.error_insecure_body
        LoadErrorKind.Crashed -> R.string.error_crashed_title to R.string.error_crashed_body
        LoadErrorKind.Other -> R.string.error_other_title to R.string.error_other_body
    }
    Column(
        modifier
            .background(AsconColors.Ground)
            // Swallow touches so the page under the error cannot be used.
            .clickable(interactionSource = null, indication = null, onClick = {})
            .padding(start = 24.dp, end = 24.dp, top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .padding(bottom = 10.dp)
                .size(72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AsconColors.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.Offline, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Text(
            stringResource(title, error.host),
            style = AsconType.EmptyTitle.copy(fontSize = 24.sp),
            textAlign = TextAlign.Center
        )
        Text(
            stringResource(body),
            style = AsconType.Body,
            color = AsconColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp)
        )
        PillButton(
            text = stringResource(R.string.error_retry),
            onClick = onRetry,
            colors = PillColors.Primary,
            icon = AsconIcons.Reload,
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
        )
    }
}
