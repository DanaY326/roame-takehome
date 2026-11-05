package travel.roame.takehome.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF4A40DB)
val Warning = Color(0xFFF59E0B)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E1FF),
    onPrimaryContainer = Color(0xFF15106B),
    secondaryContainer = Color(0xFFE4E1FF),
    onSecondaryContainer = Color(0xFF15106B),
    background = Color(0xFFF6F6FA),
    surface = Color(0xFFF6F6FA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEFEFF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8B2FF),
    onPrimary = Color(0xFF1E1785),
    primaryContainer = Color(0xFF332AAE),
    onPrimaryContainer = Color(0xFFE4E1FF),
    secondaryContainer = Color(0xFF332AAE),
    onSecondaryContainer = Color(0xFFE4E1FF),
)

@Composable
fun FlightExplorerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
