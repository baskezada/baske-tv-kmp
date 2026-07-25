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

    data class SeasonTab(val id: String, val name: String)
    data class EpisodeItem(
        val id: String,
        val title: String,
        val meta: String,
        val overview: String?,
        val imageUrl: String?,
        val progress: Float,
    )

    data class UiState(
        val loading: Boolean = true,
        val kind: Kind = Kind.Other,
        val title: String = "",
        val logoUrl: String? = null,
        val backdropUrl: String? = null,
        val posterUrl: String? = null,
        val meta: String = "",
        val genres: String? = null,
        val overview: String? = null,
        val playTargetId: String? = null,
        val playLabel: String = "Reproducir",
        val seasons: List<SeasonTab> = emptyList(),
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
                meta = buildMeta(item),
                genres = item.genres?.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                overview = item.overview,
            )

            base = when (kind) {
                Kind.Series -> {
                    val seasons = runCatching { api.getSeasons(itemId, userId).items }.getOrDefault(emptyList())
                        .map { SeasonTab(it.id, it.name ?: "Temporada") }
                    val nextUp = runCatching { api.getSeriesNextUp(userId, itemId).items.firstOrNull() }.getOrNull()
                    val hasProgress = (nextUp?.userData?.playbackPositionTicks ?: 0) > 0
                    base.copy(
                        seasons = seasons,
                        selectedSeasonId = seasons.firstOrNull()?.id,
                        playTargetId = nextUp?.id,
                        playLabel = if (hasProgress || nextUp?.indexNumber?.let { it > 1 } == true) "Continuar" else "Reproducir",
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
        val runtimeMin = runTimeTicks?.let { (it / TICKS_PER_MIN).toInt() }
        val meta = listOfNotNull(
            "E$num",
            runtimeMin?.let { "${it}min" },
            communityRating?.let { "★ ${formatRating(it)}" },
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
        )
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

    private fun buildMeta(item: BaseItemDto): String {
        val runtimeMin = item.runTimeTicks?.let { (it / TICKS_PER_MIN).toInt() }
        val runtime = runtimeMin?.let { if (it >= 60) "${it / 60}h ${it % 60}min" else "${it}min" }
        return listOfNotNull(
            item.productionYear?.toString(),
            item.officialRating,
            runtime,
            item.communityRating?.let { "★ ${formatRating(it)}" },
        ).joinToString("  ·  ")
    }

    private fun formatRating(r: Double): String = ((r * 10).toInt() / 10.0).toString()
}
