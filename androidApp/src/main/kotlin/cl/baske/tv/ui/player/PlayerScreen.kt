package cl.baske.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.theme.LocalAccent
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L

private enum class Zone { None, Back, Title, Seek, Buttons }
private enum class Transport { SeekBack, PlayPause, SeekFwd, Audio, Subtitles }
private enum class Panel { Audio, Subtitles }

private class PanelItem(val label: String, val active: Boolean, val onSelect: () -> Unit)

private val NAV_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.DirectionCenter, Key.Enter, Key.Spacebar,
    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.MediaFastForward, Key.MediaRewind,
)

/**
 * Reproducción y su estado viven acá, agnósticos del tipo de input. Lo único
 * que cambia entre Tv y touch es el overlay de controles (`TvPlayerOverlay` /
 * `TouchPlayerOverlay`), que se le pega encima llamando a las mismas acciones.
 */
@Composable
fun PlayerScreen(itemId: String, onExit: () -> Unit) {
    val viewModel = koinViewModel<PlayerViewModel>(key = itemId) { parametersOf(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val device = LocalDevice.current

    val libVlc = remember {
        // Sin --no-drop-late-frames: en hardware débil (Chromecast) dejamos que
        // VLC descarte frames atrasados para mantener la reproducción fluida en
        // vez de acumular retraso. --avcodec-fast permite atajos de decodificación
        // con impacto visual mínimo pero menos carga de CPU/GPU.
        LibVLC(context, arrayListOf("--avcodec-fast"))
    }
    val mediaPlayer = remember { MediaPlayer(libVlc) }

    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startReported by remember { mutableStateOf(false) }
    var resumeSeeked by remember { mutableStateOf(false) }
    var subtitleAdded by remember { mutableStateOf(false) }
    var currentSubUrl by remember { mutableStateOf<String?>(null) }
    var pendingSubUrl by remember { mutableStateOf<String?>(null) }
    val addedSubs = remember { mutableMapOf<String, Int>() }
    var playbackError by remember { mutableStateOf<String?>(null) }
    // true recién cuando libVLC emite el primer frame (Playing): hasta entonces
    // mostramos el loader en vez de dejar la pantalla en negro bufferendo.
    var videoStarted by remember { mutableStateOf(false) }

    val controls = remember(state.subtitles.isEmpty()) {
        buildList {
            add(Transport.SeekBack); add(Transport.PlayPause); add(Transport.SeekFwd)
            add(Transport.Audio)
            if (state.subtitles.isNotEmpty()) add(Transport.Subtitles)
        }
    }

    LaunchedEffect(state.streamUrl) {
        val url = state.streamUrl ?: return@LaunchedEffect
        val media = Media(libVlc, Uri.parse(url)).apply { setHWDecoderEnabled(true, false) }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    DisposableEffect(Unit) {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    videoStarted = true
                    val current = viewModel.state.value
                    if (!subtitleAdded && current.subtitleUrl != null) {
                        subtitleAdded = true
                        runCatching {
                            mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(current.subtitleUrl), true)
                        }
                        currentSubUrl = current.subtitleUrl
                        pendingSubUrl = current.subtitleUrl
                    }
                    if (!resumeSeeked) {
                        resumeSeeked = true
                        if (current.startPositionMs > 3_000) mediaPlayer.time = current.startPositionMs
                    }
                    if (!startReported) {
                        startReported = true
                        viewModel.reportStart(mediaPlayer.time)
                    }
                }
                MediaPlayer.Event.Paused -> {
                    isPlaying = false
                    viewModel.reportProgress(mediaPlayer.time, paused = true)
                }
                MediaPlayer.Event.EndReached -> onExit()
                MediaPlayer.Event.EncounteredError -> playbackError = "Error al reproducir el video"
            }
        }
        onDispose { mediaPlayer.setEventListener(null) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            positionMs = mediaPlayer.time
            durationMs = mediaPlayer.length
            // Captura el id del track de subtítulo recién agregado (para reusarlo).
            val pending = pendingSubUrl
            if (pending != null && mediaPlayer.spuTrack > 0) {
                addedSubs[pending] = mediaPlayer.spuTrack
                pendingSubUrl = null
            }
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            if (mediaPlayer.isPlaying) viewModel.reportProgress(mediaPlayer.time, paused = false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val finalMs = mediaPlayer.time
            viewModel.reportStopped(finalMs)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVlc.release()
        }
    }

    fun togglePlayPause() { if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play() }
    fun seekBy(deltaMs: Long) {
        val len = mediaPlayer.length
        val target = (mediaPlayer.time + deltaMs).coerceIn(0L, if (len > 0) len else Long.MAX_VALUE)
        mediaPlayer.time = target
        positionMs = target
    }
    fun seekToFraction(fraction: Float) {
        val len = mediaPlayer.length
        if (len <= 0) return
        val target = (fraction * len).toLong().coerceIn(0L, len)
        mediaPlayer.time = target
        positionMs = target
    }
    fun selectSubtitleUrl(url: String) {
        currentSubUrl = url
        val id = addedSubs[url]
        if (id != null && id > 0) {
            mediaPlayer.spuTrack = id
        } else {
            runCatching { mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(url), true) }
            pendingSubUrl = url
        }
    }
    fun disableSubtitle() { currentSubUrl = null; mediaPlayer.spuTrack = -1 }

    fun audioItems(): List<PanelItem> {
        val tracks = mediaPlayer.audioTracks ?: return emptyList()
        val current = mediaPlayer.audioTrack
        return tracks.map { td ->
            PanelItem(td.name ?: "Pista ${td.id}", td.id == current) { mediaPlayer.audioTrack = td.id }
        }
    }
    fun subtitleItems(): List<PanelItem> = buildList {
        add(PanelItem("Desactivado", currentSubUrl == null) { disableSubtitle() })
        state.subtitles.forEach { t -> add(PanelItem(t.label, currentSubUrl == t.url) { selectSubtitleUrl(t.url) }) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    mediaPlayer.attachViews(this, null, true, false)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if ((state.loading || !videoStarted) && playbackError == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalAccent.current)
            }
        }

        (playbackError ?: state.error)?.let { message ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(message, color = Color.White)
            }
        }

        if (device.isTv) {
            TvPlayerOverlay(
                title = state.title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                controls = controls,
                subsOn = currentSubUrl != null,
                onTogglePlayPause = ::togglePlayPause,
                onPlay = mediaPlayer::play,
                onPause = mediaPlayer::pause,
                onSeekBy = ::seekBy,
                audioItems = ::audioItems,
                subtitleItems = ::subtitleItems,
                onExit = onExit,
            )
        } else {
            TouchPlayerOverlay(
                title = state.title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                subsOn = currentSubUrl != null,
                hasSubtitles = state.subtitles.isNotEmpty(),
                onTogglePlayPause = ::togglePlayPause,
                onSeekBy = ::seekBy,
                onSeekToFraction = ::seekToFraction,
                audioItems = ::audioItems,
                subtitleItems = ::subtitleItems,
                onExit = onExit,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Overlay Tv: D-pad puro, sin puntero. El estado de foco/zona vive acá.
// ---------------------------------------------------------------------------

@Composable
private fun TvPlayerOverlay(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    controls: List<Transport>,
    subsOn: Boolean,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    audioItems: () -> List<PanelItem>,
    subtitleItems: () -> List<PanelItem>,
    onExit: () -> Unit,
) {
    var zone by remember { mutableStateOf(Zone.Buttons) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableIntStateOf(1) }
    var openPanel by remember { mutableStateOf<Panel?>(null) }
    var panelCursor by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val panelItems: List<PanelItem> = when (openPanel) {
        Panel.Audio -> audioItems()
        Panel.Subtitles -> subtitleItems()
        null -> emptyList()
    }

    // Auto-ocultar; no oculta mientras hay un panel abierto.
    LaunchedEffect(interactionTick) {
        delay(CONTROLS_TIMEOUT_MS)
        if (openPanel == null) zone = Zone.None
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler {
        when {
            openPanel != null -> openPanel = null
            zone != Zone.None -> zone = Zone.None
            else -> onExit()
        }
    }

    fun handleKeyUp(key: Key): Boolean {
        interactionTick++
        // Panel abierto: navegación propia.
        if (openPanel != null) {
            val last = (panelItems.size - 1).coerceAtLeast(0)
            when {
                key == Key.DirectionUp -> panelCursor = (panelCursor - 1).coerceAtLeast(0)
                key == Key.DirectionDown -> panelCursor = (panelCursor + 1).coerceAtMost(last)
                key == Key.DirectionCenter || key == Key.Enter || key == Key.Spacebar -> {
                    panelItems.getOrNull(panelCursor)?.onSelect(); openPanel = null
                }
                key == Key.DirectionLeft -> openPanel = null
            }
            return true
        }
        when (key) {
            Key.MediaPlayPause -> { onTogglePlayPause(); return true }
            Key.MediaPlay -> { onPlay(); return true }
            Key.MediaPause -> { onPause(); return true }
            Key.MediaFastForward -> { onSeekBy(SEEK_STEP_MS); zone = Zone.Seek; return true }
            Key.MediaRewind -> { onSeekBy(-SEEK_STEP_MS); zone = Zone.Seek; return true }
            else -> {}
        }
        val center = key == Key.DirectionCenter || key == Key.Enter || key == Key.Spacebar
        when (zone) {
            Zone.None -> when {
                key == Key.DirectionUp || key == Key.DirectionDown -> zone = Zone.Buttons
                key == Key.DirectionLeft -> { onSeekBy(-SEEK_STEP_MS); zone = Zone.Seek }
                key == Key.DirectionRight -> { onSeekBy(SEEK_STEP_MS); zone = Zone.Seek }
                center -> { onTogglePlayPause(); zone = Zone.Buttons }
            }
            Zone.Buttons -> when {
                key == Key.DirectionUp -> zone = Zone.Seek
                key == Key.DirectionDown -> zone = Zone.None
                key == Key.DirectionLeft -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                key == Key.DirectionRight -> selectedIndex = (selectedIndex + 1).coerceAtMost(controls.lastIndex)
                center -> when (controls[selectedIndex]) {
                    Transport.SeekBack -> onSeekBy(-SEEK_STEP_MS)
                    Transport.PlayPause -> onTogglePlayPause()
                    Transport.SeekFwd -> onSeekBy(SEEK_STEP_MS)
                    Transport.Audio -> { openPanel = Panel.Audio; panelCursor = 0 }
                    Transport.Subtitles -> { openPanel = Panel.Subtitles; panelCursor = 0 }
                }
            }
            Zone.Seek -> when {
                key == Key.DirectionUp -> zone = Zone.Title
                key == Key.DirectionDown -> zone = Zone.Buttons
                key == Key.DirectionLeft -> onSeekBy(-SEEK_STEP_MS)
                key == Key.DirectionRight -> onSeekBy(SEEK_STEP_MS)
                center -> zone = Zone.Buttons
            }
            Zone.Title -> when {
                key == Key.DirectionUp -> zone = Zone.Back
                key == Key.DirectionDown -> zone = Zone.Seek
            }
            Zone.Back -> when {
                key == Key.DirectionDown -> zone = Zone.Title
                center -> onExit()
            }
        }
        return true
    }

    val controlsVisible = zone != Zone.None || openPanel != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.key !in NAV_KEYS) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> true
                    KeyEventType.KeyUp -> handleKeyUp(event.key)
                    else -> false
                }
            },
    ) {
        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xC2000000), Color.Transparent)))
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                BackPill(focused = zone == Zone.Back)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            PlayerControls(
                title = title,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                controls = controls,
                selectedIndex = selectedIndex,
                subsOn = subsOn,
                zone = zone,
            )
        }

        openPanel?.let { panel ->
            PlayerPanel(
                title = if (panel == Panel.Audio) "Audio" else "Subtítulos",
                items = panelItems,
                cursor = panelCursor.coerceIn(0, (panelItems.size - 1).coerceAtLeast(0)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 150.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Overlay touch: tap para mostrar/ocultar, botones y slider arrastrable.
// ---------------------------------------------------------------------------

@Composable
private fun TouchPlayerOverlay(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    subsOn: Boolean,
    hasSubtitles: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekToFraction: (Float) -> Unit,
    audioItems: () -> List<PanelItem>,
    subtitleItems: () -> List<PanelItem>,
    onExit: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var openPanel by remember { mutableStateOf<Panel?>(null) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(interactionTick, controlsVisible, scrubbing) {
        if (controlsVisible && openPanel == null && !scrubbing) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    BackHandler {
        when {
            openPanel != null -> openPanel = null
            else -> onExit()
        }
    }

    val panelItems: List<PanelItem> = when (openPanel) {
        Panel.Audio -> audioItems()
        Panel.Subtitles -> subtitleItems()
        null -> emptyList()
    }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                controlsVisible = !controlsVisible
                interactionTick++
            },
    ) {
        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xC2000000), Color.Transparent)))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExit,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                    .padding(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(positionMs), color = Color.White, fontSize = 12.sp)
                    Slider(
                        value = if (scrubbing) scrubFraction else fraction,
                        onValueChange = {
                            scrubbing = true
                            scrubFraction = it
                            interactionTick++
                        },
                        onValueChangeFinished = {
                            onSeekToFraction(scrubFraction)
                            scrubbing = false
                            interactionTick++
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = LocalAccent.current,
                            activeTrackColor = LocalAccent.current,
                            inactiveTrackColor = Color(0x2EFFFFFF),
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                    Text(formatTime(durationMs), color = Color(0xB3FFFFFF), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TransportButton(Icons.Filled.Replay10, selected = false, onClick = { onSeekBy(-SEEK_STEP_MS); interactionTick++ })
                    TransportButton(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, selected = false, big = true,
                        onClick = { onTogglePlayPause(); interactionTick++ },
                    )
                    TransportButton(Icons.Filled.Forward10, selected = false, onClick = { onSeekBy(SEEK_STEP_MS); interactionTick++ })
                    Spacer(Modifier.weight(1f))
                    TransportButton(Icons.Filled.Audiotrack, selected = false, onClick = { openPanel = Panel.Audio; interactionTick++ })
                    if (hasSubtitles) {
                        TransportButton(
                            if (subsOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                            selected = false, active = subsOn,
                            onClick = { openPanel = Panel.Subtitles; interactionTick++ },
                        )
                    }
                }
            }
        }

        openPanel?.let { panel ->
            PlayerPanel(
                title = if (panel == Panel.Audio) "Audio" else "Subtítulos",
                items = panelItems,
                cursor = -1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 150.dp),
            )
        }
    }
}

