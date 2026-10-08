package com.ascon.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ascon.core.R

private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Plus Jakarta Sans, one variable font file covering weights 400 to 800. */
val PlusJakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
    jakarta(FontWeight.ExtraBold)
)

private fun style(size: Int, weight: FontWeight, lineHeight: Double? = null, tracking: Double = 0.0) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = lineHeight?.em ?: TextStyle.Default.lineHeight,
    letterSpacing = tracking.em
)

/** Type roles from design/tokens.md. */
object AsconType {
    val Display = style(38, FontWeight.ExtraBold, lineHeight = 1.05, tracking = -0.035)
    val ScreenTitle = style(30, FontWeight.ExtraBold, lineHeight = 1.1, tracking = -0.03)
    val HeaderTitle = style(25, FontWeight.ExtraBold, lineHeight = 1.15, tracking = -0.01)
    val HeaderTitleSmall = style(23, FontWeight.ExtraBold, lineHeight = 1.15, tracking = -0.01)
    val EmptyTitle = style(22, FontWeight.ExtraBold, lineHeight = 1.2, tracking = -0.02)
    val SectionTitle = style(18, FontWeight.ExtraBold, tracking = -0.01)
    val RowTitle = style(15, FontWeight.Bold)
    val RowTitleRead = style(15, FontWeight.SemiBold)
    val ButtonLarge = style(16, FontWeight.Bold)
    val ButtonLargeSecondary = style(16, FontWeight.SemiBold)
    val Button = style(15, FontWeight.Bold)
    val ButtonSecondary = style(14, FontWeight.SemiBold)
    val CardTitle = style(14, FontWeight.Bold)
    val ButtonSmall = style(13, FontWeight.Bold)
    val Body = style(15, FontWeight.Normal, lineHeight = 1.5)
    val Meta = style(13, FontWeight.Normal)
    val MetaStrong = style(13, FontWeight.SemiBold)
    val GridTitle = style(13, FontWeight.Bold, lineHeight = 1.25)
    val Value = style(14, FontWeight.Normal)
    val Small = style(12, FontWeight.Normal)
    val Caption = style(12, FontWeight.Medium)
    val CaptionStrong = style(12, FontWeight.SemiBold)
    val Badge = style(12, FontWeight.ExtraBold)
    val Eyebrow = style(12, FontWeight.Bold, tracking = 0.12)
    val EyebrowSection = style(13, FontWeight.Bold, tracking = 0.1)
}
