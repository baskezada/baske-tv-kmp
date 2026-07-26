package cl.baske.tv.ios

import cl.baske.tv.data.AuthRepository
import cl.baske.tv.data.Prefs
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.domain.Session
import cl.baske.tv.ui.connect.ConnectViewModel
import cl.baske.tv.ui.detail.DetailViewModel
import cl.baske.tv.ui.home.HomeUiState
import cl.baske.tv.ui.home.HomeViewModel
import cl.baske.tv.ui.library.LibraryViewModel
import cl.baske.tv.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Token de la suscripción a un StateFlow; Swift lo guarda y llama `cancel()` en
 * `onDisappear` para no seguir observando después de que la vista se fue.
 */
class Watcher(private val job: Job) {
    fun cancel() = job.cancel()
}

/**
 * Puente genérico Flow → closure de Swift. Queda `internal`: Swift no debe ver
 * esta versión con tipo borrado, solo los wrappers concretos de abajo (uno por
 * cada UiState), que sí exportan limpio al framework de Obj-C/Swift.
 */
internal fun <T> StateFlow<T>.watch(
    context: CoroutineContext = Dispatchers.Main,
    onEach: (T) -> Unit,
): Watcher {
    val job = kotlinx.coroutines.CoroutineScope(context).launch {
        collect { onEach(it) }
    }
    return Watcher(job)
}

fun ConnectViewModel.watchState(onEach: (ConnectViewModel.UiState) -> Unit): Watcher = state.watch(onEach = onEach)
fun HomeViewModel.watchState(onEach: (HomeUiState) -> Unit): Watcher = state.watch(onEach = onEach)
fun DetailViewModel.watchState(onEach: (DetailViewModel.UiState) -> Unit): Watcher = state.watch(onEach = onEach)
fun LibraryViewModel.watchState(onEach: (LibraryViewModel.UiState) -> Unit): Watcher = state.watch(onEach = onEach)
fun PlayerViewModel.watchState(onEach: (PlayerViewModel.UiState) -> Unit): Watcher = state.watch(onEach = onEach)
fun AuthRepository.watchSession(onEach: (Session?) -> Unit): Watcher = session.watch(onEach = onEach)
fun PrefsStore.watchPrefs(onEach: (Prefs) -> Unit): Watcher = prefs.watch(onEach = onEach)
