package app.recall.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AMOLED palette: pure black backgrounds keep OLED pixels off, and a soft mint
// primary keeps the app friendly. Text contrast is kept above WCAG AA on black.
private val RecallColors = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0B3D2E),
    onPrimaryContainer = Color(0xFFB7F5DC),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF1F1D3D),
    onSecondaryContainer = Color(0xFFDCDCFF),
    tertiary = Color(0xFFFDBA74),
    onTertiary = Color(0xFF3B2000),
    background = Color.Black,
    onBackground = Color(0xFFEDEDED),
    surface = Color.Black,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFB8B8B8),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF410002),
)

@Composable
fun RecallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RecallColors,
        content = content,
    )
}
