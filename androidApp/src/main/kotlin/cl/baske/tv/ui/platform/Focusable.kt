package cl.baske.tv.ui.platform

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Control clickeable que expone si está "realzado", para que el llamador pinte el
 * anillo/fondo de siempre.
 *
 * En Tv el realce sigue al foco del D-pad. Con puntero no existe el foco, así que
 * lo enganchamos a la presión: el mismo dibujo sirve de feedback táctil y no hay
 * que mantener dos versiones de cada botón.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (highlighted: Boolean) -> Unit,
) {
    val device = LocalDevice.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = if (device.isTv) focused else pressed

    val clickMod = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    }

    Box(modifier = modifier.then(clickMod)) {
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

/**
 * Comportamiento de foco para una fila horizontal navegada con D-pad: al ENTRAR
 * (subiendo o bajando desde otra fila) el foco cae SIEMPRE en el PRIMER elemento,
 * no en el que quede geométricamente alineado (que daba "el 3ro", o "Aleatorio" al
 * volver arriba). `firstFocus` debe estar puesto (`Modifier.focusRequester`) en el
 * primer hijo de la fila.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.rowFocus(firstFocus: FocusRequester): Modifier =
    this.focusProperties { enter = { firstFocus } }.focusGroup()
