package com.ascon.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius

/** One destination in the [FloatingNavBar]. */
data class NavItem(val icon: ImageVector, val label: String)

private val BarHeight = 68.dp
private val BarRadius = 28.dp
private val BarPadding = 10.dp
private val ItemWidth = 64.dp

/** The item is 48 tall in a 68 bar, so the gap to the bar edge is 10 on every side. */
private val ItemRadius = AsconRadius.nested(BarRadius, BarPadding)

/** Space the bar takes at the bottom of the screen, above the system navigation bar. */
val FloatingNavBarClearance = BarHeight + 20.dp

/**
 * The floating ink pill nav: 68 tall, radius 28, padding 10, four 64×48 items with
 * radius 18. The active item is a white fill with an ink icon; the rest are white at 70%.
 * Place it 16 from the sides and 20 from the bottom.
 */
@Composable
fun FloatingNavBar(items: List<NavItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BarRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .dropShadow(
                shape,
                Shadow(radius = 30.dp, color = AsconColors.ShadowFloating, offset = DpOffset(0.dp, 12.dp))
            )
            .clip(shape)
            .background(AsconColors.Ink)
            .padding(BarPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "navFill")
            val tint by animateColorAsState(
                if (selected) AsconColors.Ink else Color.White.copy(alpha = 0.7f),
                label = "navTint"
            )
            Box(
                modifier = Modifier
                    .size(ItemWidth, BarHeight - BarPadding * 2)
                    .clip(RoundedCornerShape(ItemRadius))
                    .background(fill)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}
