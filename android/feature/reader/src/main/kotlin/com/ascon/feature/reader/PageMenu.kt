package com.ascon.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconType

/**
 * The menu from a long press on a page, per notes.md: save the image or share it.
 * Report broken page waits for the backend's rule reports.
 */
@Composable
internal fun PageMenu(page: Int?, onSave: (Int) -> Unit, onShare: (Int) -> Unit, onDismiss: () -> Unit) {
    // Kept while the sheet slides away.
    var shown by remember { mutableIntStateOf(0) }
    if (page != null) shown = page
    BottomSheet(
        visible = page != null,
        onDismiss = onDismiss,
        spacing = 14.dp,
        bottomPadding = 24.dp,
        container = SheetFill,
        grabber = Grabber
    ) {
        Text(
            stringResource(R.string.reader_page, shown + 1),
            style = AsconType.SectionTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        SettingsGroup {
            if (canSavePages) {
                MenuRow(AsconIcons.Download, stringResource(R.string.reader_page_save)) { onSave(shown) }
                SettingsDivider()
            }
            MenuRow(AsconIcons.Share, stringResource(R.string.reader_page_share)) { onShare(shown) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(text, style = AsconType.Button.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}
