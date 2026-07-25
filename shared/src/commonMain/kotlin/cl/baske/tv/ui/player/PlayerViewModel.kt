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

    data class UiState(
        val loading: Boolean = true,
        val title: String = "",
        val streamUrl: String? = null,
        /** URL del subtítulo por defecto (el que se auto-selecciona al arrancar). */
        val subtitleUrl: String? = null,
        /** Todos los subtítulos externos disponibles (para el panel). */
        val subtitles: List<SubtitleTrack> = emptyList(),
        val startPositionMs: Long = 0,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Se fijan tras PlaybackInfo; los reportes usan estos valores reales.
    private var mediaSourceId: String = itemId
    private var playSessionId: String = Uuid.random().toString().replace("-", "")

    init { load() }

    private fun load() {
        val session = sessionStore.session.value ?: run {
            _state.value = UiState(loading = false, error = "No hay sesión activa")
            return
        }
        viewModelScope.launch {
            // Detalle (título + posición de resume) y PlaybackInfo en secuencia.
            val item = runCatching { api.getItem(session.userId, itemId) }.getOrNull()
            val playbackInfo = runCatching { api.getPlaybackInfo(itemId, session.userId) }.getOrNull()
            val source = playbackInfo?.mediaSources?.firstOrNull()

            if (source == null) {
                _state.value = UiState(loading = false, error = "El servidor no entregó una fuente reproducible")
                return@launch
            }

            mediaSourceId = source.id
            playSessionId = playbackInfo.playSessionId ?: playSessionId

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

            _state.value = UiState(
                loading = false,
                title = item?.name ?: source.name ?: "",
                streamUrl = api.buildStreamUrl(itemId, mediaSourceId, playSessionId),
                subtitleUrl = default?.let {
                    api.buildSubtitleUrl(itemId, mediaSourceId, it.index, subtitleFormat(it))
                },
                subtitles = subtitles,
                startPositionMs = (item?.userData?.playbackPositionTicks ?: 0) / TICKS_PER_MS,
            )
        }
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
    fun reportStopped(positionMs: Long) = report("/Sessions/Playing/Stopped", positionMs, paused = false)

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
