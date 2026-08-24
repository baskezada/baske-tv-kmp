package cl.baske.tv.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.toHomeCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 60

class LibraryViewModel(
    private val libraryId: String,
    private val api: EmbyApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    data class SortOption(val label: String, val by: String, val order: String)

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        val items: List<HomeCard> = emptyList(),
        val total: Int = 0,
        val loadingMore: Boolean = false,
        /** Letra seleccionada del filtro A-Z ("A".."Z", "#") o null = todas. */
        val letter: String? = null,
        val sortLabel: String = SORT_OPTIONS.first().label,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var userId = ""
    private var serverUrl = ""
    private var includeTypes = ""
    private var loadedCount = 0
    private var fetching = false
    private var sortBy = SORT_OPTIONS.first().by
    private var sortOrder = SORT_OPTIONS.first().order
    private var letter: String? = null

    init { load() }

    private fun load() {
        val session = sessionStore.session.value ?: run {
            _state.value = UiState(loading = false, error = "No hay sesión activa"); return
        }
        userId = session.userId
        serverUrl = session.serverUrl
        viewModelScope.launch {
            // El collectionType es clave para no traer episodios/videos sueltos.
            // /Users/{id}/Views SIEMPRE lo trae; getItemDetail a veces no, y ahí
            // caía al fallback con "Video" mostrando episodios sueltos. Se resuelve
            // por Views primero, con getItemDetail de respaldo.
            val view = runCatching { api.getViews(userId).items.firstOrNull { it.id == libraryId } }.getOrNull()
            val lib = view ?: runCatching { api.getItemDetail(userId, libraryId) }.getOrNull()
            includeTypes = when (lib?.collectionType) {
                "movies" -> "Movie"
                "tvshows" -> "Series"
                "books" -> "Book"
                "boxsets" -> "BoxSet"
                // Fallback SIN "Video" (como la web): evita listar episodios/videos
                // sueltos cuando el tipo no se conoce.
                else -> "Movie,Series,BoxSet"
            }
            _state.update { it.copy(title = lib?.name ?: "Biblioteca") }
            fetchPage(reset = true)
        }
    }

    /** Filtro por letra: "A".."Z", "#" (números/símbolos) o null = todas. */
    fun setLetter(l: String?) {
        if (l == letter) return
        letter = l
        _state.update { it.copy(letter = l, loading = true, items = emptyList()) }
        viewModelScope.launch { fetchPage(reset = true) }
    }

    fun setSort(option: SortOption) {
        if (option.by == sortBy && option.order == sortOrder) return
        sortBy = option.by
        sortOrder = option.order
        _state.update { it.copy(sortLabel = option.label, loading = true, items = emptyList()) }
        viewModelScope.launch { fetchPage(reset = true) }
    }

    fun loadMore() {
        // No paginar antes de que la carga inicial (load → fetchPage reset) haya
        // traído la primera página: el grid vacío dispara esto al instante y, sin
        // este guard, corría un fetch con includeTypes="" (aún sin resolver el
        // collectionType) que traía TODO recursivo (episodios) y su fetching=true
        // bloqueaba el fetch correcto de load(). Ese era el bug de "muestra caps".
        if (loadedCount == 0) return
        if (fetching) return
        if (loadedCount >= _state.value.total && _state.value.total > 0) return
        viewModelScope.launch { fetchPage(reset = false) }
    }

    private suspend fun fetchPage(reset: Boolean) {
        if (fetching) return
        fetching = true
        if (!reset) _state.update { it.copy(loadingMore = true) }
        val start = if (reset) 0 else loadedCount
        val resp = runCatching {
            api.getLibraryItems(
                userId, libraryId, includeTypes, start, PAGE_SIZE,
                sortBy = sortBy, sortOrder = sortOrder,
                nameStartsWith = letter?.takeIf { it != "#" },
                nameLessThan = if (letter == "#") "A" else null,
            )
        }.getOrNull()
        if (resp == null) {
            _state.update { it.copy(loading = false, loadingMore = false, error = if (it.items.isEmpty()) "No se pudo cargar la biblioteca" else it.error) }
            fetching = false
            return
        }
        val cards = resp.items.map { it.toHomeCard(serverUrl, wide = false) }
        _state.update {
            val merged = if (reset) cards else it.items + cards
            loadedCount = merged.size
            it.copy(loading = false, loadingMore = false, items = merged, total = resp.totalRecordCount ?: merged.size, error = null)
        }
        fetching = false
    }

    companion object {
        val SORT_OPTIONS = listOf(
            SortOption("Nombre (A-Z)", "SortName", "Ascending"),
            SortOption("Nombre (Z-A)", "SortName", "Descending"),
            SortOption("Agregado recientemente", "DateCreated", "Descending"),
            SortOption("Estreno (reciente)", "PremiereDate", "Descending"),
            SortOption("Estreno (antiguo)", "PremiereDate", "Ascending"),
            SortOption("Mejor calificados", "CommunityRating", "Descending"),
        )
        val LETTERS = listOf("#") + ('A'..'Z').map { it.toString() }
    }
}
