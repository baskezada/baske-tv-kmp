package cl.baske.tv.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.baske.tv.core.embyImageUrl
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.data.remote.SeerrApi
import cl.baske.tv.data.remote.SeerrResult
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.NavTarget
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.rowFocus
import cl.baske.tv.ui.theme.LocalAccent
import kotlinx.coroutines.launch
import cl.baske.tv.ui.platform.LocalDevice
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.compose.koinInject

private data class DiscoverRow(val title: String, val items: List<SeerrResult>)

/**
 * Tab "Descubrir" (plugin EmbySeerr). Solo se referencia bajo
 * BuildConfig.ENABLE_DISCOVER → R8 la saca del bundle cuando el flag está off.
 */
@Composable
fun DiscoverScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    firstFocus: androidx.compose.ui.focus.FocusRequester? = null,
    onOpenLocal: (HomeCard) -> Unit,
    onOpenProvider: (Int, String) -> Unit,
    onOpenAnime: () -> Unit,
    onOpenTmdb: (SeerrResult) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val device = LocalDevice.current
    // Restaurar foco al volver de un item (ver HomeScreen): guardamos el id de la
    // card al navegar y la re-enfocamos cuando la tab vuelve a cargar.
    var restoreItemId by rememberSaveable { mutableStateOf<Int?>(null) }
    val restoreFocus = remember { FocusRequester() }
    // Al tocar un ítem del catálogo: si ya está en la biblioteca → detalle Emby;
    // si no → detalle TMDB (pantalla) para solicitarlo.
    fun openItem(item: SeerrResult) {
        restoreItemId = item.id
        val eid = item.embyId
        if (eid != null) onOpenLocal(HomeCard(id = eid, title = item.displayTitle, subtitle = null, imageUrl = null, navTarget = NavTarget.Detail))
        else onOpenTmdb(item)
    }
    val client = koinInject<HttpClient>()
    val sessionStore = koinInject<SessionStore>()
    val api = remember { SeerrApi(client, sessionStore) }

    var rows by remember { mutableStateOf<List<DiscoverRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }
    var animeItems by remember { mutableStateOf<List<SeerrResult>>(emptyList()) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        loading = true
        error = false
        val result = runCatching {
            coroutineScope {
                // Todas en paralelo; mismo orden que el web.
                fun row(block: suspend () -> List<SeerrResult>) =
                    async { runCatching { block() }.getOrDefault(emptyList()) }
                val anime = row { api.seasonalAnime().results }
                val trending = row { api.trending().results }
                val popMovies = row { api.popularMovies().results }
                val upMovies = row { api.upcomingMovies().results }
                val popTv = row { api.popularTv().results }
                val upTv = row { api.upcomingTv().results }

                fun clean(l: List<SeerrResult>) = l.filterNot { it.isPerson }.filter { it.posterUrl != null }
                // El anime de temporada va como tile de la fila de plataformas, no como fila.
                animeItems = clean(anime.await())
                buildList {
                    clean(trending.await()).let { if (it.isNotEmpty()) add(DiscoverRow("Tendencias", it)) }
                    clean(popMovies.await()).let { if (it.isNotEmpty()) add(DiscoverRow("Películas populares", it)) }
                    clean(upMovies.await()).let { if (it.isNotEmpty()) add(DiscoverRow("Próximas películas", it)) }
                    clean(popTv.await()).let { if (it.isNotEmpty()) add(DiscoverRow("Series populares", it)) }
                    clean(upTv.await()).let { if (it.isNotEmpty()) add(DiscoverRow("Próximas series", it)) }
                }
            }
        }.getOrDefault(emptyList())
        rows = result
        error = result.isEmpty()
        loading = false
    }

    // Al terminar de cargar, si volvimos de un item, re-enfocar esa card (Tv).
    androidx.compose.runtime.LaunchedEffect(loading) {
        if (!loading && device.isTv && restoreItemId != null) {
            withFrameNanos { }
            runCatching { restoreFocus.requestFocus() }
        }
    }

    val gutter = LocalDevice.current.metrics.gutter

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topPadding + 8.dp))
            // Barra de búsqueda: al tocarla abre la pantalla de búsqueda (overlay).
            DiscoverSearchBar(gutter = gutter, focusRequester = firstFocus, onClick = onOpenSearch)
            Spacer(Modifier.height(14.dp))

            Box(Modifier.weight(1f)) {
                when {
                    loading -> Center { CircularProgressIndicator(color = Color.White) }
                    error -> Center { Text("No se pudo cargar Descubrir", color = Color(0x99FFFFFF)) }
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // Fila de plataformas (+ tile de Anime de temporada).
                        item {
                            ProviderRow(
                                gutter = gutter,
                                animeItems = animeItems,
                                onProvider = { p -> onOpenProvider(p.id, p.name) },
                                onAnime = onOpenAnime,
                            )
                        }
                        items(rows) { row ->
                            val rowFirst = remember { FocusRequester() }
                            Column {
                                Text(
                                    row.title,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = gutter, end = gutter, bottom = 12.dp),
                                )
                                LazyRow(
                                    modifier = Modifier.rowFocus(rowFirst),
                                    contentPadding = PaddingValues(horizontal = gutter),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(row.items) { i, item ->
                                        DiscoverCard(
                                            item,
                                            focusRequester = rowFirst.takeIf { i == 0 },
                                            restoreRequester = restoreFocus.takeIf { item.id == restoreItemId },
                                        ) { openItem(item) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class StreamProvider(val id: Int, val name: String, val bg: Color, val logoAsset: String)

private const val ASSET = "file:///android_asset/streaming/"
private val PROVIDERS = listOf(
    StreamProvider(8, "Netflix", Color(0xFFE50914), "${ASSET}netflix.svg"),
    StreamProvider(9, "Prime Video", Color(0xFF1399FF), "${ASSET}amazonprime.svg"),
    StreamProvider(337, "Disney+", Color(0xFF113CCF), "${ASSET}disneyplus.svg"),
    StreamProvider(350, "Apple TV+", Color(0xFF000000), "${ASSET}appletv.svg"),
    StreamProvider(1899, "Max", Color(0xFF002BE7), "${ASSET}hbomax.svg"),
    StreamProvider(15, "Hulu", Color(0xFF1CE783), "${ASSET}hulu.svg"),
    StreamProvider(531, "Paramount+", Color(0xFF0064FF), "${ASSET}paramount.svg"),
)

/** Fila horizontal de plataformas + tile de "Anime de temporada". */
@Composable
private fun ProviderRow(
    gutter: Dp,
    animeItems: List<SeerrResult>,
    onProvider: (StreamProvider) -> Unit,
    onAnime: () -> Unit,
) {
    val rowFirst = remember { FocusRequester() }
    LazyRow(
        modifier = Modifier.rowFocus(rowFirst),
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            // Tile de Anime de temporada: mosaico del primer poster + label.
            Focusable(onClick = onAnime, modifier = Modifier.focusRequester(rowFirst)) { hi ->
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161A))
                        .then(if (hi) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    animeItems.firstOrNull()?.backdropUrl?.let {
                        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Color(0x99000000)))
                    }
                    Text("Anime de temporada", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
        items(PROVIDERS, key = { it.id }) { p ->
            Focusable(onClick = { onProvider(p) }) { hi ->
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.bg)
                        .then(if (hi) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = p.logoAsset,
                        contentDescription = p.name,
                        contentScale = ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White),
                        modifier = Modifier.fillMaxWidth(0.6f).padding(vertical = 20.dp),
                        error = { Text(p.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
                        loading = {},
                    )
                }
            }
        }
    }
}


@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun DiscoverSearchBar(gutter: Dp, focusRequester: androidx.compose.ui.focus.FocusRequester?, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = gutter)) {
        val barMod = if (focusRequester != null) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth()
        Focusable(onClick = onClick, modifier = barMod) { hi ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x1FFFFFFF))
                    .then(if (hi) Modifier.border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(14.dp)) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Buscar películas y series…", color = Color(0x80FFFFFF), fontSize = 15.sp)
            }
        }
    }
}

