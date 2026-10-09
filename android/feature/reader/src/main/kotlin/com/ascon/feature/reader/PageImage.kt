package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Size
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType

/** Height over width of a page that has not loaded yet, so the list can tell pages apart. */
internal const val PENDING_PAGE_RATIO = 1.4f

/**
 * One page, fetched with [referer] and decoded at the window's width. Until it loads it
 * takes a page-shaped space with its number; if it fails, a tap tries again.
 */
@Composable
internal fun PageImage(index: Int, url: String, referer: String, images: ReaderImages, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val width = LocalWindowInfo.current.containerSize.width
    var attempt by remember(url) { mutableIntStateOf(0) }
    var state by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val loaded = state is AsyncImagePainter.State.Success

    Box(modifier.fillMaxWidth()) {
        key(attempt) {
            val request = remember(url, referer, width) {
                ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(NetworkHeaders.Builder().set("Referer", referer).build())
                    // Fit the width; long strips keep their full height.
                    .size(Size(Dimension(width.coerceAtLeast(1)), Dimension.Undefined))
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = images.loader,
                contentDescription = stringResource(R.string.reader_page, index + 1),
                contentScale = ContentScale.FillWidth,
                onState = { state = it },
                modifier = if (loaded) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().aspectRatio(
                        1 / PENDING_PAGE_RATIO
                    )
                }
            )
        }
        if (!loaded) {
            val failed = state is AsyncImagePainter.State.Error
            PendingPage(
                text = if (failed) {
                    stringResource(R.string.reader_page_failed, index + 1)
                } else {
                    (index + 1).toString()
                },
                modifier = if (failed) Modifier.clickable { attempt++ } else Modifier
            )
        }
    }
}

/** A page-shaped space with a line of text in the middle. */
@Composable
internal fun PendingPage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1 / PENDING_PAGE_RATIO)
            .background(AsconColors.DarkCard),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AsconType.Meta,
            color = AsconColors.OnDarkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
