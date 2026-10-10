package com.ascon.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val SnackbarRadius = 18.dp
private val ActionInset = 8.dp

/** The action's fill: white at 12 percent over ink, per notes.md's snackbar spec. */
private val ActionFill = Color.White.copy(alpha = 0.12f)

/** How far a swipe must travel before letting go closes the snackbar. The bar is short, so down needs less. */
private val SwipeSideways = 64.dp
private val SwipeDown = 24.dp

/** How faded the bar is while dragged, per px moved. */
private const val FADE_PER_PX = 1f / 360f

/**
 * Where a shown snackbar sits, so a tap anywhere else can close it. The screen holding
 * the snackbar passes one to [Snackbar] and to [Modifier.closesSnackbarOnTapOutside].
 */
class SnackbarArea {
    internal var bounds by mutableStateOf<Rect?>(null)
}

/**
 * Closes the snackbar in [area] when a touch lands anywhere outside it. The touch still
 * reaches whatever it lands on, so tapping a button both closes the snackbar and presses it.
 */
fun Modifier.closesSnackbarOnTapOutside(area: SnackbarArea, onDismiss: () -> Unit): Modifier {
    var origin = Offset.Zero
    return onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(area, onDismiss) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val bounds = area.bounds
                if (bounds != null && !bounds.contains(origin + down.position)) {
                    area.bounds = null
                    onDismiss()
                }
            }
        }
}

/**
 * An ink bar with a message and one action, such as "Browser closed · Undo", or a message
 * alone. With [onDismiss] it closes early on a sideways or downward swipe, and on a tap
 * outside it when [area] is also given to [Modifier.closesSnackbarOnTapOutside].
 */
@Composable
fun Snackbar(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    area: SnackbarArea? = null
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val shape = RoundedCornerShape(SnackbarRadius)
    if (area != null) DisposableEffect(area) { onDispose { area.bounds = null } }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onDismiss == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics {
                            dismiss {
                                dismiss?.invoke()
                                true
                            }
                        }
                        .pointerInput(Unit) {
                            // Outside the offset, so the bar following the finger doesn't hide the drag.
                            var total = Offset.Zero
                            detectDragGestures(
                                onDragStart = { total = Offset.Zero },
                                onDragEnd = {
                                    if (abs(total.x) > SwipeSideways.toPx() ||
                                        total.y > SwipeDown.toPx()
                                    ) {
                                        dismiss?.invoke()
                                    }
                                    scope.launch { drag.animateTo(Offset.Zero) }
                                },
                                onDragCancel = { scope.launch { drag.animateTo(Offset.Zero) } }
                            ) { change, amount ->
                                change.consume()
                                total += amount
                                scope.launch { drag.snapTo(total) }
                            }
                        }
                }
            )
            .offset { IntOffset(drag.value.x.roundToInt(), drag.value.y.coerceAtLeast(0f).roundToInt()) }
            .alpha((1f - (abs(drag.value.x) + drag.value.y.coerceAtLeast(0f)) * FADE_PER_PX).coerceIn(0f, 1f))
            .onGloballyPositioned { if (area != null) area.bounds = it.boundsInWindow() }
            .height(52.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(AsconColors.Ink)
            .padding(start = 16.dp, end = if (action != null) ActionInset else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = AsconType.ButtonSecondary,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            // The action's radius is the bar's minus the inset around it.
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(SnackbarRadius - ActionInset))
                    .background(ActionFill)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    action,
                    style = AsconType.ButtonSecondary.copy(fontWeight = AsconType.Button.fontWeight),
                    color = Color.White
                )
            }
        }
    }
}
