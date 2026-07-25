package cl.baske.tv.data

import cl.baske.tv.domain.Session
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Guarda la sesión activa (y el deviceId estable) en almacenamiento nativo.
 * Expone la sesión como StateFlow para que el resto de la app reaccione al
 * login/logout sin pasar callbacks.
 */
class SessionStore(private val settings: Settings) {

    private val _session = MutableStateFlow(load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    /** deviceId persistente: se genera una sola vez y se reusa siempre. */
    @OptIn(ExperimentalUuidApi::class)
    fun deviceId(): String {
        settings.getStringOrNull(KEY_DEVICE_ID)?.let { return it }
        val id = Uuid.random().toString().replace("-", "")
        settings.putString(KEY_DEVICE_ID, id)
        return id
    }

    fun save(session: Session) {
        settings.putString(KEY_SERVER_URL, session.serverUrl)
        settings.putString(KEY_TOKEN, session.accessToken)
        settings.putString(KEY_USER_ID, session.userId)
        settings.putString(KEY_USER_NAME, session.userName)
        settings.putBoolean(KEY_IS_ADMIN, session.isAdmin)
        _session.value = session
    }

    fun clear() {
        listOf(KEY_SERVER_URL, KEY_TOKEN, KEY_USER_ID, KEY_USER_NAME, KEY_IS_ADMIN)
            .forEach { settings.remove(it) }
        _session.value = null
    }

    private fun load(): Session? {
        val serverUrl = settings.getStringOrNull(KEY_SERVER_URL) ?: return null
        val token = settings.getStringOrNull(KEY_TOKEN) ?: return null
        val userId = settings.getStringOrNull(KEY_USER_ID) ?: return null
        return Session(
            serverUrl = serverUrl,
            accessToken = token,
            userId = userId,
            userName = settings.getStringOrNull(KEY_USER_NAME) ?: "",
            isAdmin = settings.getBoolean(KEY_IS_ADMIN, false),
        )
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_TOKEN = "access_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_IS_ADMIN = "is_admin"
    }
}
