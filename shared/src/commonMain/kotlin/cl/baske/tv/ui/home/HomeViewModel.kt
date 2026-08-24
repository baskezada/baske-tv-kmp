package cl.baske.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.baske.tv.data.HomeRepository
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.remote.EmbyApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val VIDEO_COLLECTION_TYPES = setOf("movies", "tvshows", "boxsets", "mixed", "homevideos")
private val MOVIE_SERIES_TYPES = setOf("movies", "tvshows", "mixed")

class HomeViewModel(
    private val repository: HomeRepository,
    private val sessionStore: SessionStore,
    private val api: EmbyApi,
) : ViewModel() {

    val isAdmin: Boolean get() = sessionStore.session.value?.isAdmin ?: false

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { load(fresh = false) }

    fun refresh() = load(fresh = true)

    /**
     * Revalida SOLO "Continuar viendo" contra el server (fresh) y actualiza esa
     * fila en el sitio, sin recargar todo el Home ni mostrar spinner. Se llama al
     * volver del player para que el progreso recién visto se refleje al toque.
     */
    fun refreshResume() {
        val session = sessionStore.session.value ?: return
        // No correr durante el load inicial: ese ya trae "Continuar viendo", y si
        // este emite antes (carrera) queda una lista sin hero que luego, al
        // aparecer el hero al tope, deja el scroll pasado el banner.
        if (_state.value.loading || _state.value.rows.isEmpty()) return
        viewModelScope.launch {
            val items = runCatching { repository.resume(session.userId, fresh = true) }.getOrNull() ?: return@launch
            val cards = items.map { it.toHomeCard(session.serverUrl, wide = true) }
            _state.update { s ->
                val others = s.rows.filterNot { it.id == "resume" }
                val rows = if (cards.isEmpty()) others
                else listOf(HomeRow("resume", "Continuar viendo", cards, portrait = false)) + others
                s.copy(rows = rows)
            }
        }
    }

    private fun load(fresh: Boolean) {
        val session = sessionStore.session.value ?: return
        val userId = session.userId
        val serverUrl = session.serverUrl

        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val content = buildHome(userId, serverUrl, fresh)
            _state.value = HomeUiState(
                loading = false,
                rows = content.rows,
                featured = content.featured,
                error = if (content.rows.isEmpty()) "No se pudo cargar el inicio" else null,
            )
        }
    }

    private data class HomeContent(val rows: List<HomeRow>, val featured: List<HomeCard>)

    /**
     * Orden del web: Continuar viendo → Vistas → "Recién añadido en {biblioteca}"
     * (una fila por biblioteca de video). `featured` = los 6 recién agregados
     * más nuevos, que rotan en el banner.
     */
    private suspend fun buildHome(userId: String, serverUrl: String, fresh: Boolean): HomeContent = coroutineScope {
        val resumeDeferred = async { runCatching { repository.resume(userId, fresh) }.getOrDefault(emptyList()) }
        val viewsDeferred = async { runCatching { repository.views(userId, fresh) }.getOrDefault(emptyList()) }

        val resume = resumeDeferred.await()
        val views = viewsDeferred.await()

        // "Mi biblioteca" y las filas de "Recién añadido": solo bibliotecas de
        // películas/series (movies, tvshows, mixed). Nada de música, libros, fotos.
        val libraries = views.filter { it.collectionType in MOVIE_SERIES_TYPES }
        val latestPerLibrary = libraries.map { lib ->
            async {
                lib to runCatching { repository.latestForLibrary(userId, lib.id, fresh) }.getOrDefault(emptyList())
            }
        }.awaitAll()

        val rows = buildList {
            addRow("resume", "Continuar viendo", resume, serverUrl, wide = true)
            addRow("views", "Mi biblioteca", libraries, serverUrl, wide = true)
            latestPerLibrary.forEach { (lib, items) ->
                addRow("lib-${lib.id}", "Recién añadido en ${lib.name ?: ""}", items, serverUrl, wide = false)
            }
        }
        // Destacados: recién agregados de todas las bibliotecas, sin repetir, 6.
        val featured = latestPerLibrary
            .flatMap { it.second }
            .distinctBy { it.id }
            .take(6)
            .map { it.toHomeCard(serverUrl, wide = true) }

        HomeContent(rows, featured)
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

    /**
     * Resuelve qué reproducir desde el banner: película/episodio → él mismo;
     * serie → NextUp y, si viene vacío (serie no empezada), el primer episodio.
     * `onResolved` recibe el id a reproducir (se llama en el hilo principal).
     */
    fun resolveHeroPlay(card: HomeCard, onResolved: (String) -> Unit) {
        if (card.type != "Series") { onResolved(card.id); return }
        val uid = userId() ?: run { onResolved(card.id); return }
        viewModelScope.launch {
            val ep = runCatching { api.getSeriesNextUp(uid, card.id).items.firstOrNull() }.getOrNull()
                ?: runCatching { api.getSeriesEpisodes(card.id, uid).items.firstOrNull() }.getOrNull()
            onResolved(ep?.id ?: card.id)
        }
    }

    // ---- Acciones del menú contextual "more" (como la web, según el tipo) ----

    private fun userId(): String? = sessionStore.session.value?.userId

    /** Aplica una transformación a la card `id` en todas las filas (update optimista). */
    private fun mutateCard(id: String, transform: (HomeCard) -> HomeCard) {
        _state.update { s ->
            s.copy(rows = s.rows.map { row ->
                row.copy(cards = row.cards.map { if (it.id == id) transform(it) else it })
            })
        }
    }

    fun toggleWatched(card: HomeCard) {
        val uid = userId() ?: return
        val played = !card.played
        mutateCard(card.id) { it.copy(played = played, progress = if (played) 0f else it.progress) }
        viewModelScope.launch { runCatching { api.setPlayed(uid, card.id, played) } }
    }

    fun toggleFavorite(card: HomeCard) {
        val uid = userId() ?: return
        val fav = !card.isFavorite
        mutateCard(card.id) { it.copy(isFavorite = fav) }
        viewModelScope.launch { runCatching { api.setFavorite(uid, card.id, fav) } }
    }

    /** Quita el ítem de "Continuar viendo": lo saca de esa fila localmente y avisa al server. */
    fun removeFromResume(card: HomeCard) {
        val uid = userId() ?: return
        _state.update { s ->
            s.copy(rows = s.rows.mapNotNull { row ->
                if (row.id != "resume") row
                else row.copy(cards = row.cards.filterNot { it.id == card.id })
                    .takeIf { it.cards.isNotEmpty() }
            })
        }
        viewModelScope.launch { runCatching { api.hideFromResume(uid, card.id) } }
    }

    fun refreshMetadata(card: HomeCard) {
        viewModelScope.launch { runCatching { api.refreshItem(card.id) } }
    }

    fun syncFiles(card: HomeCard) {
        viewModelScope.launch { runCatching { api.syncItemFiles(card.id) } }
    }

    fun scanLibrary(card: HomeCard) {
        viewModelScope.launch { runCatching { api.scanLibrary(card.id) } }
    }
}
