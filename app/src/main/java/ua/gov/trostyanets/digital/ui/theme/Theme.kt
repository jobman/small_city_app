package ua.gov.trostyanets.digital.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SportGreen,
    onPrimary = Color.White,
    primaryContainer = EffervescentBlue,
    onPrimaryContainer = Color.White,
    secondary = HolidayBlue,
    secondaryContainer = EffervescentBlue.copy(alpha = 0.35f),
    onSecondaryContainer = Color.White,
    tertiary = FullYellow,
    background = BrandDarkBackground,
    onBackground = Color(0xFFE9F1F7),
    surface = BrandDarkSurface,
    onSurface = Color(0xFFE9F1F7),
    error = CoralOrange,
)

private val LightColorScheme = lightColorScheme(
    primary = EffervescentBlue,
    secondary = SportGreen,
    tertiary = HolidayBlue,
    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = EffervescentBlue,
    secondaryContainer = Color(0xFFD7F5EC),
    onSecondaryContainer = EffervescentBlue,
    tertiaryContainer = Color(0xFFD6F6FA),
    background = BrandLightBackground,
    surface = BrandLightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = EffervescentBlue,
    onBackground = EffervescentBlue,
    onSurface = Color(0xFF1C2A3E),
    error = CoralOrange,
)

@Composable
fun SmallCityAPPTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
