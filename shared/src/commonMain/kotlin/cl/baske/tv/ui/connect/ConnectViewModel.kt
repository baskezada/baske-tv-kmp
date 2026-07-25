package cl.baske.tv.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.baske.tv.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ConnectViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    enum class Step { EnterServer, EnterCredentials }

    data class UiState(
        val serverUrl: String = "",
        val username: String = "",
        val password: String = "",
        val step: Step = Step.EnterServer,
        val serverName: String? = null,
        val busy: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun back() = _state.update { it.copy(step = Step.EnterServer, error = null) }

    /** Paso 1: comprueba que la URL sea un Emby y avanza a las credenciales. */
    fun connectToServer() {
        val current = _state.value
        if (current.serverUrl.isBlank() || current.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.validateServer(current.serverUrl)
                .onSuccess { conn ->
                    _state.update {
                        it.copy(
                            busy = false,
                            step = Step.EnterCredentials,
                            // Nos quedamos con la base resuelta (puede incluir /emby).
                            serverUrl = conn.baseUrl,
                            serverName = conn.info.serverName ?: conn.info.productName,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(busy = false, error = "No se pudo conectar al servidor: ${e.message}") }
                }
        }
    }

    /** Paso 2: autentica. Al guardar la sesión, RootNav navega solo al Home. */
    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.login(current.serverUrl, current.username, current.password)
                .onFailure { e ->
                    _state.update { it.copy(busy = false, error = "No se pudo iniciar sesión: ${e.message}") }
                }
            // El éxito no necesita manejo acá: SessionStore.session emite y
            // RootNav reacciona. Si falló, ya mostramos el error arriba.
        }
    }
}
