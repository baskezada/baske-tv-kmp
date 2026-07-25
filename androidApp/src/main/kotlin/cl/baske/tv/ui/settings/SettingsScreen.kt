package cl.baske.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.data.AuthRepository
import cl.baske.tv.data.HomeMode
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.ui.theme.ACCENT_PRESETS
import org.koin.compose.koinInject

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefsStore = koinInject<PrefsStore>()
    val authRepository = koinInject<AuthRepository>()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }

    BackHandler { onBack() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 40.dp),
    ) {
        Text("Ajustes", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        // ---- Color de acento ----
        SectionLabel("Color de acento")
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

        // ---- Modo de inicio ----
        SectionLabel("Inicio")
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
        Spacer(Modifier.height(40.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Focusable(onClick = onBack) { focused ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1FFFFFFF))
                        .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text("Volver", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Focusable(onClick = { authRepository.logout() }) { focused ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22EF4444))
                        .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text("Cerrar sesión", color = Color(0xFFFF8080), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
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

/** Wrapper enfocable por D-pad; expone si está enfocado para pintar el anillo. */
@Composable
private fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        content(focused)
    }
}
