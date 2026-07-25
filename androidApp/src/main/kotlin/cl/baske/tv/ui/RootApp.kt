package cl.baske.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.data.AuthRepository
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.ui.connect.ConnectScreen
import cl.baske.tv.ui.detail.DetailScreen
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.HomeScreen
import cl.baske.tv.ui.home.NavTarget
import cl.baske.tv.ui.library.LibraryScreen
import cl.baske.tv.ui.player.PlayerScreen
import cl.baske.tv.ui.settings.SettingsScreen
import cl.baske.tv.ui.theme.BaskeTheme
import cl.baske.tv.ui.theme.LocalAccent
import org.koin.compose.koinInject

private enum class Screen { Home, Settings }

@Composable
fun RootApp() {
    val authRepository = koinInject<AuthRepository>()
    val prefsStore = koinInject<PrefsStore>()
    val session by authRepository.session.collectAsStateWithLifecycle()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle()

    var playingItemId by remember { mutableStateOf<String?>(null) }
    var detailItemId by remember { mutableStateOf<String?>(null) }
    var libraryId by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf(Screen.Home) }

    LaunchedEffect(session == null) {
        if (session == null) { screen = Screen.Home; playingItemId = null; detailItemId = null; libraryId = null }
    }

    fun openCard(card: HomeCard) {
        when (card.navTarget) {
            NavTarget.Player -> playingItemId = card.id
            NavTarget.Detail -> detailItemId = card.id
            NavTarget.Library -> libraryId = card.id
        }
    }

    BaskeTheme {
        CompositionLocalProvider(LocalAccent provides Color(prefs.accentColor)) {
            when {
                session == null -> ConnectScreen()
                playingItemId != null -> PlayerScreen(
                    itemId = playingItemId!!,
                    onExit = { playingItemId = null },
                )
                detailItemId != null -> DetailScreen(
                    itemId = detailItemId!!,
                    onBack = { detailItemId = null },
                    onPlay = { id -> playingItemId = id },
                )
                libraryId != null -> LibraryScreen(
                    libraryId = libraryId!!,
                    onBack = { libraryId = null },
                    onOpenCard = { card -> openCard(card) },
                )
                screen == Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
                else -> HomeScreen(
                    onOpenSettings = { screen = Screen.Settings },
                    onCardClick = { card -> openCard(card) },
                    onPlayItem = { card -> if (card.playDirect) playingItemId = card.id else detailItemId = card.id },
                )
            }
        }
    }
}
