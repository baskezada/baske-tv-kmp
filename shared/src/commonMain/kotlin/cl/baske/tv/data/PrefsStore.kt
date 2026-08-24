package cl.baske.tv.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Modo de layout del Home (equivalente a los ajustes del web). */
enum class HomeMode { Vitrina, Banner, SinBanner }

/** Motor de reproducción. VLC = actual; MPV = más fiable (libass), seleccionable. */
enum class PlayerEngine { Vlc, Mpv }

data class Prefs(
    /** ARGB. Default cian, igual que el web (#06b6d4). */
    val accentColor: Long = 0xFF06B6D4,
    val homeMode: HomeMode = HomeMode.Vitrina,
    val playerEngine: PlayerEngine = PlayerEngine.Mpv,
)

/** Preferencias de interfaz persistidas (accent + modo de Home). */
class PrefsStore(private val settings: Settings) {

    private val _prefs = MutableStateFlow(load())
    val prefs: StateFlow<Prefs> = _prefs.asStateFlow()

    fun setAccentColor(color: Long) {
        settings.putLong(KEY_ACCENT, color)
        _prefs.update { it.copy(accentColor = color) }
    }

    fun setHomeMode(mode: HomeMode) {
        settings.putString(KEY_HOME_MODE, mode.name)
        _prefs.update { it.copy(homeMode = mode) }
    }

    fun setPlayerEngine(engine: PlayerEngine) {
        settings.putString(KEY_PLAYER_ENGINE, engine.name)
        _prefs.update { it.copy(playerEngine = engine) }
    }

    private fun load(): Prefs = Prefs(
        accentColor = settings.getLong(KEY_ACCENT, 0xFF06B6D4),
        homeMode = runCatching { HomeMode.valueOf(settings.getStringOrNull(KEY_HOME_MODE) ?: "") }
            .getOrDefault(HomeMode.Vitrina),
        playerEngine = runCatching { PlayerEngine.valueOf(settings.getStringOrNull(KEY_PLAYER_ENGINE) ?: "") }
            .getOrDefault(PlayerEngine.Mpv),
    )

    private companion object {
        const val KEY_ACCENT = "pref_accent"
        const val KEY_HOME_MODE = "pref_home_mode"
        const val KEY_PLAYER_ENGINE = "pref_player_engine"
    }
}
