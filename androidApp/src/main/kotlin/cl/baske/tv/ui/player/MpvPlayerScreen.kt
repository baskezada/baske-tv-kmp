package cl.baske.tv.ui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Pantalla de reproducción con **mpv** (motor alternativo, seleccionable en
 * Ajustes). Reusa el mismo overlay/controles y el mismo [PlayerViewModel] que la
 * versión de libVLC — solo cambia el motor. mpv descomprime HTTP bien, así que
 * reproduce la URL de Emby directo (sin el HLS-rewrite de VLC) y renderiza ASS
 * con libass integrado.
 */
@Composable
fun MpvPlayerScreen(itemId: String, onExit: () -> Unit, onPlayItem: (String) -> Unit) {
    val viewModel = koinViewModel<PlayerViewModel>(key = itemId) { parametersOf(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val device = LocalDevice.current
    val context = LocalContext.current

    ImmersiveFullscreen()

    val mpv = remember { MpvPlayer(context).apply { init() } }

    val positionMs = mpv.positionMs
    val durationMs = mpv.durationMs
    val isPlaying = !mpv.paused
    val videoStarted = mpv.videoReady

    var subtitleAdded by remember { mutableStateOf(false) }
    var currentSubUrl by remember { mutableStateOf<String?>(null) }
    var startReported by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var liveRetries by remember { mutableIntStateOf(0) }
    var statsVisible by remember { mutableStateOf(false) }
    val tvOverlayFocus = remember { FocusRequester() }

    fun playNextOrExit() { state.nextEpisodeId?.let(onPlayItem) ?: onExit() }

    // Cargar el stream (mpv reproduce la URL directo — sin rewrite). Resume via start=.
    LaunchedEffect(state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        subtitleAdded = false
        mpv.loadFile(url, state.startPositionMs)
    }

    // Subtítulo externo por defecto (ASS): mpv lo renderiza con libass.
    LaunchedEffect(videoStarted, state.subtitleUrl) {
        if (videoStarted && !subtitleAdded) {
            state.subtitleUrl?.let { mpv.addSubtitle(it); currentSubUrl = it }
            subtitleAdded = true
        }
    }

    // reportStart al primer frame; progreso cada 10s.
    LaunchedEffect(videoStarted) {
        if (videoStarted && !startReported) { startReported = true; viewModel.reportStart(positionMs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (!mpv.paused) viewModel.reportProgress(mpv.positionMs, paused = false)
        }
    }

    // Fin del archivo: live = re-abrir; VOD = siguiente episodio o salir.
    LaunchedEffect(mpv.ended) {
        if (!mpv.ended) return@LaunchedEffect
        if (state.isLive) {
            if (liveRetries < 8) { liveRetries++; viewModel.reload() } else onExit()
        } else playNextOrExit()
    }

    // Fallback a transcode: si un direct-play no muestra frame de video (equipo que
    // "acepta" el códec pero no lo decodifica), reabrir forzando transcode.
    LaunchedEffect(state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        if (state.isLive || url.contains(".m3u8", ignoreCase = true)) return@LaunchedEffect
        var waited = 0
        while (waited < 12_000) {
            delay(500); waited += 500
            if (videoStarted) return@LaunchedEffect
        }
        if (!mpv.videoReady) viewModel.retryWithTranscode()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.reportStopped(mpv.positionMs)
            mpv.destroy()
        }
    }

    fun seekBy(deltaMs: Long) = mpv.seekBy(deltaMs)
    fun seekToFraction(fraction: Float) { if (durationMs > 0) mpv.seekTo((fraction * durationMs).toLong()) }

    fun audioItems(): List<PanelItem> = buildList {
        for (i in 0 until mpv.trackCount()) {
            if (mpv.trackType(i) != "audio") continue
            val id = mpv.trackId(i)
            add(PanelItem(mpv.trackTitle(i) ?: "Pista $id", mpv.trackSelected(i)) { mpv.setAudioTrack(id) })
        }
    }
    fun subtitleItems(): List<PanelItem> = buildList {
        add(PanelItem("Desactivado", currentSubUrl == null) { currentSubUrl = null; mpv.disableSubtitle() })
        state.subtitles.forEach { t ->
            add(PanelItem(t.label, currentSubUrl == t.url) { currentSubUrl = t.url; mpv.addSubtitle(t.url) })
        }
    }
    fun episodeItems(): List<PanelItem> =
        state.episodes.map { ep -> PanelItem(ep.label, ep.current) { if (!ep.current) onPlayItem(ep.id) } }

    val hasNext = state.nextEpisodeId != null
    val hasEpisodes = state.episodes.isNotEmpty()
    val controls = remember(state.isLive, state.subtitles.isEmpty(), hasNext, hasEpisodes) {
        buildList {
            if (state.isLive) {
                add(Transport.PlayPause)
            } else {
                add(Transport.SeekBack); add(Transport.PlayPause); add(Transport.SeekFwd)
                if (hasNext) add(Transport.NextEp)
                add(Transport.Audio)
                if (state.subtitles.isNotEmpty()) add(Transport.Subtitles)
                if (hasEpisodes) add(Transport.Episodes)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {}
                        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, ht: Int) {
                            mpv.attachSurface(h.surface, w, ht)
                            mpv.play()
                        }
                        override fun surfaceDestroyed(h: SurfaceHolder) { mpv.detachSurface() }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        val terminalError = playbackError ?: state.error
        if ((state.loading || !videoStarted) && terminalError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAccent.current)
            }
        }

        when {
            terminalError != null -> PlayerErrorScreen(message = terminalError, onExit = onExit)
            device.isTv -> TvPlayerOverlay(
                title = state.title,
                isPlaying = isPlaying,
                isLive = state.isLive,
                positionMs = positionMs,
                durationMs = durationMs,
                controls = controls,
                subsOn = currentSubUrl != null,
                onTogglePlayPause = mpv::togglePlayPause,
                onPlay = mpv::play,
                onPause = mpv::pause,
                onSeekBy = ::seekBy,
                onNext = { state.nextEpisodeId?.let(onPlayItem) },
                audioItems = ::audioItems,
                subtitleItems = ::subtitleItems,
                episodeItems = ::episodeItems,
                onExit = onExit,
                focus = tvOverlayFocus,
            )
            else -> TouchPlayerOverlay(
                title = state.title,
                subtitle = state.subtitle,
                isPlaying = isPlaying,
                isLive = state.isLive,
                positionMs = positionMs,
                durationMs = durationMs,
                subsOn = currentSubUrl != null,
                hasSubtitles = state.subtitles.isNotEmpty(),
                hasNext = hasNext,
                hasEpisodes = hasEpisodes,
                onTogglePlayPause = mpv::togglePlayPause,
                onSeekBy = ::seekBy,
                onSeekToFraction = ::seekToFraction,
                onNext = { state.nextEpisodeId?.let(onPlayItem) },
                audioItems = ::audioItems,
                subtitleItems = ::subtitleItems,
                episodeItems = ::episodeItems,
                settingsItems = {
                    qualitySettingsItems(
                        current = state.qualityBitrate,
                        sourceHeight = state.sourceHeight,
                        statsOn = statsVisible,
                        onToggleStats = { statsVisible = !statsVisible },
                        onPickQuality = { viewModel.setQuality(it) },
                    )
                },
                statsVisible = statsVisible,
                statsLines = { mpv.stats() },
                onExit = onExit,
            )
        }
    }

    if (!device.isTv) BackHandler { onExit() }
}