@Composable
private fun BackPill(focused: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color(0xCC000000) else Color(0x85000000))
            .then(if (focused) Modifier.border(2.dp, Color(0xE6FFFFFF), RoundedCornerShape(50)) else Modifier)
            .padding(start = 12.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Volver", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    controls: List<Transport>,
    selectedIndex: Int,
    subsOn: Boolean,
    zone: Zone,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(start = 40.dp, end = 40.dp, top = 80.dp, bottom = 32.dp),
    ) {
        Text(
            title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1,
            textDecoration = if (zone == Zone.Title) TextDecoration.Underline else TextDecoration.None,
        )
        Spacer(Modifier.height(14.dp))

        val seekFocused = zone == Zone.Seek
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(positionMs), color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (seekFocused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x2EFFFFFF)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(if (seekFocused) 8.dp else 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(LocalAccent.current),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(formatTime(durationMs), color = Color(0xB3FFFFFF), fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))

        val buttonsFocused = zone == Zone.Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            controls.forEachIndexed { index, transport ->
                val selected = buttonsFocused && index == selectedIndex
                when (transport) {
                    Transport.SeekBack -> TransportButton(Icons.Filled.Replay10, selected)
                    Transport.PlayPause -> TransportButton(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, selected, big = true,
                    )
                    Transport.SeekFwd -> TransportButton(Icons.Filled.Forward10, selected)
                    Transport.Audio -> {
                        Spacer(Modifier.weight(1f))
                        TransportButton(Icons.Filled.Audiotrack, selected)
                    }
                    Transport.Subtitles -> TransportButton(
                        if (subsOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                        selected, active = subsOn,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerPanel(
    title: String,
    items: List<PanelItem>,
    cursor: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF71B1B1D))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        Text(
            title.uppercase(),
            color = Color(0x47FFFFFF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 8.dp),
        )
        val accent = LocalAccent.current
        Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            items.forEachIndexed { i, item ->
                val isCursor = i == cursor
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCursor) Color(0x1FFFFFFF) else Color.Transparent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = item.onSelect,
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.label,
                        modifier = Modifier.weight(1f),
                        color = when {
                            isCursor -> Color.White
                            item.active -> accent
                            else -> Color(0xB3FFFFFF)
                        },
                        fontSize = 13.sp,
                        fontWeight = if (isCursor || item.active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    if (item.active) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    selected: Boolean,
    big: Boolean = false,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val accent = LocalAccent.current
    val size = if (big) 60.dp else 48.dp
    val bg = when {
        selected && active -> accent.copy(alpha = 0.32f)
        selected -> Color(0x33FFFFFF)
        active -> accent.copy(alpha = 0.22f)
        else -> Color(0x17FFFFFF)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (selected) Modifier.border(2.dp, Color(0xE6FFFFFF), CircleShape)
                else Modifier.border(1.dp, Color(0x24FFFFFF), CircleShape),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) accent else Color.White,
            modifier = Modifier.size(if (big) 30.dp else 24.dp),
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    } else {
        "$m:${s.toString().padStart(2, '0')}"
    }
}
