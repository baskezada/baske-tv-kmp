package cl.baske.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.components.ModalDivider
import cl.baske.tv.ui.components.ModalOption
import cl.baske.tv.ui.components.ModalSheet
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.platform.Metrics
import cl.baske.tv.ui.platform.requestIfTv
import cl.baske.tv.ui.platform.rowFocus
import cl.baske.tv.ui.theme.LocalAccent
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailScreen(itemId: String, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val viewModel = koinViewModel<DetailViewModel>(key = itemId) { parametersOf(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val device = LocalDevice.current
    val metrics = device.metrics
    val playFocus = remember { FocusRequester() }
    var moreOpen by remember { mutableStateOf(false) }
    var seasonsOpen by remember { mutableStateOf(false) }
    var episodeMenu by remember { mutableStateOf<DetailViewModel.EpisodeItem?>(null) }
    val listState = rememberLazyListState()
    // Al enfocar CUALQUIER botón del header (reproducir/fav/aleatorio/más),
    // scrollear del todo arriba para ver el header/póster completo. Scroll
    // INSTANTÁNEO (no animado): la animación la cancela a mitad el bringIntoView
    // automático del foco → quedaba inestable. Se reafirma en el frame siguiente
    // para ganarle a ese bringIntoView de forma determinista.
    val scope = rememberCoroutineScope()
    val scrollTop: () -> Unit = {
        scope.launch {
            runCatching {
                listState.scrollToItem(0)
                withFrameNanos { }
                listState.scrollToItem(0)
            }
        }
    }

    BackHandler { onBack() }
    LaunchedEffect(state.loading, state.playTargetId) {
        if (!state.loading && state.playTargetId != null) playFocus.requestIfTv(device)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error!!, color = Color.White) }
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
                item(key = "header") {
                    // "Reproducir aleatorio": solo en series con episodios; elige uno al azar.
                    val onPlayRandom: (() -> Unit)? =
                        if (state.kind == DetailViewModel.Kind.Series && state.allEpisodeIds.isNotEmpty()) {
                            { viewModel.randomEpisodeId()?.let(onPlay) }
                        } else null
                    DetailHeader(
                        state, accent, metrics, playFocus, onPlay,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onMore = { moreOpen = true },
                        onPlayRandom = onPlayRandom,
                        onHeaderFocused = scrollTop,
                    )
                }
                if (state.kind == DetailViewModel.Kind.Series) {
                    item(key = "seasons") { SeasonSelector(state, metrics) { seasonsOpen = true } }
                    if (state.episodesLoading) {
                        item(key = "epsLoading") {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    } else {
                        if (state.episodes.isNotEmpty()) {
                            item(key = "epsCount") {
                                Text(
                                    "${state.episodes.size} episodios",
                                    color = Color(0x80FFFFFF), fontSize = 12.sp,
                                    modifier = Modifier.padding(start = metrics.gutter, top = 4.dp, bottom = 8.dp),
                                )
                            }
                        }
                        items(state.episodes, key = { it.id }) { ep ->
                            EpisodeRow(
                                ep, accent, metrics, device.isPhone,
                                onPlay = { onPlay(ep.id) },
                                onLongPress = { episodeMenu = ep },
                            )
                        }
                    }
                }
            }
        }
    }

    // Botón de volver (solo táctil; en TV se usa el back del control).
    if (device.isTouch) {
        Focusable(
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp),
        ) { highlighted ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }

    // Modal de temporadas.
    if (seasonsOpen) {
        ModalSheet("Temporadas", onDismiss = { seasonsOpen = false }) {
            Spacer(Modifier.height(2.dp))
            // Los "Especiales" (temporada 0) siempre al final de la lista.
            val orderedSeasons = state.seasons.sortedBy { it.name.contains("especial", ignoreCase = true) }
            orderedSeasons.forEach { season ->
                SeasonModalRow(
                    season = season,
                    selected = season.id == state.selectedSeasonId,
                    accent = accent,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { viewModel.selectSeason(season.id); seasonsOpen = false }
                Spacer(Modifier.height(8.dp))
            }
            FaltanTemporadasButton(modifier = Modifier.padding(horizontal = 16.dp)) {
                viewModel.refreshMetadata(); seasonsOpen = false
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // Modal "más opciones" de la serie/película.
    if (moreOpen) {
        ModalSheet(state.title.ifEmpty { "Opciones" }, onDismiss = { moreOpen = false }) {
            ModalOption(
                if (state.played) "Marcar como no visto" else "Marcar como visto",
                icon = Icons.Filled.CheckCircle,
            ) { viewModel.toggleWatched(); moreOpen = false }
        }
    }

    // Modal del episodio (long-press).
    episodeMenu?.let { ep ->
        ModalSheet(ep.title, onDismiss = { episodeMenu = null }) {
            ModalOption(
                if (ep.played) "Marcar como no visto" else "Marcar como visto",
                icon = Icons.Filled.CheckCircle,
            ) { viewModel.toggleEpisodeWatched(ep.id); episodeMenu = null }
            ModalDivider()
            ModalOption(
                if (ep.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                icon = if (ep.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            ) { viewModel.toggleEpisodeFavorite(ep.id); episodeMenu = null }
        }
    }
}

@Composable
private fun DetailHeader(
    state: DetailViewModel.UiState,
    accent: Color,
    metrics: Metrics,
    playFocus: FocusRequester,
    onPlay: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onPlayRandom: (() -> Unit)?,
    onHeaderFocused: () -> Unit = {},
) {
    val device = LocalDevice.current
    if (device.isPhone) DetailHeaderPhone(state, accent, metrics, playFocus, onPlay, onToggleFavorite, onMore, onPlayRandom, onHeaderFocused)
    else DetailHeaderWide(state, accent, metrics, playFocus, onPlay, onToggleFavorite, onMore, onPlayRandom, onHeaderFocused)
}

// Teléfono: como la web mobile — póster centrado arriba, título/meta/overview
// centrados, botón de reproducir centrado.
@Composable
private fun DetailHeaderPhone(
    state: DetailViewModel.UiState,
    accent: Color,
    metrics: Metrics,
    playFocus: FocusRequester,
    onPlay: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onPlayRandom: (() -> Unit)?,
    onHeaderFocused: () -> Unit = {},
) {
    Box(Modifier.fillMaxWidth()) {
        // Backdrop tenue detrás de la parte superior.
        AsyncImage(
            model = state.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(280.dp).background(Color(0xFF141414)),
        )
        Box(Modifier.fillMaxWidth().height(280.dp).background(Brush.verticalGradient(listOf(Color(0xB3080808), Color(0xFF080808)))))

        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = metrics.gutter, end = metrics.gutter, top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = state.posterUrl,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A)),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                state.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            DetailMeta(state, center = true)
            state.overview?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it, color = Color(0xB3FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.playTargetId != null) {
                Spacer(Modifier.height(18.dp))
                PlayButton(state.playLabel, accent, playFocus, onFocusChanged = { if (it) onHeaderFocused() }) { onPlay(state.playTargetId!!) }
            }
            Spacer(Modifier.height(14.dp))
            ActionButtons(state, accent, center = true, onToggleFavorite = onToggleFavorite, onMore = onMore, onPlayRandom = onPlayRandom, onFocusScrollTop = onHeaderFocused)
        }
    }
}

// TV/tablet: layout apaisado (póster a la izquierda, contenido a la derecha).
@Composable
private fun DetailHeaderWide(
    state: DetailViewModel.UiState,
    accent: Color,
    metrics: Metrics,
    playFocus: FocusRequester,
    onPlay: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onPlayRandom: (() -> Unit)?,
    onHeaderFocused: () -> Unit = {},
) {
    val device = LocalDevice.current
    val headerHeight = if (device.isTv) 460.dp else 420.dp
    Box(Modifier.fillMaxWidth().height(headerHeight)) {
        AsyncImage(
            model = state.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color(0xFF141414)),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99080808), Color(0xFF080808)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6080808), Color.Transparent))))

        Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = metrics.gutter, end = metrics.gutter, bottom = 28.dp)) {
            AsyncImage(
                model = state.posterUrl,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(metrics.detailPosterWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)),
            )
            Spacer(Modifier.width(if (device.isTv) 32.dp else 24.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(state.title, color = Color.White, fontSize = metrics.detailTitleSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                DetailMeta(state, center = false)
                state.overview?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = Color(0xB3FFFFFF), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(24.dp))
                // rowFocus: al volver arriba (subir desde Temporadas) el foco cae en
                // Reproducir (último enfocado / primero), no en Aleatorio o Más.
                Row(
                    modifier = if (state.playTargetId != null) Modifier.rowFocus(playFocus) else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.playTargetId != null) {
                        PlayButton(state.playLabel, accent, playFocus, big = true, onFocusChanged = { if (it) onHeaderFocused() }) { onPlay(state.playTargetId!!) }
                    }
                    ActionButtons(state, accent, center = false, onToggleFavorite = onToggleFavorite, onMore = onMore, onPlayRandom = onPlayRandom, onFocusScrollTop = onHeaderFocused)
                }
            }
        }
    }
}

