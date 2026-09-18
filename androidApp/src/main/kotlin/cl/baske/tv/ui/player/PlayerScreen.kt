package cl.baske.tv.ui.player

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.theme.LocalAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private const val SEEK_STEP_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L

/**
 * Paso de seek según cuánto se mantiene presionado el D-pad (repeatCount de los
 * KeyDown repetidos): un toque = 10s; al mantener, acelera a 30s y luego 60s.
 */
private fun seekStepForHold(repeat: Int): Long = when {
    repeat < 4 -> SEEK_STEP_MS
    repeat < 10 -> 30_000L
    else -> 60_000L
}

private enum class Zone { None, Back, Title, Seek, Buttons }
internal enum class Transport { SeekBack, PlayPause, SeekFwd, NextEp, Audio, Subtitles, Episodes, Settings }
internal enum class Panel { Audio, Subtitles, Episodes, Settings, Quality }

internal class PanelItem(
    val label: String,
    val active: Boolean,
    /** Si != null, seleccionar el ítem ABRE ese subpanel en vez de aplicar+cerrar. */
    val submenu: Panel? = null,
    val onSelect: () -> Unit = {},
)

private val NAV_KEYS = setOf(
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
    Key.DirectionCenter, Key.Enter, Key.Spacebar,
    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause, Key.MediaFastForward, Key.MediaRewind,
)


// ---------------------------------------------------------------------------
// Overlay Tv: D-pad puro, sin puntero. El estado de foco/zona vive acá.
// ---------------------------------------------------------------------------