@Composable
internal fun DiscoverCard(
    item: SeerrResult,
    fillWidth: Boolean = false,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    restoreRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onClick: () -> Unit,
) {
    Column(modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(130.dp)) {
        Focusable(
            onClick = onClick,
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (restoreRequester != null) Modifier.focusRequester(restoreRequester) else Modifier),
        ) { highlighted ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (highlighted) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier),
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                )
                // Badge de estado (como en Seerr): verde=disponible, azul=procesando,
                // ámbar=solicitado/pendiente. Sin badge = solicitable.
                StatusBadge(item.mediaInfo?.status, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }
        Box(Modifier.height(8.dp))
        Text(item.displayTitle, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.year?.let {
            Text(it, color = Color(0x80FFFFFF), fontSize = 11.sp)
        }
    }
}

/** Badge de estado del ítem (esquina), con los 4 estados de Seerr. */
@Composable
internal fun StatusBadge(status: Int?, modifier: Modifier = Modifier) {
    val color: Color
    val icon: ImageVector
    when (status) {
        5, 4 -> { color = Color(0xFF22C55E); icon = Icons.Filled.Check }        // disponible / parcial
        3 -> { color = Color(0xFF3B82F6); icon = Icons.Filled.Downloading }     // procesando
        2 -> { color = Color(0xFFF59E0B); icon = Icons.Filled.Schedule }        // solicitado / pendiente
        else -> return
    }
    Box(
        modifier = modifier.size(22.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
internal fun SearchSectionHeader(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}

/** Card de resultado local (ítem Emby) en la búsqueda. */
@Composable
internal fun LocalResultCard(item: BaseItemDto, serverUrl: String, onClick: () -> Unit) {
    val tag = item.imageTags?.get("Primary")
    val url = embyImageUrl(serverUrl, item.id, "Primary", tag, 320)
    Column(modifier = Modifier.fillMaxWidth()) {
        Focusable(onClick = onClick) { highlighted ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (highlighted) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                )
                // Está en la biblioteca → check verde.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, contentDescription = "En tu biblioteca", tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Box(Modifier.height(8.dp))
        Text(item.name ?: "", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.productionYear?.let { Text(it.toString(), color = Color(0x80FFFFFF), fontSize = 11.sp) }
    }
}

internal enum class RequestState { Idle, Loading, Done, Error }

@Composable
internal fun RequestButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    accent: Color,
    onClick: () -> Unit,
) {
    Focusable(onClick = { if (enabled && !loading) onClick() }, modifier = Modifier.fillMaxWidth()) { hi ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (enabled) accent else Color(0x1FFFFFFF))
                .then(if (hi && enabled) Modifier.border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(50)) else Modifier)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                icon?.let {
                    Icon(it, contentDescription = null, tint = if (enabled) Color.Black else Color(0x99FFFFFF), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, color = if (enabled) Color.Black else Color(0xCCFFFFFF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