// Botones de acción (favorito + menú "más" con marcar visto), como la web.
@Composable
private fun ActionButtons(
    state: DetailViewModel.UiState,
    accent: Color,
    center: Boolean,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
    onPlayRandom: (() -> Unit)? = null,
    onFocusScrollTop: () -> Unit = {},
) {
    Row(
        modifier = if (center) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionCircle(
            icon = if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            active = state.isFavorite,
            accent = accent,
            onClick = onToggleFavorite,
            onFocused = onFocusScrollTop,
        )
        // "Reproducir aleatorio" (solo series con episodios): botón chico junto al fav.
        onPlayRandom?.let {
            ActionCircle(icon = Icons.Filled.Shuffle, active = false, accent = accent, onClick = it, onFocused = onFocusScrollTop)
        }
        ActionCircle(icon = Icons.Filled.MoreVert, active = false, accent = accent, onClick = onMore, onFocused = onFocusScrollTop)
    }
}

@Composable
private fun ActionCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, accent: Color, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    Focusable(onClick = onClick, modifier = Modifier.onFocusChanged { if (it.isFocused) onFocused() }) { highlighted ->
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) accent.copy(alpha = 0.22f) else Color(0x24FFFFFF))
                .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) accent else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// Línea de metadatos: "2026 · 24m · [16] · ★ 7.9 · 1 temp. · Comedia, Acción".
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailMeta(state: DetailViewModel.UiState, center: Boolean) {
    val textColor = Color(0xCCFFFFFF)
    FlowRow(
        modifier = if (center) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalArrangement = Arrangement.Center,
    ) {
        var shown = false
        @Composable fun dot() { if (shown) Text("·", color = Color(0x66FFFFFF), fontSize = 13.sp) }
        state.year?.let { dot(); Text(it, color = textColor, fontSize = 13.sp); shown = true }
        state.runtimeLabel?.let { dot(); Text(it, color = textColor, fontSize = 13.sp); shown = true }
        state.officialRating?.let {
            dot()
            Box(modifier = Modifier.border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(it, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            shown = true
        }
        state.rating?.let {
            dot()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text(formatRating(it), color = textColor, fontSize = 13.sp)
            }
            shown = true
        }
        state.seasonsLabel?.let { dot(); Text(it, color = textColor, fontSize = 13.sp); shown = true }
        state.genresLabel?.let { dot(); Text(it, color = textColor, fontSize = 13.sp); shown = true }
    }
}

@Composable
private fun PlayButton(label: String, accent: Color, playFocus: FocusRequester, big: Boolean = false, onFocusChanged: (Boolean) -> Unit = {}, onClick: () -> Unit) {
    val hPad = if (big) 34.dp else 26.dp
    val vPad = if (big) 16.dp else 12.dp
    val iconSize = if (big) 26.dp else 22.dp
    val fontSize = if (big) 17.sp else 15.sp
    Focusable(onClick = onClick, modifier = Modifier.focusRequester(playFocus).onFocusChanged { onFocusChanged(it.isFocused) }) { highlighted ->
        // Ring blanco translúcido EXTERNO (separado de la superficie blanca) al
        // presionar/enfocar — mismo estilo para todos los botones.
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
                    .padding(horizontal = hPad, vertical = vPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.Black, fontSize = fontSize, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Selector de temporada: barra que abre el modal de temporadas.
@Composable
private fun SeasonSelector(state: DetailViewModel.UiState, metrics: Metrics, onOpen: () -> Unit) {
    if (state.seasons.isEmpty()) return
    val current = state.seasons.firstOrNull { it.id == state.selectedSeasonId } ?: state.seasons.first()

    Box(modifier = Modifier.fillMaxWidth().padding(start = metrics.gutter, end = metrics.gutter, top = 20.dp, bottom = 8.dp)) {
        Focusable(onClick = onOpen) { highlighted ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .then(if (highlighted) Modifier.border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(22.dp))
            }
        }
    }
}

// Fila de temporada en el modal: barra de acento a la izquierda (si es la
// actual) + nombre + "N ep." — como la ref de la web.
@Composable
private fun SeasonModalRow(
    season: DetailViewModel.SeasonTab,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Focusable(onClick = onClick, modifier = modifier.fillMaxWidth()) { highlighted ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) Color(0x26FFFFFF) else Color(0x14FFFFFF)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) accent else Color.Transparent))
            Spacer(Modifier.width(14.dp))
            Text(season.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            season.episodeCount?.let {
                Text("$it ep.", color = Color(0x80FFFFFF), fontSize = 12.sp)
                Spacer(Modifier.width(16.dp))
            }
        }
    }
}

