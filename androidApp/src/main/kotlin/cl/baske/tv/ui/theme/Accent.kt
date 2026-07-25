package cl.baske.tv.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Color de acento activo (de PrefsStore). Se provee en RootApp. */
val LocalAccent = staticCompositionLocalOf { Color(0xFF06B6D4) }

/** Paleta de acentos ofrecida en Ajustes (ARGB). */
val ACCENT_PRESETS: List<Long> = listOf(
    0xFF06B6D4, // cian
    0xFF7C5CFF, // violeta
    0xFFEF4444, // rojo
    0xFFF59E0B, // ámbar
    0xFF22C55E, // verde
    0xFFEC4899, // rosa
    0xFF3B82F6, // azul
    0xFFFFFFFF, // blanco
)
