package cl.baske.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.baske.tv.data.HomeRepository
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val VIDEO_COLLECTION_TYPES = setOf("movies", "tvshows", "boxsets", "mixed", "homevideos")

class HomeViewModel(
    private val repository: HomeRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load(fresh = false) }

    fun refresh() = load(fresh = true)

    private fun load(fresh: Boolean) {
        val session = sessionStore.session.value ?: return
        val userId = session.userId
        val serverUrl = session.serverUrl

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val rows = buildHome(userId, serverUrl, fresh)
            _state.value = HomeUiState(
                loading = false,
                rows = rows,
                error = if (rows.isEmpty()) "No se pudo cargar el inicio" else null,
            )
        }
    }

    /**
     * Orden del web: Continuar viendo → Vistas → "Recién añadido en {biblioteca}"
     * (una fila por biblioteca de video).
     */
    private suspend fun buildHome(userId: String, serverUrl: String, fresh: Boolean): List<HomeRow> = coroutineScope {
        val resumeDeferred = async { runCatching { repository.resume(userId, fresh) }.getOrDefault(emptyList()) }
        val viewsDeferred = async { runCatching { repository.views(userId, fresh) }.getOrDefault(emptyList()) }

        val resume = resumeDeferred.await()
        val views = viewsDeferred.await()

        val libraries = views.filter {
            it.collectionType == null || it.collectionType in VIDEO_COLLECTION_TYPES
        }
        val latestPerLibrary = libraries.map { lib ->
            async {
                lib to runCatching { repository.latestForLibrary(userId, lib.id, fresh) }.getOrDefault(emptyList())
            }
        }.awaitAll()

        buildList {
            addRow("resume", "Continuar viendo", resume, serverUrl, wide = true)
            addRow("views", "Vistas", views, serverUrl, wide = true)
            latestPerLibrary.forEach { (lib, items) ->
                addRow("lib-${lib.id}", "Recién añadido en ${lib.name ?: ""}", items, serverUrl, wide = false)
            }
        }
    }

    private fun MutableList<HomeRow>.addRow(
        id: String,
        title: String,
        items: List<BaseItemDto>,
        serverUrl: String,
        wide: Boolean,
    ) {
        if (items.isEmpty()) return
        add(HomeRow(id, title, items.map { it.toHomeCard(serverUrl, wide) }, portrait = !wide))
    }
}
