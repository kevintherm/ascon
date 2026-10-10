package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.PageFit
import com.ascon.core.model.PageGap
import com.ascon.core.model.ReaderBackground
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.ReadingMode

// Sizes and fills from ReaderSettings.
internal val SheetFill = Color(0xFF1B1C21)
internal val Grabber = Color(0x33FFFFFF)
private val Subtitle = Color(0x99FFFFFF)

/** The scope switch nests its 12 radius buttons 4 inside a 16 radius track. */
private val ScopeRadius = 16.dp
private val ScopeInset = 4.dp

private val GrayGround = Color(0xFF3A3C42)

/** The color the pages sit on for each background. */
internal fun ReaderBackground.color(): Color = when (this) {
    ReaderBackground.Black -> Color.Black
    ReaderBackground.Gray -> GrayGround
    ReaderBackground.White -> Color.White
}

/** What the sheet changes. */
internal class ReaderSettingsCommands(
    val onScope: (SettingsScope) -> Unit,
    val onChange: ((ReaderSettings) -> ReaderSettings) -> Unit
)

/**
 * The reader settings sheet, per ReaderSettings, from the Aa button. Changes save for the
 * series or for all series, as the switch at the top says.
 */
@Composable
internal fun ReaderSettingsSheet(
    visible: Boolean,
    state: ReaderUiState,
    commands: ReaderSettingsCommands,
    onDismiss: () -> Unit
) {
    val settings = state.settings
    val change = commands.onChange
    BottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        spacing = 14.dp,
        bottomPadding = 24.dp,
        container = SheetFill,
        grabber = Grabber
    ) {
        Header(state.title)
        if (state.seriesId != null) ScopeSwitch(state.settingsScope, commands.onScope)
        ModeTiles(settings.mode) { mode -> change { it.copy(mode = mode) } }
        SettingsGroup {
            ChoiceRow(
                stringResource(R.string.reader_settings_fit),
                listOf(
                    PageFit.Width to stringResource(R.string.reader_settings_fit_width),
                    PageFit.Screen to stringResource(R.string.reader_settings_fit_screen)
                ),
                settings.fit
            ) { fit -> change { it.copy(fit = fit) } }
            SettingsDivider()
            ChoiceRow(
                stringResource(R.string.reader_settings_gap),
                listOf(
                    PageGap.Auto to stringResource(R.string.reader_settings_gap_auto),
                    PageGap.None to stringResource(R.string.reader_settings_gap_none),
                    PageGap.Small to stringResource(R.string.reader_settings_gap_small)
                ),
                settings.gap
            ) { gap -> change { it.copy(gap = gap) } }
            SettingsDivider()
            BackgroundRow(settings.background) { background -> change { it.copy(background = background) } }
        }
        SettingsGroup {
            SettingsSwitchRow(stringResource(R.string.reader_settings_crop_borders), settings.cropBorders) { on ->
                change { it.copy(cropBorders = on) }
            }
            SettingsDivider()
            SettingsSwitchRow(stringResource(R.string.reader_settings_keep_screen_on), settings.keepScreenOn) { on ->
                change { it.copy(keepScreenOn = on) }
            }
            SettingsDivider()
            SettingsSwitchRow(stringResource(R.string.reader_settings_volume_keys), settings.volumeKeys) { on ->
                change { it.copy(volumeKeys = on) }
            }
            SettingsDivider()
            SettingsSwitchRow(stringResource(R.string.reader_settings_show_tap_zones), settings.showTapZones) { on ->
                change { it.copy(showTapZones = on) }
            }
        }
    }
}

@Composable
private fun Header(title: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.reader_settings_title),
            style = AsconType.SectionTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
        title?.let {
            Text(
                it,
                style = AsconType.Small,
                color = Subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun ScopeSwitch(scope: SettingsScope, onScope: (SettingsScope) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ScopeRadius))
            .background(Track)
            .padding(ScopeInset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Segment(stringResource(R.string.reader_settings_this_series), scope == SettingsScope.Series, 40.dp) {
            onScope(SettingsScope.Series)
        }
        Segment(stringResource(R.string.reader_settings_all_series), scope == SettingsScope.AllSeries, 40.dp) {
            onScope(SettingsScope.AllSeries)
        }
    }
}

@Composable
private fun RowScope.Segment(text: String, selected: Boolean, height: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(height)
            .clip(RoundedCornerShape(ScopeRadius - ScopeInset))
            .background(if (selected) Color.White else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = AsconType.ButtonSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = if (selected) AsconColors.Ink else Unselected
        )
    }
}

/** Long strip, Left to right, Right to left. */
@Composable
private fun ModeTiles(mode: ReadingMode, onMode: (ReadingMode) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeTile(
            AsconIcons.LongStrip,
            stringResource(R.string.reader_settings_long_strip),
            mode == ReadingMode.LongStrip
        ) {
            onMode(ReadingMode.LongStrip)
        }
        ModeTile(
            AsconIcons.LeftToRight,
            stringResource(R.string.reader_settings_left_to_right),
            mode == ReadingMode.LeftToRight
        ) { onMode(ReadingMode.LeftToRight) }
        ModeTile(
            AsconIcons.RightToLeft,
            stringResource(R.string.reader_settings_right_to_left),
            mode == ReadingMode.RightToLeft
        ) { onMode(ReadingMode.RightToLeft) }
    }
}

@Composable
private fun RowScope.ModeTile(icon: ImageVector, text: String, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) AsconColors.Ink else Color.White
    Column(
        Modifier
            .weight(1f)
            .height(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Tile)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(
            text,
            style = AsconType.Small.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold),
            color = content
        )
    }
}
