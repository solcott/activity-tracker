package io.github.solcott.activitytracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Material's roles mapped onto Apple's Dynamic Type styles at the default size. Compose renders the system font (SF Pro) here. */
@Composable internal actual fun platformTypography(): Typography = IosTypography

private fun style(size: Int, lineHeight: Int, tracking: Double, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = tracking.sp, fontWeight = weight)

private val LargeTitle = style(34, 41, 0.37, FontWeight.Bold)
private val Title1 = style(28, 34, 0.36, FontWeight.Bold)
private val Title2 = style(22, 28, 0.35, FontWeight.Bold)
private val Title3 = style(20, 25, 0.38, FontWeight.SemiBold)
private val Headline = style(17, 22, -0.43, FontWeight.SemiBold)
private val Body = style(17, 22, -0.43)
private val Callout = style(16, 21, -0.31)
private val Subheadline = style(15, 20, -0.23)
private val Footnote = style(13, 18, -0.08)
private val Caption1 = style(12, 16, 0.0)
private val Caption2 = style(11, 13, 0.06)

private val IosTypography =
    Typography(
        displayLarge = style(48, 56, 0.0, FontWeight.Bold),
        displayMedium = style(40, 48, 0.0, FontWeight.Bold),
        displaySmall = LargeTitle,
        headlineLarge = LargeTitle,
        headlineMedium = Title1,
        headlineSmall = Title2,
        titleLarge = Title3,
        titleMedium = Headline,
        titleSmall = Subheadline.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = Body,
        bodyMedium = Callout,
        bodySmall = Footnote,
        labelLarge = Subheadline.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = Caption1.copy(fontWeight = FontWeight.Medium),
        labelSmall = Caption2,
    )
