package cl.baske.tv.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.connect.ConnectViewModel.Step
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("BaskeTV", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.step == Step.EnterServer) "Conéctate a tu servidor Emby"
                else state.serverName?.let { "Conectado a $it" } ?: "Inicia sesión",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            when (state.step) {
                Step.EnterServer -> ServerStep(state, viewModel)
                Step.EnterCredentials -> CredentialsStep(state, viewModel)
            }

            state.error?.let { error ->
                Spacer(Modifier.height(16.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ServerStep(state: ConnectViewModel.UiState, viewModel: ConnectViewModel) {
    val focus = rememberInitialFocus()
    OutlinedTextField(
        value = state.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label = { Text("Dirección del servidor") },
        placeholder = { Text("emby.miservidor.com") },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.connectToServer() }),
        modifier = Modifier.widthIn(max = 460.dp).focusRequester(focus),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick = viewModel::connectToServer,
        enabled = !state.busy && state.serverUrl.isNotBlank(),
    ) {
        BusyLabel(state.busy, "Conectar")
    }
}

@Composable
private fun CredentialsStep(state: ConnectViewModel.UiState, viewModel: ConnectViewModel) {
    val focus = rememberInitialFocus()
    OutlinedTextField(
        value = state.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text("Usuario") },
        singleLine = true,
        enabled = !state.busy,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.widthIn(max = 460.dp).focusRequester(focus),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.password,
        onValueChange = viewModel::onPasswordChange,
        label = { Text("Contraseña") },
        singleLine = true,
        enabled = !state.busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.login() }),
        modifier = Modifier.widthIn(max = 460.dp),
    )
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = viewModel::back, enabled = !state.busy) { Text("Atrás") }
        Button(
            onClick = viewModel::login,
            enabled = !state.busy && state.username.isNotBlank(),
        ) {
            BusyLabel(state.busy, "Entrar")
        }
    }
}

@Composable
private fun BusyLabel(busy: Boolean, label: String) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.width(18.dp).height(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    } else {
        Text(label)
    }
}

@Composable
private fun rememberInitialFocus(): FocusRequester {
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }
    return focus
}