@Composable
internal fun TvPlayerOverlay(
    title: String,
    subtitle: String?,
    channelNumber: String? = null,
    isPlaying: Boolean,
    isLive: Boolean,
    positionMs: Long,
    durationMs: Long,
    controls: List<Transport>,
    subsOn: Boolean,
    onTogglePlayPause: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevChannel: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
    audioItems: () -> List<PanelItem>,
    subtitleItems: () -> List<PanelItem>,
    episodeItems: () -> List<PanelItem>,
    settingsItems: () -> List<PanelItem>,
    qualityItems: () -> List<PanelItem>,
    statsVisible: Boolean,
    statsLines: () -> List<Pair<String, String>>,
    onExit: () -> Unit,
    focus: FocusRequester,
) {
    // "Player" es la zona primaria: OK=play/pausa, ←/→=seek. En vivo no hay seek,
    // así que la zona primaria es directamente la fila de botones.
    val playerZone = if (isLive) Zone.Buttons else Zone.Seek
    // VOD: empieza con los controles OCULTOS (video limpio). Live TV: empieza
    // MOSTRANDO los controles (para ver qué canal es al abrir/cambiar); se auto-ocultan.
    var zone by remember { mutableStateOf(if (isLive) Zone.Buttons else Zone.None) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableIntStateOf(1) }
    var openPanel by remember { mutableStateOf<Panel?>(null) }
    var panelCursor by remember { mutableIntStateOf(0) }

    // Feedback visual (como la web): destello central de play/pausa y destello
    // lateral de seek. Sirven sobre todo con los controles ocultos.
    var ppPlaying by remember { mutableStateOf(false) }
    var ppTick by remember { mutableIntStateOf(0) }
    var ppVisible by remember { mutableStateOf(false) }
    LaunchedEffect(ppTick) { if (ppTick == 0) return@LaunchedEffect; ppVisible = true; delay(500); ppVisible = false }
    // Acumulado de la ráfaga de seek (ms, con signo). El badge muestra +N/−N y se
    // resetea cuando la ráfaga termina (el badge se desvanece).
    var seekAccumMs by remember { mutableLongStateOf(0L) }
    var seekFbTick by remember { mutableIntStateOf(0) }
    var seekFbVisible by remember { mutableStateOf(false) }
    LaunchedEffect(seekFbTick) { if (seekFbTick == 0) return@LaunchedEffect; seekFbVisible = true; delay(800); seekFbVisible = false }

    val panelItems: List<PanelItem> = when (openPanel) {
        Panel.Audio -> audioItems()
        Panel.Subtitles -> subtitleItems()
        Panel.Episodes -> episodeItems()
        Panel.Settings -> settingsItems()
        Panel.Quality -> qualityItems()
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
            openPanel == Panel.Quality -> openPanel = Panel.Settings
            openPanel != null -> openPanel = null
            zone != Zone.None -> zone = Zone.None
            else -> onExit()
        }
    }

    // Play/pausa que oculta al REPRODUCIR (como en el táctil): al pausar deja los
    // controles visibles; al reanudar los oculta al toque para ver el video limpio.
    fun playPauseHide() {
        val resuming = !isPlaying
        onTogglePlayPause()
        ppPlaying = resuming; ppTick++            // destello central play/pausa
        if (resuming) zone = Zone.None
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
                    val item = panelItems.getOrNull(panelCursor)
                    if (item?.submenu != null) { openPanel = item.submenu; panelCursor = 0 }
                    else { item?.onSelect(); openPanel = null }
                }
                // ← vuelve al menú padre desde un submenú; si no, cierra.
                key == Key.DirectionLeft -> openPanel = if (openPanel == Panel.Quality) Panel.Settings else null
            }
            return true
        }
        // Live TV: arriba/abajo = cambiar de canal; OK = mostrar/ocultar los
        // controles (sin play/pausa). Sin seek ni fila de botones.
        if (isLive) {
            when {
                key == Key.DirectionUp -> onPrevChannel?.invoke()
                key == Key.DirectionDown -> onNextChannel?.invoke()
                key == Key.DirectionCenter || key == Key.Enter || key == Key.Spacebar ->
                    zone = if (zone == Zone.None) Zone.Buttons else Zone.None
            }
            return true
        }
        when (key) {
            Key.MediaPlayPause -> { playPauseHide(); return true }
            Key.MediaPlay -> { onPlay(); ppPlaying = true; ppTick++; zone = Zone.None; return true }
            Key.MediaPause -> { onPause(); ppPlaying = false; ppTick++; return true }
            Key.MediaFastForward -> { onSeekBy(SEEK_STEP_MS); if (!seekFbVisible) seekAccumMs = 0L; seekAccumMs += SEEK_STEP_MS; seekFbTick++; return true }
            Key.MediaRewind -> { onSeekBy(-SEEK_STEP_MS); if (!seekFbVisible) seekAccumMs = 0L; seekAccumMs -= SEEK_STEP_MS; seekFbTick++; return true }
            else -> {}
        }
        val center = key == Key.DirectionCenter || key == Key.Enter || key == Key.Spacebar
        when (zone) {
            // Controles ocultos: OK solo alterna play/pausa (con destello central).
            // ABAJO va directo al botón play/pausa de la fila; ARRIBA revela el player.
            Zone.None -> when {
                center -> {
                    val resuming = !isPlaying
                    onTogglePlayPause()
                    ppPlaying = resuming; ppTick++
                }
                key == Key.DirectionDown -> {
                    selectedIndex = controls.indexOfFirst { it == Transport.PlayPause }.coerceAtLeast(0)
                    zone = Zone.Buttons
                }
                else -> zone = playerZone
            }
            // "Player": OK = play/pausa, ↑ = Volver, ↓ = fila de botones.
            // (←/→ = seek se manejan en KeyDown, con aceleración al mantener.)
            Zone.Seek -> when {
                key == Key.DirectionDown -> zone = Zone.Buttons
                center -> playPauseHide()
            }
            Zone.Buttons -> when {
                key == Key.DirectionUp -> zone = if (isLive) Zone.Buttons else Zone.Seek
                key == Key.DirectionDown -> zone = Zone.None
                key == Key.DirectionLeft -> selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                key == Key.DirectionRight -> selectedIndex = (selectedIndex + 1).coerceAtMost(controls.lastIndex)
                center -> when (controls.getOrNull(selectedIndex)) {
                    Transport.SeekBack -> onSeekBy(-SEEK_STEP_MS)
                    Transport.PlayPause -> playPauseHide()
                    Transport.SeekFwd -> onSeekBy(SEEK_STEP_MS)
                    Transport.NextEp -> onNext()
                    Transport.Audio -> { openPanel = Panel.Audio; panelCursor = 0 }
                    Transport.Subtitles -> { openPanel = Panel.Subtitles; panelCursor = 0 }
                    Transport.Episodes -> {
                        openPanel = Panel.Episodes
                        // Abrir el panel posicionado en el episodio actual.
                        panelCursor = episodeItems().indexOfFirst { it.active }.coerceAtLeast(0)
                    }
                    Transport.Settings -> { openPanel = Panel.Settings; panelCursor = 0 }
                    null -> {}
                }
            }
            // Zonas ya no usadas (se salía por Volver; ahora se sale con el botón físico Atrás).
            Zone.Back, Zone.Title -> {}
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
                val isSeekKey = event.key == Key.DirectionLeft || event.key == Key.DirectionRight
                // ←/→ hacen seek cuando estamos en el "player" (no en la fila de
                // botones, no en vivo, sin panel). Se procesa en KeyDown para que al
                // MANTENER presionado acelere (repeatCount crece).
                val seekContext = openPanel == null && !isLive &&
                    (zone == Zone.Seek || zone == Zone.None)
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (isSeekKey && seekContext) {
                            interactionTick++
                            val step = seekStepForHold(event.nativeKeyEvent.repeatCount)
                            val delta = if (event.key == Key.DirectionLeft) -step else step
                            onSeekBy(delta)
                            // Solo seek + destello lateral (acumulado); NO abre los controles.
                            if (!seekFbVisible) seekAccumMs = 0L      // nueva ráfaga
                            seekAccumMs += delta
                            seekFbTick++
                        }
                        true
                    }
                    // El seek ya se hizo en KeyDown → no repetir en KeyUp.
                    KeyEventType.KeyUp -> if (isSeekKey && seekContext) true else handleKeyUp(event.key)
                    else -> false
                }
            },
    ) {
        // Destellos de feedback (como la web): laterales de seek + central de play/pausa.
        // Independientes de los controles → se ven aunque estén ocultos.
        AnimatedVisibility(
            visible = seekFbVisible && seekAccumMs < 0, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) { SeekFeedback(seekAccumMs) }
        AnimatedVisibility(
            visible = seekFbVisible && seekAccumMs > 0, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { SeekFeedback(seekAccumMs) }
        AnimatedVisibility(
            visible = ppVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) { CenterPlayPauseFeedback(playing = ppPlaying) }

        // Stats for nerds (se prende/apaga desde Ajustes), arriba-izquierda.
        if (statsVisible) {
            StatsOverlay(
                lines = statsLines,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 32.dp, top = 24.dp),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                channelNumber = channelNumber,
                isPlaying = isPlaying,
                isLive = isLive,
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
                title = when (panel) { Panel.Audio -> "Audio"; Panel.Subtitles -> "Subtítulos"; Panel.Episodes -> "Episodios"; Panel.Settings -> "Ajustes"; Panel.Quality -> "Calidad" },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TouchPlayerOverlay(
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    isLive: Boolean,
    positionMs: Long,
    durationMs: Long,
    subsOn: Boolean,
    hasSubtitles: Boolean,
    hasNext: Boolean,
    hasEpisodes: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onNext: () -> Unit,
    audioItems: () -> List<PanelItem>,
    subtitleItems: () -> List<PanelItem>,
    episodeItems: () -> List<PanelItem>,
    settingsItems: () -> List<PanelItem>,
    qualityItems: () -> List<PanelItem>,
    statsVisible: Boolean,
    statsLines: () -> List<Pair<String, String>>,
    onExit: () -> Unit,
) {
    var controlsVisible by remember { mutableStateOf(false) }   // empieza oculto
    var openPanel by remember { mutableStateOf<Panel?>(null) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    // Ancho del overlay (para saber en qué tercio cayó el doble-tap).
    var overlayWidth by remember { mutableIntStateOf(0) }
    // Feedback del doble-tap: acumulado de la ráfaga (+N/−N s). Se resetea al desvanecerse.
    var seekAccumMs by remember { mutableLongStateOf(0L) }
    var seekTick by remember { mutableIntStateOf(0) }
    var seekVisible by remember { mutableStateOf(false) }
    LaunchedEffect(seekTick) {
        if (seekTick == 0) return@LaunchedEffect
        seekVisible = true
        delay(800)
        seekVisible = false
    }

    // --- Volumen (mitad derecha) y brillo (mitad izquierda) por deslizamiento
    // vertical, con un medidor del lado tocado (como la app de Emby). ---
    val context = LocalContext.current
    val activity = context as? Activity
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxVol = remember { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    var overlayHeight by remember { mutableIntStateOf(0) }
    var brightness by remember {
        mutableFloatStateOf(
            (activity?.window?.attributes?.screenBrightness ?: -1f).let { if (it < 0f) 0.5f else it },
        )
    }
    var gaugeSide by remember { mutableIntStateOf(0) }   // -1 brillo / +1 volumen
    var gaugeValue by remember { mutableFloatStateOf(0f) }
    var gaugeVisible by remember { mutableStateOf(false) }
    var gaugeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(gaugeTick) {
        if (gaugeTick == 0) return@LaunchedEffect
        delay(700); gaugeVisible = false
    }
    // Al salir del player, devolver el brillo al del sistema (no dejar el override pegado).
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = -1f } }
        }
    }

    LaunchedEffect(interactionTick, controlsVisible, scrubbing) {
        if (controlsVisible && openPanel == null && !scrubbing) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }
    BackHandler {
        when {
            openPanel == Panel.Quality -> openPanel = Panel.Settings
            openPanel != null -> openPanel = null
            else -> onExit()
        }
    }

    val panelItems: List<PanelItem> = when (openPanel) {
        Panel.Audio -> audioItems()
        Panel.Subtitles -> subtitleItems()
        Panel.Episodes -> episodeItems()
        Panel.Settings -> settingsItems()
        Panel.Quality -> qualityItems()
        null -> emptyList()
    }
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Play/pausa: al PAUSAR deja los controles visibles (para poder reanudar);
    // al REPRODUCIR los oculta al toque (para ver el video sin estorbo).
    fun playPauseSmart() {
        val resuming = !isPlaying
        onTogglePlayPause()
        if (resuming) controlsVisible = false else interactionTick++
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlayWidth = it.width; overlayHeight = it.height }
            .pointerInput(isLive) {
                detectTapGestures(
                    // Tap simple: mostrar/ocultar controles.
                    onTap = { controlsVisible = !controlsVisible; interactionTick++ },
                    // Doble-tap: tercio izq = -10s, tercio der = +10s, centro = play/pausa
                    // (como en la web / YouTube). En vivo no hay seek → siempre play/pausa.
                    onDoubleTap = { offset ->
                        val third = overlayWidth / 3f
                        fun seekFb(delta: Long) {
                            onSeekBy(delta)
                            if (!seekVisible) seekAccumMs = 0L    // nueva ráfaga
                            seekAccumMs += delta
                            seekTick++; interactionTick++
                        }
                        when {
                            isLive || overlayWidth == 0 -> playPauseSmart()
                            offset.x < third -> seekFb(-SEEK_STEP_MS)
                            offset.x > overlayWidth - third -> seekFb(SEEK_STEP_MS)
                            else -> playPauseSmart()
                        }
                    },
                )
            }
            // Deslizar vertical: mitad izquierda = brillo, mitad derecha = volumen.
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { off ->
                        val side = if (off.x < overlayWidth / 2f) -1 else 1
                        gaugeSide = side
                        gaugeValue = if (side < 0) brightness
                        else (audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / maxVol
                        gaugeVisible = true
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val h = if (overlayHeight > 0) overlayHeight.toFloat() else 1080f
                        val v = (gaugeValue - dragAmount / h).coerceIn(0f, 1f)
                        gaugeValue = v
                        if (gaugeSide < 0) {
                            brightness = v
                            activity?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = v } }
                        } else {
                            audio?.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
                        }
                        change.consume()
                    },
                    onDragEnd = { gaugeTick++ },
                )
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
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backInteraction = remember { MutableInteractionSource() }
                val backPressed by backInteraction.collectIsPressedAsState()
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .then(if (backPressed) Modifier.border(2.dp, Color(0xE6FFFFFF), CircleShape) else Modifier)
                        .clickable(
                            interactionSource = backInteraction,
                            indication = null,
                            onClick = onExit,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Feedback del doble-tap: destello con «⟲10 / 10⟳» en el lado tocado.
        AnimatedVisibility(
            visible = seekVisible && seekAccumMs < 0, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) { SeekFeedback(seekAccumMs) }
        AnimatedVisibility(
            visible = seekVisible && seekAccumMs > 0, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) { SeekFeedback(seekAccumMs) }

        // Medidor de brillo (izquierda) / volumen (derecha) mientras se desliza.
        AnimatedVisibility(
            visible = gaugeVisible, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(if (gaugeSide < 0) Alignment.CenterStart else Alignment.CenterEnd),
        ) { VolumeBrightnessGauge(side = gaugeSide, value = gaugeValue) }

        // Stats for nerds: overlay técnico en vivo (arriba-izquierda), persiste
        // aunque se oculten los controles, hasta apagarlo desde Ajustes.
        if (statsVisible) {
            StatsOverlay(
                lines = statsLines,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 66.dp),
            )
        }

        // Transporte CENTRAL: play/pausa grande al medio + ±10s a los lados (como la web).
        // En vivo no se muestra (no hay play/pausa ni seek en Live TV).
        AnimatedVisibility(
            visible = controlsVisible && !isLive, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (!isLive) {
                    TransportButton(Icons.Filled.Replay10, selected = false, onClick = { onSeekBy(-SEEK_STEP_MS); interactionTick++ })
                }
                TransportButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    selected = false, big = true,
                    onClick = { playPauseSmart() },
                )
                if (!isLive) {
                    TransportButton(Icons.Filled.Forward10, selected = false, onClick = { onSeekBy(SEEK_STEP_MS); interactionTick++ })
                }
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
                // Título de la serie + capítulo, arriba del progress bar (como la web).
                subtitle?.let {
                    Text(it, color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                }
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))

                if (isLive) {
                    // Live TV: sin slider/tiempos, solo el indicador "EN VIVO".
                    LiveBadge()
                } else {
                    val shownFraction = if (scrubbing) scrubFraction else fraction
                    val accent = LocalAccent.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatTime(if (scrubbing) (scrubFraction * durationMs).toLong() else positionMs),
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        )
                        Slider(
                            value = shownFraction,
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
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            // Track redondeado con relleno de acento + thumb circular blanco (como la web).
                            track = { s ->
                                val f = s.value.coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0x40FFFFFF)),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(f)
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(accent),
                                    )
                                }
                            },
                            thumb = {
                                Box(
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            },
                        )
                        Text(formatTime(durationMs), color = Color(0xB3FFFFFF), fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // El transporte (play/±10s) vive ahora en el centro. Abajo quedan
                    // "siguiente capítulo" + opciones (audio, subtítulos, episodios).
                    Spacer(Modifier.weight(1f))
                    if (hasNext) {
                        TransportButton(Icons.Filled.SkipNext, selected = false, onClick = { onNext(); interactionTick++ })
                    }
                    // Episodios va justo después de "siguiente" (navegación juntas).
                    if (hasEpisodes) {
                        TransportButton(
                            Icons.AutoMirrored.Filled.PlaylistPlay, selected = false,
                            active = openPanel == Panel.Episodes,
                            onClick = { openPanel = Panel.Episodes; interactionTick++ },
                        )
                    }
                    // "active" (resaltado de acento) SOLO cuando el panel de ese botón está abierto.
                    TransportButton(Icons.Filled.Audiotrack, selected = false, active = openPanel == Panel.Audio, onClick = { openPanel = Panel.Audio; interactionTick++ })
                    if (hasSubtitles) {
                        TransportButton(
                            if (subsOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                            selected = false, active = openPanel == Panel.Subtitles,
                            onClick = { openPanel = Panel.Subtitles; interactionTick++ },
                        )
                    }
                    // Ajustes: selector de calidad + estadísticas. No en Live TV.
                    if (!isLive) {
                        TransportButton(
                            Icons.Filled.Settings, selected = false, active = openPanel == Panel.Settings,
                            onClick = { openPanel = Panel.Settings; interactionTick++ },
                        )
                    }
                }
            }
        }

        openPanel?.let { panel ->
            // Scrim a pantalla completa: tocar afuera vuelve al menú padre (o cierra).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { openPanel = if (openPanel == Panel.Quality) Panel.Settings else null },
                    ),
            )
            PlayerPanel(
                title = when (panel) { Panel.Audio -> "Audio"; Panel.Subtitles -> "Subtítulos"; Panel.Episodes -> "Episodios"; Panel.Settings -> "Ajustes"; Panel.Quality -> "Calidad" },
                // Al tocar un ítem: si es submenú lo abre; si no, aplica y cierra.
                items = panelItems.map { pi ->
                    PanelItem(pi.label, pi.active, pi.submenu) {
                        if (pi.submenu != null) openPanel = pi.submenu else { pi.onSelect(); openPanel = null }
                    }
                },
                cursor = -1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 150.dp),
            )
        }
    }
}

