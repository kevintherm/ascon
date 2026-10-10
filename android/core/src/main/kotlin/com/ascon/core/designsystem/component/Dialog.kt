package com.ascon.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType

/** The dialog's buttons sit 20 from its bottom corners, so their radius is its 28 minus 20. */
private const val PADDING = 20
private val DialogShape = RoundedCornerShape(AsconRadius.Sheet)
private val ButtonShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.Sheet, PADDING.dp))
private val DialogScrim = Color(0x9E0E0F12)

/**
 * A centered question with Cancel and a confirm button, as in AccountSignOut. Back or a
 * tap outside cancels. A [destructive] confirm is drawn in the danger color.
 */
@Composable
fun ConfirmDialog(
    visible: Boolean,
    title: String,
    body: String,
    cancel: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(DialogScrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, DialogShape)
                    .clip(DialogShape)
                    .background(AsconColors.Surface)
                    // Taps inside the dialog don't reach the scrim.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = title }
                    .padding(start = PADDING.dp, end = PADDING.dp, top = 24.dp, bottom = PADDING.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, style = AsconType.SectionTitle.copy(fontSize = 20.sp), color = AsconColors.Ink)
                Text(body, style = AsconType.Body.copy(fontSize = 14.sp), color = AsconColors.TextMuted)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogButton(cancel, AsconColors.Ground, AsconColors.Ink, onDismiss)
                    DialogButton(
                        confirm,
                        if (destructive) AsconColors.Danger else AsconColors.Ink,
                        Color.White,
                        onConfirm
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DialogButton(text: String, fill: Color, content: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clip(ButtonShape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = AsconType.Button, color = content)
    }
}
