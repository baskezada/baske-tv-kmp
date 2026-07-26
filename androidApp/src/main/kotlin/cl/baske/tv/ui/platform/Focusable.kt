package cl.baske.tv.ui.platform

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/**
 * Control clickeable que expone si está "realzado", para que el llamador pinte el
 * anillo/fondo de siempre.
 *
 * En Tv el realce sigue al foco del D-pad. Con puntero no existe el foco, así que
 * lo enganchamos a la presión: el mismo dibujo sirve de feedback táctil y no hay
 * que mantener dos versiones de cada botón.
 */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable (highlighted: Boolean) -> Unit,
) {
    val device = LocalDevice.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = if (device.isTv) focused else pressed

    Box(
        modifier = modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
    ) {
        content(highlighted)
    }
}

/**
 * Pide el foco solo en Tv. Con puntero no hay nada que enfocar y hacerlo dejaría
 * un anillo prendido sin que el usuario haya navegado a ese control.
 */
fun FocusRequester.requestIfTv(device: Device) {
    if (device.isTv) runCatching { requestFocus() }
}