// Pantalla de error del player: mensaje + botón Volver, con back inmediato.
@Composable
internal fun PlayerErrorScreen(message: String, onExit: () -> Unit) {
    val focus = remember { FocusRequester() }
    BackHandler { onExit() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp).padding(32.dp),
        ) {
            Text(
                message,
                color = Color.White,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Focusable(onClick = onExit, modifier = Modifier.focusRequester(focus)) { highlighted ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(3.dp, if (highlighted) Color(0xB3FFFFFF) else Color.Transparent, RoundedCornerShape(50))
                        .padding(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Volver", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
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

/** Indicador "EN VIVO" (punto rojo + texto) para canales de Live TV. */
@Composable
private fun LiveBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935)),
        )
        Spacer(Modifier.width(8.dp))
        Text("EN VIVO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlayerControls(
    title: String,
    subtitle: String?,
    channelNumber: String?,
    isPlaying: Boolean,
    isLive: Boolean,
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
        // Live TV: número del canal en grande, arriba del nombre del canal.
        if (isLive && !channelNumber.isNullOrBlank()) {
            Text(
                channelNumber, color = LocalAccent.current, fontWeight = FontWeight.Bold,
                fontSize = 34.sp, lineHeight = 36.sp,
            )
            Spacer(Modifier.height(2.dp))
        }
        // Serie arriba (chica, gris) y episodio abajo (grande, destacado). En pelis
        // (sin episodio) va solo el título grande.
        subtitle?.let {
            Text(
                title, color = Color(0xB3FFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
        }
        Text(
            subtitle ?: title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))

        if (isLive) {
            // Sin barra de progreso: indicador "EN VIVO".
            LiveBadge()
        } else {
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
                    Transport.NextEp -> TransportButton(Icons.Filled.SkipNext, selected)
                    Transport.Audio -> {
                        Spacer(Modifier.weight(1f))
                        TransportButton(Icons.Filled.Audiotrack, selected)
                    }
                    Transport.Subtitles -> TransportButton(
                        if (subsOn) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                        selected, active = subsOn,
                    )
                    Transport.Episodes -> TransportButton(Icons.AutoMirrored.Filled.PlaylistPlay, selected)
                    Transport.Settings -> TransportButton(Icons.Filled.Settings, selected)
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
        // Auto-scroll al cursor: clave para el panel de episodios, que se abre
        // posicionado en el episodio actual (que puede estar muy abajo).
        val listState = rememberLazyListState()
        LaunchedEffect(cursor) {
            if (cursor in items.indices) listState.animateScrollToItem(cursor)
        }
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 320.dp)) {
            itemsIndexed(items) { i, item ->
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Anillo blanco cuando está seleccionado (D-pad TV) o presionado (táctil).
    val ring = selected || pressed
    val bg = when {
        ring && active -> accent.copy(alpha = 0.32f)
        ring -> Color(0x33FFFFFF)
        active -> accent.copy(alpha = 0.22f)
        else -> Color(0x17FFFFFF)
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (ring) Modifier.border(2.dp, Color(0xE6FFFFFF), CircleShape)
                else Modifier.border(1.dp, Color(0x24FFFFFF), CircleShape),
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
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

/** Badge circular transitorio del seek: muestra el ACUMULADO de la ráfaga (+30 s / −20 s). */
@Composable
private fun SeekFeedback(deltaMs: Long) {
    val forward = deltaMs >= 0
    val secs = (abs(deltaMs) / 1000).toInt()
    Box(
        modifier = Modifier
            .padding(horizontal = 28.dp)
            .size(94.dp)
            .clip(CircleShape)
            .background(Color(0x66000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (forward) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${if (forward) "+" else "−"}$secs s",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Medidor vertical transitorio de brillo (side<0) o volumen (side>0), estilo Emby. */
@Composable
private fun VolumeBrightnessGauge(side: Int, value: Float) {
    val accent = LocalAccent.current
    val v = value.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .padding(horizontal = 22.dp)
            .width(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x73000000))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            when {
                side < 0 -> Icons.Filled.BrightnessHigh
                v <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                else -> Icons.AutoMirrored.Filled.VolumeUp
            },
            contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .height(130.dp)
                .width(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x40FFFFFF)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(v)
                    .width(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("${(v * 100).roundToInt()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Etiqueta corta de la calidad actual (para mostrarla en el menú de Ajustes). */
internal fun qualityLabel(bitrate: Int?): String = when (bitrate) {
    null -> "Original"
    10_000_000 -> "1080p"
    4_000_000 -> "720p"
    2_000_000 -> "540p"
    else -> "Auto"
}

/** Menú de Ajustes: "Calidad" (abre submenú) + toggle de estadísticas. */
internal fun settingsMenuItems(
    currentQualityLabel: String,
    statsOn: Boolean,
    onToggleStats: () -> Unit,
): List<PanelItem> = listOf(
    PanelItem("Calidad · $currentQualityLabel", active = false, submenu = Panel.Quality),
    PanelItem(if (statsOn) "Ocultar estadísticas" else "Estadísticas (nerds)", statsOn) { onToggleStats() },
)

/** Submenú de Calidad: Original (si la fuente ≤1080p) + presets. */
internal fun qualityMenuItems(
    current: Int?,
    sourceHeight: Int?,
    onPickQuality: (Int?) -> Unit,
): List<PanelItem> = buildList {
    // "Original" (direct-play, calidad de la fuente) solo si NO supera 1080p. Si la
    // fuente es 4K/1440p, el tope pasa a 1080p (no ofrecemos el original, muy pesado).
    if (sourceHeight == null || sourceHeight <= 1080) {
        add(PanelItem("Original", current == null) { onPickQuality(null) })
    }
    listOf<Pair<String, Int?>>(
        "1080p" to 10_000_000,
        "720p" to 4_000_000,
        "540p" to 2_000_000,
    ).forEach { (label, br) -> add(PanelItem(label, current == br) { onPickQuality(br) }) }
}

/** Overlay técnico "for nerds": refresca los datos cada segundo. */
@Composable
private fun StatsOverlay(lines: () -> List<Pair<String, String>>, modifier: Modifier) {
    var data by remember { mutableStateOf(lines()) }
    LaunchedEffect(Unit) { while (true) { data = lines(); delay(1000) } }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB8000000))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("Estadísticas", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        data.forEach { (k, v) ->
            Row {
                Text("$k: ", color = Color(0x99FFFFFF), fontSize = 11.sp)
                Text(v, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Destello central de play/pausa (como la web), transitorio. */
@Composable
private fun CenterPlayPauseFeedback(playing: Boolean) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(Color(0x59000000)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp),
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
