package id.kitachat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = KitachatGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightText,
    secondary = KitachatGreenDark,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    error = Danger
)

private val DarkColors = darkColorScheme(
    primary = KitachatGreen,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkText,
    secondary = ColorPlaceholder.green,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    outline = DarkBorder,
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
)

private object ColorPlaceholder { val green = KitachatGreen }

@Composable
fun KitachatTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
