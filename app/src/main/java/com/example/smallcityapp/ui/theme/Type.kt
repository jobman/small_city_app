package com.example.smallcityapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.smallcityapp.R

val MontserratAlternates = FontFamily(
    Font(R.font.montserrat_alternates_regular, FontWeight.Normal),
    Font(R.font.montserrat_alternates_medium, FontWeight.Medium),
    Font(R.font.montserrat_alternates_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_alternates_bold, FontWeight.Bold),
)

private val DefaultTypography = Typography()

private fun TextStyle.brandFont(): TextStyle =
    copy(fontFamily = MontserratAlternates, letterSpacing = 0.sp)

val Typography = Typography(
    displayLarge = DefaultTypography.displayLarge.brandFont(),
    displayMedium = DefaultTypography.displayMedium.brandFont(),
    displaySmall = DefaultTypography.displaySmall.brandFont(),
    headlineLarge = DefaultTypography.headlineLarge.brandFont(),
    headlineMedium = DefaultTypography.headlineMedium.brandFont(),
    headlineSmall = DefaultTypography.headlineSmall.brandFont(),
    titleLarge = DefaultTypography.titleLarge.brandFont(),
    titleMedium = DefaultTypography.titleMedium.brandFont(),
    titleSmall = DefaultTypography.titleSmall.brandFont(),
    bodyLarge = DefaultTypography.bodyLarge.brandFont(),
    bodyMedium = DefaultTypography.bodyMedium.brandFont(),
    bodySmall = DefaultTypography.bodySmall.brandFont(),
    labelLarge = DefaultTypography.labelLarge.brandFont(),
    labelMedium = DefaultTypography.labelMedium.brandFont(),
    labelSmall = DefaultTypography.labelSmall.brandFont(),
)
