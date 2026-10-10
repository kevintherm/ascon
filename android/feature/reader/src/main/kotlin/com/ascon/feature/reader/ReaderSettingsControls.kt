package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.ReaderBackground

// Fills from ReaderSettings, for the rows inside the sheet's groups.
internal val Track = Color(0x14FFFFFF)
internal val Tile = Color(0x0FFFFFFF)
internal val Unselected = Color(0xBFFFFFFF)
private val Divider = Color(0x14FFFFFF)
private val SwitchOff = Color(0x33FFFFFF)
private val SwatchBorder = Color(0x26FFFFFF)

/** The row switches nest their 9 radius buttons 3 inside a 12 radius track. */
private val ChoiceRadius = 12.dp
private val ChoiceInset = 3.dp

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Tile)
            .padding(horizontal = 16.dp),
        content = content
    )
}

@Composable
internal fun SettingsDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

@Composable
private fun SettingRow(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 58.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = RowTitle, color = Color.White, modifier = Modifier.weight(1f))
        trailing()
    }
}

private val RowTitle = AsconType.Button.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun <T> ChoiceRow(title: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SettingRow(title) {
        Row(
            Modifier
                .clip(RoundedCornerShape(ChoiceRadius))
                .background(Track)
                .padding(ChoiceInset)
                .selectableGroup()
                .semantics { contentDescription = title },
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            choices.forEach { (value, label) ->
                val on = value == selected
                Box(
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(ChoiceRadius - ChoiceInset))
                        .background(if (on) Color.White else Color.Transparent)
                        .selectable(on, role = Role.RadioButton) { onSelect(value) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = AsconType.Meta.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
                        color = if (on) AsconColors.Ink else Unselected
                    )
                }
            }
        }
    }
}

@Composable
internal fun BackgroundRow(selected: ReaderBackground, onSelect: (ReaderBackground) -> Unit) {
    SettingRow(stringResource(R.string.reader_settings_background)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderBackground.entries.forEach { background ->
                val on = background == selected
                val label = stringResource(
                    when (background) {
                        ReaderBackground.Black -> R.string.reader_settings_black
                        ReaderBackground.Gray -> R.string.reader_settings_gray
                        ReaderBackground.White -> R.string.reader_settings_white
                    }
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(2.dp, if (on) Color.White else SwatchBorder, CircleShape)
                        // The chosen swatch keeps a ring of the sheet between it and its border.
                        .padding(if (on) 5.dp else 2.dp)
                        .clip(CircleShape)
                        .background(background.color())
                        .selectable(on, role = Role.RadioButton) { onSelect(background) }
                        .semantics { contentDescription = label }
                )
            }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title,
        Modifier.heightIn(min = 54.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange)
    ) {
        Box(
            Modifier
                .size(52.dp, 32.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else SwitchOff)
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .offset(x = if (checked) 20.dp else 0.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) AsconColors.Ink else Color.White)
            )
        }
    }
}
