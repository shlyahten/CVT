package ru.shlyahten.cvt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AutomotiveColorScheme = darkColorScheme(
    primary = AutoCyan,
    onPrimary = Color.Black,
    primaryContainer = AutoSurfaceCard,
    onPrimaryContainer = AutoCyan,
    secondary = AutoEmerald,
    onSecondary = Color.Black,
    tertiary = AutoAmber,
    onTertiary = Color.Black,
    error = AutoRed,
    onError = Color.White,
    background = AutoBackground,
    onBackground = AutoTextPrimary,
    surface = AutoSurface,
    onSurface = AutoTextPrimary,
    surfaceVariant = AutoSurfaceCard,
    onSurfaceVariant = AutoTextSecondary,
    outline = AutoBorder,
)

@Composable
fun CVTTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AutomotiveColorScheme,
        typography = Typography,
        content = content
    )
}