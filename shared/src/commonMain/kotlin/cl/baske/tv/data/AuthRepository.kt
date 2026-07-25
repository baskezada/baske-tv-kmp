package cl.baske.tv.data

import cl.baske.tv.core.normalizeServerUrl
import cl.baske.tv.data.model.PublicSystemInfo
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.domain.Session
import kotlinx.coroutines.flow.StateFlow

/** Servidor resuelto: la base que realmente responde la API + su info pública. */
data class ServerConnection(
    val baseUrl: String,
    val info: PublicSystemInfo,
)

class AuthRepository(
    private val api: EmbyApi,
    private val sessionStore: SessionStore,
) {
    val session: StateFlow<Session?> = sessionStore.session

    /**
     * Resuelve y valida el servidor. Muchos despliegues sirven el cliente web
     * en la raíz y la API bajo `/emby` (el caso de emby.baske.cl), así que
     * probamos ambos candidatos y nos quedamos con el que responde JSON.
     */
    suspend fun validateServer(rawUrl: String): Result<ServerConnection> = runCatching {
        val base = resolveBaseUrl(rawUrl)
        ServerConnection(base, api.getPublicSystemInfo(base))
    }

    /** Autentica contra la base ya resuelta y persiste la sesión. */
    suspend fun login(baseUrl: String, username: String, password: String): Result<Session> = runCatching {
        val url = normalizeServerUrl(baseUrl)
        val result = api.authenticateByName(url, username, password)
        val token = result.accessToken ?: error("El servidor no devolvió un AccessToken")
        val user = result.user ?: error("El servidor no devolvió el usuario")
        val session = Session(
            serverUrl = url,
            accessToken = token,
            userId = user.id,
            userName = user.name,
            isAdmin = user.policy?.isAdministrator ?: false,
        )
        sessionStore.save(session)
        session
    }

    fun logout() = sessionStore.clear()

    private suspend fun resolveBaseUrl(rawUrl: String): String {
        val normalized = normalizeServerUrl(rawUrl)
        // Si el usuario ya escribió el prefijo, no lo dupliques.
        val candidates = buildList {
            add(normalized)
            if (!normalized.endsWith("/emby")) add("$normalized/emby")
        }
        for (candidate in candidates) {
            val ok = runCatching { api.getPublicSystemInfo(candidate) }.isSuccess
            if (ok) return candidate
        }
        error("No respondió como un servidor Emby en $normalized (ni en $normalized/emby)")
    }
}
