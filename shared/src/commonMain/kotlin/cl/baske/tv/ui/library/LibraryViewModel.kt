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

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        val items: List<HomeCard> = emptyList(),
        val total: Int = 0,
        val loadingMore: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var userId = ""
    private var serverUrl = ""
    private var includeTypes = ""
    private var loadedCount = 0
    private var fetching = false

    init { load() }

    private fun load() {
        val session = sessionStore.session.value ?: run {
            _state.value = UiState(loading = false, error = "No hay sesión activa"); return
        }
        userId = session.userId
        serverUrl = session.serverUrl
        viewModelScope.launch {
            val lib = runCatching { api.getItemDetail(userId, libraryId) }.getOrNull()
            // Ojo: con Recursive los hijos directos suelen ser carpetas, así que
            // filtramos por tipo de tope siempre — nunca episodios/temporadas.
            includeTypes = when (lib?.collectionType) {
                "movies" -> "Movie"
                "tvshows" -> "Series"
                "books" -> "Book"
                "boxsets" -> "BoxSet"
                else -> "Movie,Series,BoxSet,Video"
            }
            _state.update { it.copy(title = lib?.name ?: "Biblioteca") }
            fetchPage(reset = true)
        }
    }

    fun loadMore() {
        if (fetching || loadedCount >= _state.value.total && _state.value.total > 0) return
        if (loadedCount >= _state.value.total && loadedCount > 0) return
        viewModelScope.launch { fetchPage(reset = false) }
    }

    private suspend fun fetchPage(reset: Boolean) {
        if (fetching) return
        fetching = true
        if (!reset) _state.update { it.copy(loadingMore = true) }
        val start = if (reset) 0 else loadedCount
        val resp = runCatching {
            api.getLibraryItems(userId, libraryId, includeTypes, start, PAGE_SIZE)
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
}
