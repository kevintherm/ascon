package com.ascon.core.designsystem.icon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors

/**
 * Icons on a 24 grid with a 2 stroke and round caps and joins, from design/tokens.md.
 * Path data is copied from the approved screens. Only play and the more-dots are filled.
 * Tint them with the icon's content color; the black here is replaced at draw time.
 */
object AsconIcons {
    val Home = stroked("Home", "M4 11 12 4l8 7v9H4z", "M10 20v-5h4v5")
    val Library = stroked("Library", rect(4f, 4f, 6f, 16f, 1.5f), rect(14f, 4f, 6f, 16f, 1.5f))
    val Browse = stroked("Browse", circle(12f, 12f, 8f), "m15 9-2 4-4 2 2-4z")
    val Settings = stroked(
        "Settings",
        "M4 7h10M18 7h2M4 17h4M12 17h8",
        circle(16f, 7f, 2f),
        circle(10f, 17f, 2f)
    )
    val Search = stroked("Search", circle(11f, 11f, 7f), "m20 20-3.5-3.5")
    val Back = stroked("Back", "M15 5l-7 7 7 7")
    val ChevronRight = stroked("ChevronRight", "M9 6l6 6-6 6")
    val ChevronDown = stroked("ChevronDown", "M6 9l6 6 6-6")
    val Download = stroked("Download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Bell = stroked("Bell", "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    val Check = stroked("Check", "M5 12l4 4 10-10")
    val Plus = stroked("Plus", "M12 5v14M5 12h14")
    val Sort = stroked("Sort", "M7 4v16M4 17l3 3 3-3M17 20V4M14 7l3-3 3 3")
    val Person = stroked("Person", circle(12f, 8f, 4f), "M5 20a7 7 0 0 1 14 0")
    val Forward = stroked("Forward", "M9 5l7 7-7 7")
    val PreviousChapter = stroked("PreviousChapter", "M17 6l-6 6 6 6M7 6v12")
    val NextChapter = stroked("NextChapter", "M7 6l6 6-6 6M17 6v12")
    val Reader = stroked("Reader", "M4 5h7v14H4zM13 5h7v14h-7z")
    val External = stroked("External", "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6")
    val ReaderOff = stroked("ReaderOff", "M4 5h7v14H4zM13 5h7v14h-7z", "M3 3l18 18")
    val Close = stroked("Close", "M6 6l12 12M18 6 6 18")
    val Reread = stroked("Reread", "M4 12a8 8 0 1 0 2.3-5.6", "M4 4v5h5")
    val Reload = stroked("Reload", "M20 12a8 8 0 1 1-2.34-5.66", "M20 4v4.5h-4.5")
    val Share = stroked("Share", "M12 4v11M7.5 8.5 12 4l4.5 4.5", "M6 13v6h12v-6")
    val Offline = stroked(
        "Offline",
        "M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 10.5-2M8.5 15.5a5 5 0 0 1 5-1",
        circle(12f, 19f, 1f),
        "M3 3l18 18"
    )
    val Shield = stroked("Shield", "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z")
    val Chapters = stroked("Chapters", "M4 6h16M4 12h16M4 18h10")
    val ArrowRight = stroked("ArrowRight", "M5 12h14M13 6l6 6-6 6")
    val LongStrip = stroked("LongStrip", rect(7f, 2f, 10f, 20f, 2f), "M7 9h10M7 15h10")
    val LeftToRight = stroked("LeftToRight", rect(3f, 4f, 8f, 16f, 1.5f), "M15 12h6M18 9l3 3-3 3")
    val RightToLeft = stroked("RightToLeft", rect(13f, 4f, 8f, 16f, 1.5f), "M9 12H3M6 9l-3 3 3 3")
    val Play = filled("Play", "M8 5.5v13l11-6.5z")
    val More = filled("More", circle(5f, 12f, 1.8f), circle(12f, 12f, 1.8f), circle(19f, 12f, 1.8f))

    /** The Progress A logo in ink. Not tinted: draw it with Image, not Icon. */
    val Mark = mark(ink = AsconColors.Ink, trackAlpha = 0.2f)

    /** The Progress A logo for dark grounds. */
    val MarkWhite = mark(ink = Color.White, trackAlpha = 0.28f)
}

private const val GRID = 24f
private const val STROKE = 2f

private fun builder(name: String, viewport: Float = GRID) = ImageVector.Builder(
    name = name,
    defaultWidth = viewport.dp,
    defaultHeight = viewport.dp,
    viewportWidth = viewport,
    viewportHeight = viewport
)

private fun stroked(name: String, vararg paths: String): ImageVector = builder(name).apply {
    paths.forEach { data ->
        addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}.build()

private fun filled(name: String, vararg paths: String): ImageVector = builder(name).apply {
    paths.forEach { data -> addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black)) }
}.build()

private const val MARK_VIEWPORT = 100f
private const val MARK_STROKE = 14f
private const val CROSSBAR_STROKE = 10f

// The crossbar gradient spans these x positions in the mark's 100 grid, as in the SVG.
private const val CROSSBAR_GRADIENT_START = 34f
private const val CROSSBAR_GRADIENT_END = 56f

private fun mark(ink: Color, trackAlpha: Float): ImageVector = builder("Mark", MARK_VIEWPORT).apply {
    addPath(
        pathData = addPathNodes("M24 84 L50 18 L76 84"),
        stroke = SolidColor(ink),
        strokeLineWidth = MARK_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round
    )
    addPath(
        pathData = addPathNodes("M39 63 H61"),
        stroke = SolidColor(ink),
        strokeAlpha = trackAlpha,
        strokeLineWidth = CROSSBAR_STROKE,
        strokeLineCap = StrokeCap.Round
    )
    addPath(
        pathData = addPathNodes("M39 63 H51"),
        stroke = Brush.linearGradient(
            listOf(AsconColors.Accent2, AsconColors.Accent),
            start = Offset(CROSSBAR_GRADIENT_START, 0f),
            end = Offset(CROSSBAR_GRADIENT_END, 0f)
        ),
        strokeLineWidth = CROSSBAR_STROKE,
        strokeLineCap = StrokeCap.Round
    )
}.build()

/** SVG `<circle>` as path data. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${r + r} 0a$r $r 0 1 0 ${-(r + r)} 0z"

/** SVG `<rect>` with rounded corners as path data. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} $r" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"
