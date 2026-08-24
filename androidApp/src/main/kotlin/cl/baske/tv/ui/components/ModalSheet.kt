package cl.baske.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.theme.LocalAccent

/**
 * Modal centrado (dialog): scrim oscuro a pantalla completa + una tarjeta
 * centrada con título y contenido. Reemplaza a los DropdownMenu. DEBE
 * renderizarse en el Box raíz de la pantalla (no dentro de un item de lista)
 * para que el scrim cubra todo.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ModalSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val device = LocalDevice.current
    // En TV el foco del D-pad debe SALTAR al modal apenas abre (si no, sigue
    // detrás del scrim, en la pantalla que no se ve). focusGroup + requestFocus
    // lleva el foco a la primera opción; exit=Cancel lo atrapa adentro para que
    // arriba/abajo recorra las opciones y no se escape al fondo.
    val optionsFocus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    LaunchedEffect(Unit) { if (device.isTv) runCatching { optionsFocus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF21C1C20))
                // Consume el tap para que tocar la tarjeta no cierre el modal.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(vertical = 8.dp),
        ) {
            // Header compacto tipo menú: título chico en mayúsculas, sin back button
            // (es un modal; se cierra tocando afuera o con el back del sistema).
            Text(
                title.uppercase(),
                color = Color(0x80FFFFFF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            )
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(optionsFocus)
                    .focusProperties { exit = { if (device.isTv) FocusRequester.Cancel else FocusRequester.Default } }
                    .focusGroup(),
            ) {
                content()
            }
        }
    }
}

/** Divisor fino entre opciones de un menú (lista con separadores). */
@Composable
fun ModalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(Color(0x14FFFFFF)),
    )
}

/** Fila de opción dentro de un ModalSheet (icono opcional + label + check si activa). */
@Composable
fun ModalOption(
    label: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val contentColor = when {
        destructive -> Color(0xFFFF6B6B)
        selected -> accent
        else -> Color.White
    }
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth()) { highlighted ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Resaltado cuadrado de borde a borde (estilo item de lista).
                .background(if (highlighted) Color(0x1FFFFFFF) else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, tint = if (selected || destructive) contentColor else Color(0xCCFFFFFF), modifier = Modifier.width(22.dp).height(22.dp))
            }
            Text(
                label,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.width(18.dp).height(18.dp))
        }
    }
}
