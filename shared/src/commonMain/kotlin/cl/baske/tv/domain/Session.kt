package cl.baske.tv.domain

/**
 * Sesión activa contra un servidor Emby. Es lo mínimo que necesita todo el
 * resto de la app para hacer requests autenticados y saber a quién pertenecen.
 */
data class Session(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String,
    val isAdmin: Boolean,
)
