package cl.baske.tv.ui.connect

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.R
import cl.baske.tv.ui.connect.ConnectViewModel.Step
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.theme.LocalAccent
import org.koin.androidx.compose.koinViewModel

private val BG = Color(0xFF080808)
private val FIELD_MAX = 420.dp

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    Box(modifier = Modifier.fillMaxSize().background(BG)) {
        // Glow de acento arriba, para dar profundidad (como los banners de adentro).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.13f), Color.Transparent))),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Marca: león (tintado con el acento) + wordmark, igual que el header interno.
            Box(modifier = Modifier.height(46.dp)) {
                Image(
                    painterResource(R.drawable.logo_symbol),
                    contentDescription = "BaskeTV",
                    modifier = Modifier.height(46.dp),
                    colorFilter = ColorFilter.tint(accent),
                )
                Image(painterResource(R.drawable.logo_text), contentDescription = null, modifier = Modifier.height(46.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                when {
                    state.step == Step.EnterServer -> "Conéctate a tu servidor Emby"
                    state.serverName != null -> "Conectado a ${state.serverName}"
                    else -> "Inicia sesión"
                },
                color = Color(0x99FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(36.dp))

            when (state.step) {
                Step.EnterServer -> ServerStep(state, viewModel)
                Step.EnterCredentials -> CredentialsStep(state, viewModel)
            }

            state.error?.let { error ->
                Spacer(Modifier.height(20.dp))
                ErrorChip(error)
            }
        }
    }
}

@Composable
private fun ServerStep(state: ConnectViewModel.UiState, viewModel: ConnectViewModel) {
    val focus = rememberInitialFocus()
    LoginField(
        value = state.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label = "Dirección del servidor",
        placeholder = "emby.miservidor.com",
        leading = Icons.Outlined.Dns,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.connectToServer() }),
        modifier = Modifier.widthIn(max = FIELD_MAX).fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(24.dp))
    PrimaryButton(
        label = "Conectar",
        busy = state.busy,
        enabled = state.serverUrl.isNotBlank(),
        onClick = viewModel::connectToServer,
    )
}

@Composable
private fun CredentialsStep(state: ConnectViewModel.UiState, viewModel: ConnectViewModel) {
    val focus = rememberInitialFocus()
    var passwordVisible by remember { mutableStateOf(false) }
    val accent = LocalAccent.current

    LoginField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        label = "Usuario",
        leading = Icons.Outlined.Person,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.widthIn(max = FIELD_MAX).fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(14.dp))
    LoginField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = "Contraseña",
        leading = Icons.Outlined.Lock,
        enabled = !state.busy,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.login() }),
        trailing = {
            Focusable(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (passwordVisible) "Ocultar" else "Mostrar",
                    tint = Color(0x99FFFFFF),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        modifier = Modifier.widthIn(max = FIELD_MAX).fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("Atrás", onClick = viewModel::back)
        PrimaryButton(
            label = "Entrar",
            busy = state.busy,
            enabled = state.username.isNotBlank(),
            onClick = viewModel::login,
        )
    }
}

// ---------------------------------------------------------------------------
// Componentes estilizados (acento cian, fondo oscuro), a tono con el interior.
// ---------------------------------------------------------------------------

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leading: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = Color(0x66FFFFFF)) } },
        leadingIcon = { Icon(leading, contentDescription = null, modifier = Modifier.size(22.dp)) },
        trailingIcon = trailing,
        singleLine = true,
        enabled = enabled,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0x16FFFFFF),
            unfocusedContainerColor = Color(0x0FFFFFFF),
            disabledContainerColor = Color(0x0AFFFFFF),
            focusedBorderColor = accent,
            unfocusedBorderColor = Color(0x2EFFFFFF),
            focusedLeadingIconColor = accent,
            unfocusedLeadingIconColor = Color(0x80FFFFFF),
            focusedLabelColor = accent,
            unfocusedLabelColor = Color(0x80FFFFFF),
            cursorColor = accent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
        modifier = modifier,
    )
}

@Composable
private fun PrimaryButton(
    label: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val active = enabled && !busy
    Focusable(onClick = onClick, enabled = active) { highlighted ->
        // Blanco con texto negro, como el botón "Reproducir" del hero. Al enfocar,
        // un anillo de acento (que contrasta sobre el blanco).
        Box(
            modifier = Modifier
                .clipPill()
                .background(if (active) Color.White else Color(0x59FFFFFF))
                .then(if (highlighted) Modifier.border(2.5.dp, accent, RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 30.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
            } else {
                Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Focusable(onClick = onClick) { highlighted ->
        Box(
            modifier = Modifier
                .clipPill()
                .border(1.5.dp, if (highlighted) Color.White else Color(0x40FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 24.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun ErrorChip(message: String) {
    Row(
        modifier = Modifier
            .widthIn(max = FIELD_MAX)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FF5A5A))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFFF8A8A), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, color = Color(0xFFFFB3B3), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun Modifier.clipPill(): Modifier = this.clip(RoundedCornerShape(50))

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return focus
}
