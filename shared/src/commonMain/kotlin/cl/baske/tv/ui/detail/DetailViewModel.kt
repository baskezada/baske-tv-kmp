package cl.baske.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.baske.tv.core.embyImageUrl
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.remote.EmbyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TICKS_PER_MS = 10_000L
private const val TICKS_PER_MIN = 600_000_000L

class DetailViewModel(
    private val itemId: String,
    private val api: EmbyApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    enum class Kind { Movie, Series, Book, Other }

    data class SeasonTab(val id: String, val name: String, val episodeCount: Int? = null)
    data class EpisodeItem(
        val id: String,
        val title: String,
        val meta: String,
        val overview: String?,
        val imageUrl: String?,
        val progress: Float,
        val played: Boolean = false,
        val isFavorite: Boolean = false,
    )

    data class UiState(
        val loading: Boolean = true,
        val kind: Kind = Kind.Other,
        val title: String = "",
        val logoUrl: String? = null,
        val backdropUrl: String? = null,
        val posterUrl: String? = null,
        // Meta estructurada (para la línea "2026 · 24m · 16 · ★ 7.9 · 1 temp. · géneros").
        val year: String? = null,
        val runtimeLabel: String? = null,
        val officialRating: String? = null,
        val rating: Double? = null,
        val seasonsLabel: String? = null,
        val genresLabel: String? = null,
        val overview: String? = null,
        val playTargetId: String? = null,
        val playLabel: String = "Reproducir",
        val isFavorite: Boolean = false,
        val played: Boolean = false,
        val seasons: List<SeasonTab> = emptyList(),
        /** Ids de TODOS los episodios de la serie (para "Reproducir aleatorio"). */
        val allEpisodeIds: List<String> = emptyList(),
        val selectedSeasonId: String? = null,
        val episodes: List<EpisodeItem> = emptyList(),
        val episodesLoading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var serverUrl: String = ""
    private var userId: String = ""

    init { load() }

    private fun load() {
        val session = sessionStore.session.value ?: run {
            _state.value = UiState(loading = false, error = "No hay sesión activa"); return
        }
        userId = session.userId
        serverUrl = session.serverUrl
        viewModelScope.launch {
            val item = runCatching { api.getItemDetail(userId, itemId) }.getOrNull() ?: run {
                _state.value = UiState(loading = false, error = "No se pudo cargar la ficha"); return@launch
            }
            val kind = when (item.type) {
                "Movie" -> Kind.Movie
                "Series" -> Kind.Series
                "Book", "AudioBook" -> Kind.Book
                else -> Kind.Other
            }
            var base = UiState(
                loading = false,
                kind = kind,
                title = item.name ?: "",
                logoUrl = imageUrl(item, "Logo", 480),
                backdropUrl = backdrop(item),
                posterUrl = imageUrl(item, "Primary", 400),
                year = item.productionYear?.toString(),
                runtimeLabel = item.runTimeTicks?.let { formatRuntime(it) },
                officialRating = item.officialRating,
                rating = item.communityRating,
                genresLabel = translateGenres(item.genres),
                overview = item.overview,
                isFavorite = item.userData?.isFavorite ?: false,
                played = item.userData?.played ?: false,
            )

            base = when (kind) {
                Kind.Series -> {
                    val seasons = runCatching { api.getSeasons(itemId, userId).items }.getOrDefault(emptyList())
                        .map { SeasonTab(it.id, it.name ?: "Temporada", it.childCount) }
                    // Lista completa de episodios de la serie: sirve de fallback para
                    // el botón Reproducir (S1E1 en series no empezadas) y alimenta el
                    // botón "Reproducir aleatorio".
                    val allEpisodes = runCatching { api.getSeriesEpisodes(itemId, userId).items }.getOrDefault(emptyList())
                    // NextUp viene vacío en series NO empezadas; en ese caso se cae
                    // al primer episodio (S1E1) para que el botón Reproducir siempre
                    // aparezca, como en la web.
                    val nextUp = runCatching { api.getSeriesNextUp(userId, itemId).items.firstOrNull() }.getOrNull()
                        ?: allEpisodes.firstOrNull()
                    val hasProgress = (nextUp?.userData?.playbackPositionTicks ?: 0) > 0
                    // Prefijo "T1:E1" como la web ("T1:E1 Reproducir").
                    val code = nextUp?.let { ep ->
                        val p = ep.parentIndexNumber
                        val i = ep.indexNumber
                        if (p != null && i != null) "T$p:E$i" else null
                    }
                    val verb = if (hasProgress || nextUp?.indexNumber?.let { it > 1 } == true) "Continuar" else "Reproducir"
                    base.copy(
                        seasons = seasons,
                        allEpisodeIds = allEpisodes.map { it.id },
                        selectedSeasonId = seasons.firstOrNull()?.id,
                        seasonsLabel = seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 temp." else "$it temps." },
                        playTargetId = nextUp?.id,
                        playLabel = listOfNotNull(code, verb).joinToString(" "),
                    )
                }
                Kind.Movie -> {
                    val hasProgress = (item.userData?.playbackPositionTicks ?: 0) > 0
                    base.copy(playTargetId = itemId, playLabel = if (hasProgress) "Continuar" else "Reproducir")
                }
                else -> base
            }
            _state.value = base
            base.selectedSeasonId?.let { loadEpisodes(it) }
        }
    }

    fun selectSeason(seasonId: String) {
        if (seasonId == _state.value.selectedSeasonId && _state.value.episodes.isNotEmpty()) return
        _state.update { it.copy(selectedSeasonId = seasonId) }
        loadEpisodes(seasonId)
    }

    private fun loadEpisodes(seasonId: String) {
        _state.update { it.copy(episodesLoading = true) }
        viewModelScope.launch {
            val eps = runCatching { api.getEpisodes(itemId, seasonId, userId).items }.getOrDefault(emptyList())
                .map { it.toEpisodeItem() }
            _state.update { it.copy(episodes = eps, episodesLoading = false) }
        }
    }

    private fun BaseItemDto.toEpisodeItem(): EpisodeItem {
        val num = indexNumber ?: 0
        // Como la web: "10 abr 2026 · 23m" (fecha de estreno · duración).
        val meta = listOfNotNull(
            formatDate(premiereDate),
            runTimeTicks?.let { formatRuntime(it) },
        ).joinToString(" · ")
        val progress = if ((runTimeTicks ?: 0) > 0)
            (userData?.playbackPositionTicks ?: 0).toFloat() / runTimeTicks!!.toFloat() else 0f
        return EpisodeItem(
            id = id,
            title = "$num. ${name ?: ""}",
            meta = meta,
            overview = overview,
            imageUrl = imageUrl(this, "Primary", 340),
            progress = progress.coerceIn(0f, 1f),
            played = userData?.played ?: false,
            isFavorite = userData?.isFavorite ?: false,
        )
    }

    // ---- Acciones (favorito / marcar visto) ----

    /** Un episodio al azar de toda la serie (para "Reproducir aleatorio"). */
    fun randomEpisodeId(): String? = _state.value.allEpisodeIds.randomOrNull()

    /** Refresca metadatos de la serie/película (para "¿Faltan temporadas?"). Best-effort (admin). */
    fun refreshMetadata() {
        viewModelScope.launch { runCatching { api.refreshItem(itemId) } }
    }

    fun toggleFavorite() {
        val fav = !_state.value.isFavorite
        _state.update { it.copy(isFavorite = fav) }
        viewModelScope.launch { runCatching { api.setFavorite(userId, itemId, fav) } }
    }

    fun toggleWatched() {
        val played = !_state.value.played
        _state.update { it.copy(played = played) }
        viewModelScope.launch { runCatching { api.setPlayed(userId, itemId, played) } }
    }

    fun toggleEpisodeFavorite(epId: String) {
        val ep = _state.value.episodes.firstOrNull { it.id == epId } ?: return
        val fav = !ep.isFavorite
        _state.update { s -> s.copy(episodes = s.episodes.map { if (it.id == epId) it.copy(isFavorite = fav) else it }) }
        viewModelScope.launch { runCatching { api.setFavorite(userId, epId, fav) } }
    }

    fun toggleEpisodeWatched(epId: String) {
        val ep = _state.value.episodes.firstOrNull { it.id == epId } ?: return
        val played = !ep.played
        _state.update { s ->
            s.copy(
                episodes = s.episodes.map {
                    if (it.id == epId) it.copy(played = played, progress = if (played) 0f else it.progress) else it
                },
            )
        }
        viewModelScope.launch { runCatching { api.setPlayed(userId, epId, played) } }
    }

    private fun imageUrl(item: BaseItemDto, type: String, width: Int): String? {
        val tag = item.imageTags?.get(type) ?: return if (type == "Primary") embyImageUrl(serverUrl, item.id, type, null, width) else null
        return embyImageUrl(serverUrl, item.id, type, tag, width)
    }

    private fun backdrop(item: BaseItemDto): String? {
        val tag = item.backdropImageTags?.firstOrNull()
        return if (tag != null) embyImageUrl(serverUrl, item.id, "Backdrop", tag, 1280)
        else imageUrl(item, "Primary", 1280)
    }

    private fun formatRuntime(ticks: Long): String {
        val min = (ticks / TICKS_PER_MIN).toInt()
        return if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
    }

    // "2026-04-10T..." → "10 abr 2026". Parseo manual para no meter una lib de
    // fechas en commonMain; si el formato no matchea, se devuelve null.
    private fun formatDate(iso: String?): String? {
        val date = iso?.take(10) ?: return null
        val parts = date.split("-")
        if (parts.size != 3) return null
        val year = parts[0]
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        val mName = months.getOrNull(month - 1) ?: return null
        return "$day $mName $year"
    }

    private fun translateGenres(genres: List<String>?): String? {
        val list = genres?.takeIf { it.isNotEmpty() } ?: return null
        return list.joinToString(", ") { GENRE_ES[it] ?: it }
    }

    companion object {
        // Emby devuelve los géneros en inglés; la web los muestra en español.
        private val GENRE_ES = mapOf(
            "Action" to "Acción", "Adventure" to "Aventura", "Animation" to "Animación",
            "Anime" to "Anime", "Comedy" to "Comedia", "Crime" to "Crimen",
            "Documentary" to "Documental", "Drama" to "Drama", "Family" to "Familia",
            "Fantasy" to "Fantasía", "History" to "Historia", "Horror" to "Terror",
            "Music" to "Música", "Musical" to "Musical", "Mystery" to "Misterio",
            "Romance" to "Romance", "Science Fiction" to "Ciencia ficción",
            "Sci-Fi & Fantasy" to "Ciencia ficción y fantasía", "Thriller" to "Suspenso",
            "War" to "Bélica", "War & Politics" to "Bélica y política", "Western" to "Western",
            "Kids" to "Infantil", "Reality" to "Reality", "Soap" to "Telenovela",
            "Talk" to "Talk show", "News" to "Noticias", "Suspense" to "Suspenso",
            "Sport" to "Deporte", "Supernatural" to "Sobrenatural",
        )
    }
}
