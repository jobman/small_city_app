package ua.gov.trostyanets.digital.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ua.gov.trostyanets.digital.R

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
    displayLarge = DefaultTypography.displayLarge.brandFont().copy(fontSize = 60.sp, lineHeight = 68.sp),
    displayMedium = DefaultTypography.displayMedium.brandFont().copy(fontSize = 48.sp, lineHeight = 56.sp),
    displaySmall = DefaultTypography.displaySmall.brandFont().copy(fontSize = 40.sp, lineHeight = 48.sp),
    headlineLarge = DefaultTypography.headlineLarge.brandFont().copy(fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = DefaultTypography.headlineMedium.brandFont().copy(fontSize = 30.sp, lineHeight = 38.sp),
    headlineSmall = DefaultTypography.headlineSmall.brandFont().copy(fontSize = 26.sp, lineHeight = 34.sp),
    titleLarge = DefaultTypography.titleLarge.brandFont().copy(fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = DefaultTypography.titleMedium.brandFont().copy(fontSize = 21.sp, lineHeight = 29.sp),
    titleSmall = DefaultTypography.titleSmall.brandFont().copy(fontSize = 19.sp, lineHeight = 27.sp),
    bodyLarge = DefaultTypography.bodyLarge.brandFont().copy(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = DefaultTypography.bodyMedium.brandFont().copy(fontSize = 18.sp, lineHeight = 27.sp),
    bodySmall = DefaultTypography.bodySmall.brandFont().copy(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = DefaultTypography.labelLarge.brandFont().copy(fontSize = 17.sp, lineHeight = 24.sp),
    labelMedium = DefaultTypography.labelMedium.brandFont().copy(fontSize = 15.sp, lineHeight = 22.sp),
    labelSmall = DefaultTypography.labelSmall.brandFont().copy(fontSize = 13.sp, lineHeight = 18.sp),
)
