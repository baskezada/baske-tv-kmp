package cl.baske.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.data.HomeMode
import cl.baske.tv.data.PlayerEngine
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.platform.requestIfTv
import cl.baske.tv.ui.theme.ACCENT_PRESETS
import org.koin.compose.koinInject

// ---------------------------------------------------------------------------
// Ajustes: lista de secciones. Por ahora solo "Interfaz" (luego Mi perfil,
// Suscripción, Reproducción, Estadísticas).
// ---------------------------------------------------------------------------

@Composable
fun AjustesScreen(onBack: () -> Unit, onOpenInterfaz: () -> Unit) {
    val device = LocalDevice.current
    val firstFocus = remember { FocusRequester() }
    BackHandler { onBack() }
    LaunchedEffect(Unit) { firstFocus.requestIfTv(device) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = device.metrics.gutter, vertical = 20.dp),
    ) {
        SettingsHeader("Ajustes", onBack)
        Spacer(Modifier.height(16.dp))
        SettingsRow(
            icon = Icons.Filled.Tune,
            label = "Interfaz",
            focusRequester = firstFocus,
            onClick = onOpenInterfaz,
        )
        Spacer(Modifier.height(12.dp))
        val context = androidx.compose.ui.platform.LocalContext.current
        SettingsRow(
            icon = Icons.Filled.BugReport,
            label = "Exportar logs",
            onClick = { cl.baske.tv.core.LogExporter.export(context) },
        )
    }
}

// ---------------------------------------------------------------------------
// Interfaz: color de énfasis + vista de inicio.
// ---------------------------------------------------------------------------

@Composable
fun InterfazScreen(onBack: () -> Unit) {
    val prefsStore = koinInject<PrefsStore>()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle()
    val device = LocalDevice.current
    val firstFocus = remember { FocusRequester() }

    BackHandler { onBack() }
    LaunchedEffect(Unit) { firstFocus.requestIfTv(device) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = device.metrics.gutter, vertical = 20.dp),
    ) {
        SettingsHeader("Interfaz", onBack)
        Spacer(Modifier.height(24.dp))

        // ---- Color de énfasis ----
        SectionLabel("Color de énfasis")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ACCENT_PRESETS.forEachIndexed { index, color ->
                val active = color == prefs.accentColor
                Focusable(
                    onClick = { prefsStore.setAccentColor(color) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                ) { focused ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .then(
                                if (focused) Modifier.border(3.dp, Color.White, CircleShape)
                                else if (active) Modifier.border(2.dp, Color(0x99FFFFFF), CircleShape)
                                else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))

        // ---- Vista de inicio ----
        SectionLabel("Vista de inicio")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val modes = listOf(
                HomeMode.Vitrina to "Vitrina",
                HomeMode.Banner to "Con banner",
                HomeMode.SinBanner to "Sin banner",
            )
            modes.forEach { (mode, label) ->
                val active = prefs.homeMode == mode
                Focusable(onClick = { prefsStore.setHomeMode(mode) }) { focused ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Color(prefs.accentColor) else Color(0x1FFFFFFF))
                            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            label,
                            color = if (active) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(36.dp))

        // ---- Reproductor ----
        SectionLabel("Reproductor")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val engines = listOf(
                PlayerEngine.Vlc to "VLC",
                PlayerEngine.Mpv to "mpv",
            )
            engines.forEach { (engine, label) ->
                val active = prefs.playerEngine == engine
                Focusable(onClick = { prefsStore.setPlayerEngine(engine) }) { focused ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Color(prefs.accentColor) else Color(0x1FFFFFFF))
                            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(
                            label,
                            color = if (active) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "mpv (por defecto) es más fiable y renderiza subtítulos ASS. VLC queda como alternativa.",
            color = Color(0x80FFFFFF),
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Focusable(onClick = onBack) { highlighted ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF))
                    .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

/** Fila de sección de Ajustes: card con icono + label + chevron. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        modifier = (focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier).fillMaxWidth(),
    ) { highlighted ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (highlighted) Color(0x24FFFFFF) else Color(0x12FFFFFF))
                .then(if (highlighted) Modifier.border(1.5.dp, Color(0x45FFFFFF), RoundedCornerShape(14.dp)) else Modifier)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0x66FFFFFF), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = Color(0x66FFFFFF),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 14.dp),
    )
}