@Composable
private fun FaltanTemporadasButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = modifier.fillMaxWidth()) { highlighted ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) Color(0x24FFFFFF) else Color(0x14FFFFFF))
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("¿Faltan temporadas?", color = Color(0xCCFFFFFF), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    ep: DetailViewModel.EpisodeItem,
    accent: Color,
    metrics: Metrics,
    isPhone: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
) {
    val device = LocalDevice.current
    val thumbWidth = if (isPhone) 120.dp else 150.dp
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = if (device.isTv) focused else pressed

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.gutter, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                // Card siempre visible (fondo sutil), se realza al enfocar/presionar.
                .background(if (highlighted) Color(0x24FFFFFF) else Color(0x0DFFFFFF))
                .then(if (highlighted) Modifier.border(1.5.dp, Color(0x45FFFFFF), RoundedCornerShape(14.dp)) else Modifier)
                // Tap = reproducir; mantener presionado = menú del episodio.
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onPlay,
                    onLongClick = onLongPress,
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(thumbWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1A1A))) {
                AsyncImage(model = ep.imageUrl, contentDescription = ep.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (ep.played) {
                    Box(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Visto", tint = Color.Black, modifier = Modifier.size(13.dp))
                    }
                }
                if (ep.progress > 0f) {
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(ep.progress).height(3.dp).background(accent))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ep.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ep.meta.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(ep.meta, color = Color(0x80FFFFFF), fontSize = 12.sp)
                }
                ep.overview?.let {
                    Spacer(Modifier.height(5.dp))
                    Text(it, color = Color(0x66FFFFFF), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun formatRating(r: Double): String = ((r * 10).toInt() / 10.0).toString()
