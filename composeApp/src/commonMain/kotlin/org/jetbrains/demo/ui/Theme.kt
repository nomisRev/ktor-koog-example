package org.jetbrains.demo.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Color palette based on provided SCSS values
private val FederalBlue = Color(0xFF03045E)
private val MarianBlue = Color(0xFF023E8A)
private val HonoluluBlue = Color(0xFF0077B6)
private val BlueGreen = Color(0xFF0096C7)
private val PacificCyan = Color(0xFF00B4D8)
private val VividSkyBlue = Color(0xFF48CAE4)
private val NonPhotoBlue = Color(0xFF90E0EF)
private val NonPhotoBlue2 = Color(0xFFADE8F4)
private val LightCyan = Color(0xFFCAF0F8)

// Common colors
private val White = Color.White
private val DarkBackground = Color(0xFF0F1419)

// Error colors
private val ErrorLight = Color(0xFFBA1A1A)
private val ErrorDark = Color(0xFFFFB4AB)
private val ErrorContainer = Color(0xFFFFDAD6)
private val ErrorContainerLight = Color(0xFF410002)
private val ErrorContainerDark = Color(0xFF93000A)
private val OnErrorDark = Color(0xFF690005)

private val LightColors: ColorScheme = lightColorScheme(
    primary = HonoluluBlue,
    onPrimary = White,
    primaryContainer = LightCyan,
    onPrimaryContainer = FederalBlue,
    secondary = PacificCyan,
    onSecondary = White,
    secondaryContainer = NonPhotoBlue2,
    onSecondaryContainer = MarianBlue,
    tertiary = VividSkyBlue,
    onTertiary = White,
    tertiaryContainer = NonPhotoBlue,
    onTertiaryContainer = FederalBlue,
    background = White,
    onBackground = FederalBlue,
    surface = White,
    onSurface = FederalBlue,
    surfaceVariant = LightCyan,
    onSurfaceVariant = MarianBlue,
    outline = BlueGreen,
    outlineVariant = NonPhotoBlue,
    error = ErrorLight,
    onError = White,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorContainerLight
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = VividSkyBlue,
    onPrimary = FederalBlue,
    primaryContainer = MarianBlue,
    onPrimaryContainer = LightCyan,
    secondary = NonPhotoBlue,
    onSecondary = FederalBlue,
    secondaryContainer = BlueGreen,
    onSecondaryContainer = NonPhotoBlue2,
    tertiary = PacificCyan,
    onTertiary = FederalBlue,
    tertiaryContainer = HonoluluBlue,
    onTertiaryContainer = NonPhotoBlue,
    background = DarkBackground,
    onBackground = LightCyan,
    surface = DarkBackground,
    onSurface = LightCyan,
    surfaceVariant = MarianBlue,
    onSurfaceVariant = NonPhotoBlue,
    outline = BlueGreen,
    outlineVariant = HonoluluBlue,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainer
)

@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
