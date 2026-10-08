package com.ascon.feature.library.shelf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.PillButton
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.graphics.Screentone
import com.ascon.core.designsystem.graphics.ScreentoneFade
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.feature.library.R

/** The empty shelf: a stack of blank covers with the mark, and two ways to start. */
@Composable
internal fun EmptyLibrary(onOpenSite: () -> Unit, onImport: () -> Unit, bottomPadding: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = bottomPadding)
    ) {
        Text(
            stringResource(R.string.library_title),
            style = AsconType.ScreenTitle,
            modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() }
        )
        CoverStack(Modifier.padding(top = 40.dp))
        Column(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.library_empty_title),
                style = AsconType.EmptyTitle,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.library_empty_body),
                style = AsconType.Body,
                color = AsconColors.TextMuted,
                textAlign = TextAlign.Center
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillButton(
                text = stringResource(R.string.library_open_site),
                onClick = onOpenSite,
                colors = PillColors.Ink,
                icon = AsconIcons.Browse,
                modifier = Modifier.fillMaxWidth()
            )
            PillButton(
                text = stringResource(R.string.library_import),
                onClick = onImport,
                colors = PillColors.Surface,
                textStyle = AsconType.ButtonLargeSecondary,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private val BlankCoverShape = RoundedCornerShape(16.dp)
private val FrontCoverShape = RoundedCornerShape(18.dp)
private val FrontCoverGround = Brush.linearGradient(listOf(Color(0xFF2A2C33), Color(0xFF121317)))
private const val TILT = 9f

@Composable
private fun CoverStack(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.TopCenter) {
        BlankCover(Modifier.offset(x = (-68).dp, y = 30.dp).rotate(-TILT))
        BlankCover(Modifier.offset(x = 68.dp, y = 30.dp).rotate(TILT))
        Box(
            Modifier
                .offset(y = 14.dp)
                .size(132.dp, 190.dp)
                .dropShadow(
                    FrontCoverShape,
                    Shadow(radius = 40.dp, color = AsconColors.ShadowFloating, offset = DpOffset(0.dp, 18.dp))
                )
                .clip(FrontCoverShape)
                .background(FrontCoverGround),
            contentAlignment = Alignment.Center
        ) {
            Screentone(
                Color.White.copy(alpha = 0.12f),
                dotRadius = 1.dp,
                pitch = 6.dp,
                fade = ScreentoneFade.out(angleDegrees = 215f, end = 0.65f)
            )
            Image(AsconIcons.MarkWhite, contentDescription = null, modifier = Modifier.size(72.dp))
        }
    }
}

@Composable
private fun BlankCover(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(120.dp, 172.dp)
            .clip(BlankCoverShape)
            .background(AsconColors.SurfaceMuted)
    ) {
        Screentone(AsconColors.Ink.copy(alpha = 0.14f), dotRadius = 1.dp, pitch = 6.dp)
    }
}
