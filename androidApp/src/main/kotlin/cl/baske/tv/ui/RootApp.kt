package cl.baske.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.data.AuthRepository
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.ui.connect.ConnectScreen
import cl.baske.tv.ui.detail.DetailScreen
import cl.baske.tv.data.remote.SeerrResult
import cl.baske.tv.ui.discover.DiscoverSearchScreen
import cl.baske.tv.ui.discover.ProviderBrowse
import cl.baske.tv.ui.discover.ProviderScreen
import cl.baske.tv.ui.discover.TmdbDetailScreen
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.HomeScreen
import cl.baske.tv.ui.home.NavTarget
import cl.baske.tv.ui.library.LibraryScreen
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.platform.rememberDevice
import cl.baske.tv.ui.settings.AjustesScreen
import cl.baske.tv.ui.settings.InterfazScreen
import cl.baske.tv.ui.theme.BaskeTheme
import cl.baske.tv.ui.theme.LocalAccent
import org.koin.compose.koinInject

private enum class Screen { Home, Ajustes, Interfaz }

@Composable
fun RootApp(
    deepLinkPlay: StateFlow<String?>? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val authRepository = koinInject<AuthRepository>()
    val prefsStore = koinInject<PrefsStore>()
    val session by authRepository.session.collectAsStateWithLifecycle()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle()

    var playingItemId by remember { mutableStateOf<String?>(null) }
    var detailItemId by remember { mutableStateOf<String?>(null) }
    var libraryId by remember { mutableStateOf<String?>(null) }
    var providerBrowse by remember { mutableStateOf<ProviderBrowse?>(null) }
    var tmdbItem by remember { mutableStateOf<SeerrResult?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.Home) }

    LaunchedEffect(session == null) {
        if (session == null) { screen = Screen.Home; playingItemId = null; detailItemId = null; libraryId = null; providerBrowse = null; tmdbItem = null; searchOpen = false }
    }

    // Deep link de una card de Watch Next del launcher (basketv://play/{id}):
    // reproducir ese ítem, pero solo con sesión activa (si no, espera al login).
    if (deepLinkPlay != null) {
        val pendingPlay by deepLinkPlay.collectAsStateWithLifecycle()
        LaunchedEffect(pendingPlay, session != null) {
            val id = pendingPlay
            if (id != null && session != null) {
                playingItemId = id
                onDeepLinkConsumed()
            }
        }
    }

    fun openCard(card: HomeCard) {
        when (card.navTarget) {
            NavTarget.Player -> playingItemId = card.id
            NavTarget.Detail -> detailItemId = card.id
            NavTarget.Library -> libraryId = card.id
        }
    }

    // Conserva el estado (scroll, tab) de Home y Biblioteca al navegar a un item
    // y volver: guarda su estado saveable cuando salen de composición y lo
    // restaura al reaparecer.
    val screenState = rememberSaveableStateHolder()

    BaskeTheme {
        CompositionLocalProvider(
            LocalAccent provides Color(prefs.accentColor),
            LocalDevice provides rememberDevice(),
        ) {
            when {
                session == null -> ConnectScreen()
                // key(playingItemId): al auto-avanzar de episodio, el id cambia y
                // esto REMONTA el player entero — MediaPlayer y estado frescos, y
                // el onDispose del anterior reporta Stopped + libera. Sin el key,
                // los remember/estado del player anterior se arrastrarían.
                playingItemId != null -> key(playingItemId) {
                    cl.baske.tv.ui.player.MpvPlayerScreen(
                        itemId = playingItemId!!,
                        onExit = { playingItemId = null },
                        onPlayItem = { nextId -> playingItemId = nextId },
                    )
                }
                detailItemId != null -> DetailScreen(
                    itemId = detailItemId!!,
                    onBack = { detailItemId = null },
                    onPlay = { id -> playingItemId = id },
                )
                tmdbItem != null -> TmdbDetailScreen(item = tmdbItem!!, onBack = { tmdbItem = null })
                searchOpen -> DiscoverSearchScreen(
                    onBack = { searchOpen = false },
                    onOpenLocal = { card -> searchOpen = false; openCard(card) },
                    onOpenTmdb = { item -> searchOpen = false; tmdbItem = item },
                )
                libraryId != null -> screenState.SaveableStateProvider("library:${libraryId}") {
                    LibraryScreen(
                        libraryId = libraryId!!,
                        onBack = { libraryId = null },
                        onOpenCard = { card -> openCard(card) },
                    )
                }
                providerBrowse != null -> screenState.SaveableStateProvider("provider:${providerBrowse!!.providerId}") {
                    ProviderScreen(
                        browse = providerBrowse!!,
                        onBack = { providerBrowse = null },
                        onOpenLocal = { card -> providerBrowse = null; openCard(card) },
                        onOpenTmdb = { item -> tmdbItem = item },
                    )
                }
                screen == Screen.Ajustes -> AjustesScreen(
                    onBack = { screen = Screen.Home },
                    onOpenInterfaz = { screen = Screen.Interfaz },
                )
                screen == Screen.Interfaz -> InterfazScreen(onBack = { screen = Screen.Ajustes })
                else -> screenState.SaveableStateProvider("home") {
                    HomeScreen(
                        onOpenSettings = { screen = Screen.Ajustes },
                        onLogout = { authRepository.logout() },
                        onCardClick = { card -> openCard(card) },
                        onPlayItem = { card -> if (card.playDirect) playingItemId = card.id else detailItemId = card.id },
                        onOpenProvider = { id, name -> providerBrowse = ProviderBrowse(id, name) },
                        onOpenAnime = { providerBrowse = ProviderBrowse(null, "Anime de temporada") },
                        onOpenTmdb = { item -> tmdbItem = item },
                        onOpenSearch = { searchOpen = true },
                    )
                }
            }
        }
    }
}
