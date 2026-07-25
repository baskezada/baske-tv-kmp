package cl.baske.tv.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Modo de layout del Home (equivalente a los ajustes del web). */
enum class HomeMode { Vitrina, Banner, SinBanner }

data class Prefs(
    /** ARGB. Default cian, igual que el web (#06b6d4). */
    val accentColor: Long = 0xFF06B6D4,
    val homeMode: HomeMode = HomeMode.Vitrina,
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

    private fun load(): Prefs = Prefs(
        accentColor = settings.getLong(KEY_ACCENT, 0xFF06B6D4),
        homeMode = runCatching { HomeMode.valueOf(settings.getStringOrNull(KEY_HOME_MODE) ?: "") }
            .getOrDefault(HomeMode.Vitrina),
    )

    private companion object {
        const val KEY_ACCENT = "pref_accent"
        const val KEY_HOME_MODE = "pref_home_mode"
    }
}
