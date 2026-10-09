package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType

/** Close is 1.6 times as wide as Keep browsing, as in the protection sheet's confirm. */
private const val CLOSE_WEIGHT = 1.6f

/**
 * Asks before Back on the first page of history closes the browser, since closing
 * discards the page and its history. Back to Ascon in the menu leaves without asking.
 */
@Composable
internal fun CloseSheet(visible: Boolean, onDismiss: () -> Unit, onClose: () -> Unit) {
    BottomSheet(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.browser_close_title),
                style = AsconType.SectionTitle.copy(fontSize = 17.sp),
                color = AsconColors.Ink
            )
            Text(
                stringResource(R.string.browser_close_body),
                style = AsconType.Body.copy(fontSize = 14.sp),
                color = AsconColors.TextMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetButton(
                stringResource(R.string.browser_close_cancel),
                1f,
                AsconColors.Ground,
                AsconColors.Ink,
                onDismiss
            )
            SheetButton(
                stringResource(R.string.browser_close_confirm),
                CLOSE_WEIGHT,
                AsconColors.Ink,
                Color.White,
                onClose
            )
        }
    }
}

@Composable
private fun RowScope.SheetButton(text: String, weight: Float, fill: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(weight)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AsconType.Button, color = content)
    }
}
