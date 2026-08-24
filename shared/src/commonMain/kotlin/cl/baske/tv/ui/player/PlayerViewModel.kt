package cl.baske.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.MediaStream
import cl.baske.tv.data.model.PlaybackReport
import cl.baske.tv.data.remote.EmbyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TICKS_PER_MS = 10_000L
private val TEXT_SUB_CODECS = setOf("ass", "ssa", "srt", "subrip", "vtt", "webvtt")

@OptIn(ExperimentalUuidApi::class)
class PlayerViewModel(
    private val itemId: String,
    private val api: EmbyApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    data class SubtitleTrack(val label: String, val url: String)

    /** Un episodio de la serie para el panel de episodios del player. */
    data class EpisodeChoice(val id: String, val label: String, val current: Boolean)

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        /** Línea superior chica: "S02E08 · Título del cap" (solo episodios). */
        val subtitle: String? = null,
        val streamUrl: String? = null,
        /** URL del subtítulo por defecto (el que se auto-selecciona al arrancar). */
        val subtitleUrl: String? = null,
        /** Todos los subtítulos externos disponibles (para el panel). */
        val subtitles: List<SubtitleTrack> = emptyList(),
        val startPositionMs: Long = 0,
        /** Ventana de intro (ms) para "Saltar intro". introEndMs=0 → sin intro. */
        val introStartMs: Long = 0,
        val introEndMs: Long = 0,
        /** Id del episodio siguiente (auto-siguiente + botón). null si no aplica. */
        val nextEpisodeId: String? = null,
        /** Etiqueta corta del siguiente episodio (ej. "T2 · E3 · Título"). */
        val nextEpisodeLabel: String? = null,
        /** Episodios de la serie (para el panel), vacío si no es un episodio. */
        val episodes: List<EpisodeChoice> = emptyList(),
        /** Canal de Live TV: sin barra de progreso/seek, sin intro/siguiente. */
        val isLive: Boolean = false,
        /** Calidad elegida (maxBitrate en bps); null = Auto (direct-play si se puede). */
        val qualityBitrate: Int? = null,
        /** Altura del video de la fuente (para el selector: oculta "Original" si >1080). */
        val sourceHeight: Int? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Se fijan tras PlaybackInfo; los reportes usan estos valores reales.
    private var mediaSourceId: String = itemId
    private var playSessionId: String = Uuid.random().toString().replace("-", "")
    // Live TV: id del stream abierto, para cerrarlo (liberar el tuner) al salir.
    private var liveStreamId: String? = null
    // Fallback: si el direct-play falló, reabrimos forzando transcode (una vez).
    private var forceTranscode = false
    // Calidad manual elegida por el usuario (maxBitrate bps); null = Auto.
    private var maxBitrate: Int? = null

    init { load() }

    /**
     * Reintenta la reproducción re-abriendo la fuente (útil en Live TV: VLC emite
     * EndReached/Error cuando el transcode del canal se queda sin segmentos por un
     * momento; re-abrir el live stream da una TranscodingUrl fresca y sigue).
     */
    fun reload() {
        liveStreamId?.let { id -> viewModelScope.launch { runCatching { api.closeLiveStream(id) } } }
        load()
    }

    /**
     * El direct-play falló (equipo que declara un decoder que no puede con el
     * archivo). Reabre forzando transcode. Solo una vez (si el transcode también
     * falla, no reintenta en loop). Devuelve false si ya se había forzado.
     */
    fun retryWithTranscode(): Boolean {
        if (forceTranscode) return false
        forceTranscode = true
        load()
        return true
    }

    /** Selector de calidad: null = Auto; si no, transcodifica a ese bitrate (bps). */
    fun setQuality(bitrate: Int?) {
        if (maxBitrate == bitrate) return
        maxBitrate = bitrate
        load()
    }

    private fun load() {
        val session = sessionStore.session.value ?: run {
            _state.value = UiState(loading = false, error = "No hay sesión activa")
            return
        }
        viewModelScope.launch {
            // Primero el detalle (barato) para saber si es un canal de Live TV: eso
            // decide si a PlaybackInfo le pedimos transcode progresivo (.ts) en vez
            // de HLS. Recién después PlaybackInfo (con el flag correcto).
            val item = runCatching { api.getItem(session.userId, itemId) }.getOrNull()
            val liveChannel = item?.type == "TvChannel"
            val playbackInfo = runCatching {
                api.getPlaybackInfo(itemId, session.userId, live = liveChannel, forceTranscode = forceTranscode, maxBitrate = maxBitrate)
            }.getOrNull()
            val source = playbackInfo?.mediaSources?.firstOrNull()

            if (source == null) {
                _state.value = UiState(loading = false, error = "El servidor no entregó una fuente reproducible")
                return@launch
            }

            mediaSourceId = source.id
            playSessionId = playbackInfo.playSessionId ?: playSessionId
            liveStreamId = source.liveStreamId
            // Canal de Live TV: stream infinito (abierto con AutoOpenLiveStream) →
            // sin resume, sin intro, sin siguiente episodio, sin barra de progreso.
            val isLive = item?.type == "TvChannel" || source.isInfiniteStream

            // Intro (Emby marca IntroStart/IntroEnd en Chapters) → botón "Saltar intro".
            val chapters = item?.chapters.orEmpty()
            val introEndTicks = chapters.firstOrNull { it.markerType == "IntroEnd" }?.startPositionTicks ?: 0
            val introStartTicks = chapters.firstOrNull { it.markerType == "IntroStart" }?.startPositionTicks ?: 0
            val introStartMs = if (introEndTicks > 0) introStartTicks / TICKS_PER_MS else 0
            val introEndMs = if (introEndTicks > 0) introEndTicks / TICKS_PER_MS else 0

            // Solo subtítulos externos de texto (el usuario no usa embebidos).
            val subStreams = source.mediaStreams
                .filter { it.type == "Subtitle" && it.isExternal && it.codec in TEXT_SUB_CODECS }
            val subtitles = subStreams.map {
                SubtitleTrack(
                    label = subtitleLabel(it),
                    url = api.buildSubtitleUrl(itemId, mediaSourceId, it.index, subtitleFormat(it)),
                )
            }
            val default = subStreams.firstOrNull { it.isDefault } ?: subStreams.firstOrNull()

            // Si el server marcó que esta fuente necesita transcode para este
            // dispositivo, devuelve una TranscodingUrl (HLS) → la usamos en vez del
            // archivo crudo (static). Si no, direct-play como siempre.
            val streamUrl = source.transcodingUrl
                ?.let { api.resolveTranscodeUrl(it) }
                ?: api.buildStreamUrl(itemId, mediaSourceId, playSessionId)

            // Episodio → título = serie; subtítulo = "S02E08 · nombre del cap".
            val isEpisode = item?.type == "Episode"
            val titleText = if (isEpisode) (item?.seriesName ?: item?.name ?: "") else (item?.name ?: source.name ?: "")
            val subtitleText = if (isEpisode) {
                val code = listOfNotNull(
                    item?.parentIndexNumber?.let { "S" + it.toString().padStart(2, '0') },
                    item?.indexNumber?.let { "E" + it.toString().padStart(2, '0') },
                ).joinToString("")
                listOfNotNull(code.ifEmpty { null }, item?.name).joinToString(" · ").ifEmpty { null }
            } else null

            _state.value = UiState(
                loading = false,
                title = titleText,
                subtitle = subtitleText,
                streamUrl = streamUrl,
                subtitleUrl = default?.let {
                    api.buildSubtitleUrl(itemId, mediaSourceId, it.index, subtitleFormat(it))
                },
                subtitles = subtitles,
                startPositionMs = if (isLive) 0 else (item?.userData?.playbackPositionTicks ?: 0) / TICKS_PER_MS,
                introStartMs = introStartMs,
                introEndMs = introEndMs,
                isLive = isLive,
                qualityBitrate = maxBitrate,
                sourceHeight = source.mediaStreams?.firstOrNull { it.type == "Video" }?.height,
            )

            // Si esto es un episodio, traer la serie completa (en segundo plano,
            // para no demorar el arranque): sirve para el auto-siguiente y para
            // el panel de episodios.
            if (item?.type == "Episode" && item.seriesId != null) {
                val eps = runCatching { api.getSeriesEpisodes(item.seriesId!!, session.userId) }
                    .getOrNull()?.items.orEmpty()
                val idx = eps.indexOfFirst { it.id == itemId }
                val next = if (idx >= 0) eps.getOrNull(idx + 1) else null
                _state.value = _state.value.copy(
                    nextEpisodeId = next?.id,
                    nextEpisodeLabel = next?.let { episodeLabel(it) },
                    episodes = eps.map { EpisodeChoice(it.id, episodeLabel(it), it.id == itemId) },
                )
            }
        }
    }

    private fun episodeLabel(ep: cl.baske.tv.data.model.BaseItemDto): String {
        val season = ep.parentIndexNumber?.let { "T$it" }
        val number = ep.indexNumber?.let { "E$it" }
        val code = listOfNotNull(season, number).joinToString("·")
        return listOfNotNull(code.ifEmpty { null }, ep.name).joinToString(" · ")
    }

    private fun subtitleLabel(stream: MediaStream): String =
        stream.displayTitle ?: stream.language ?: "Subtítulo ${stream.index}"

    private fun subtitleFormat(stream: MediaStream): String = when (stream.codec?.lowercase()) {
        "ass", "ssa" -> "ass"
        "srt", "subrip" -> "srt"
        else -> "vtt"
    }

    fun reportStart(positionMs: Long) = report("/Sessions/Playing", positionMs, paused = false)
    fun reportProgress(positionMs: Long, paused: Boolean) = report("/Sessions/Playing/Progress", positionMs, paused)
    fun reportStopped(positionMs: Long) {
        report("/Sessions/Playing/Stopped", positionMs, paused = false)
        // Live TV: cerrar el stream para liberar el tuner del servidor.
        liveStreamId?.let { id ->
            viewModelScope.launch { runCatching { api.closeLiveStream(id) } }
        }
    }

    private fun report(path: String, positionMs: Long, paused: Boolean) {
        // Fire-and-forget: un reporte fallido no debe romper la reproducción.
        viewModelScope.launch {
            runCatching {
                api.reportPlayback(
                    path,
                    PlaybackReport(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        playSessionId = playSessionId,
                        positionTicks = positionMs * TICKS_PER_MS,
                        isPaused = paused,
                    ),
                )
            }
        }
    }
}
