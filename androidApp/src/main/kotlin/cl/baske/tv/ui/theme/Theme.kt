package cl.baske.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BaskeColors = darkColorScheme(
    primary = Color(0xFF7C5CFF),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB8B8B8),
)

@Composable
fun BaskeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BaskeColors, content = content)
}
