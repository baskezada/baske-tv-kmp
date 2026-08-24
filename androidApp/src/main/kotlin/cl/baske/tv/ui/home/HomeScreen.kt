package cl.baske.tv.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.R
import cl.baske.tv.BuildConfig
import cl.baske.tv.data.HomeMode
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.data.SessionStore
import cl.baske.tv.ui.discover.DiscoverScreen
import cl.baske.tv.ui.search.SearchScreen
import cl.baske.tv.ui.tv.TvScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class Tab { Home, Descubrir, TV, Libros }

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit = {},
    onCardClick: (HomeCard) -> Unit = {},
    onPlayItem: (HomeCard) -> Unit = {},
    onOpenProvider: (Int, String) -> Unit = { _, _ -> },
    onOpenAnime: () -> Unit = {},
    onOpenTmdb: (cl.baske.tv.data.remote.SeerrResult) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefsStore = koinInject<PrefsStore>()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle()
    val session by koinInject<SessionStore>().session.collectAsStateWithLifecycle()
    // Avatar del usuario logueado (reemplaza el ícono de ajustes). Emby: Primary
    // del usuario; si no tiene, el AsyncImage cae al ícono de ajustes.
    val avatarUrl = session?.let { "${it.serverUrl}/Users/${it.userId}/Images/Primary?api_key=${it.accessToken}" }
    val userInitial = session?.userName?.trim()?.firstOrNull()?.uppercase() ?: "?"
    val accent = LocalAccent.current
    val device = LocalDevice.current
    val metrics = device.metrics
    // Inset real de la barra de navegación del sistema (gestos/botones), para
    // que la última fila no quede tapada por la barra inferior de la app.
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Frosted glass en header/bottom SOLO en teléfono/tablet: en TV el blur en
    // vivo mientras scrolleás es caro para GPUs de gama baja (Chromecast) y en
    // Android TV viejo (API<31) ni siquiera difumina. hazeState=null → sin blur.
    val hazeState = remember { HazeState() }
    val blurBars = !device.isTv

    val firstCardFocus = remember { FocusRequester() }
    val gearFocus = remember { FocusRequester() }
    val heroPlayFocus = remember { FocusRequester() }
    val navHomeFocus = remember { FocusRequester() }
    // Grupo de foco del contenido de cada tab: el D-pad BAJA de los tabs a este
    // grupo, que delega en su primer enfocable. Apuntar a un grupo (contenedor
    // SIEMPRE montado) en vez de a una card puntual evita el "FocusRequester not
    // initialized" cuando esa card aún no está colocada (p.ej. al reordenarse las
    // filas por refreshResume, o con la primera fila bajo el header).
    val homeContentFocus = remember { FocusRequester() }
    val discoverFirstFocus = remember { FocusRequester() }
    val tvFirstFocus = remember { FocusRequester() }
    // Restauración de foco al volver de un item: guardamos el id de la card desde
    // la que se navegó (rememberSaveable → sobrevive el dispose de Home vía
    // SaveableStateProvider) y al reaparecer pedimos foco en esa misma card. Se
    // guarda al hacer click (navegar), no en cada onFocused, para no recomponer
    // las filas en cada movimiento del D-pad.
    var restoreCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }
    var activeTab by rememberSaveable { mutableStateOf(Tab.Home) }
    val listState = rememberLazyListState()
    // Estados de scroll de cada tab (hoisted): sirven para revelar el blur del
    // header al bajar y para conservar la posición al volver de un item.
    val discoverListState = rememberLazyListState()
    val tvGridState = rememberLazyGridState()
    val density = LocalDensity.current
    // El header ahora es FIJO arriba (no scrollea). Se mide su alto para reservar
    // espacio del contenido cuando no hay hero detrás, y se vuelve opaco al bajar.
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // Alto real de la barra inferior (íconos + label + inset de gestos), para
    // reservar exactamente ese espacio y que la última fila no quede tapada.
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var heroPlayFocused by remember { mutableStateOf(false) }
    var heroIndex by remember { mutableStateOf(0) }
    // Menú contextual "more" (mantener presionada una card). menuIsResume marca
    // si la card salió de "Continuar viendo" (habilita "Quitar de continuar viendo").
    var menuCard by remember { mutableStateOf<HomeCard?>(null) }
    var menuIsResume by remember { mutableStateOf(false) }
    // Modal de perfil (al tocar el avatar): por ahora Ajustes + Cerrar sesión.
    var profileMenuOpen by remember { mutableStateOf(false) }
    // Sin scroll manual: el hero + la primera fila entran en pantalla (ver
    // heroFraction), así el bringIntoView del foco no necesita mover nada al
    // navegar el banner/Continuar viendo. Recién al bajar a la 2da fila scrollea.

    val hasContent = state.rows.isNotEmpty()
    val featuredList = state.featured
    // Clamp por si la lista cambió de tamaño.
    if (featuredList.isNotEmpty() && heroIndex >= featuredList.size) heroIndex = 0
    val effectiveHero = if (prefs.homeMode == HomeMode.SinBanner) null else featuredList.getOrNull(heroIndex)
    val hasHero = effectiveHero != null

    // Header transparente ARRIBA en TODAS las tabs: el BLUR/fondo arranca apagado
    // y entra con un fade corto 0→1 al empezar a scrollear la tab activa (por
    // estado, no ligado al offset → sin estados intermedios raros). Detrás siempre
    // hay un gradiente negro sutil para legibilidad del logo. El logo/botones se
    // ven SIEMPRE. Igual en TV. En Inicio sin hero el fondo se muestra de una
    // (el contenido arranca bajo el header, sin imagen detrás).
    val heroAtTop = activeTab == Tab.Home && hasHero
    val revealBlur by remember(activeTab, hasHero) {
        derivedStateOf {
            when (activeTab) {
                Tab.Home -> !hasHero ||
                    listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 80
                Tab.Descubrir -> discoverListState.firstVisibleItemIndex > 0 ||
                    discoverListState.firstVisibleItemScrollOffset > 80
                Tab.TV -> tvGridState.firstVisibleItemIndex > 0 ||
                    tvGridState.firstVisibleItemScrollOffset > 80
                else -> true
            }
        }
    }
    val blurAlpha by animateFloatAsState(
        targetValue = if (revealBlur) 1f else 0f,
        animationSpec = tween(220),
        label = "headerBlur",
    )

    // Al entrar al Home (arranque y, sobre todo, al volver del player) revalida
    // "Continuar viendo" para reflejar el progreso recién visto sin recargar todo.
    LaunchedEffect(Unit) { viewModel.refreshResume() }

    // Espeja "Continuar viendo" en la fila Watch Next del launcher de Android TV /
    // Google TV. Se re-sincroniza cada vez que cambia el resume (p.ej. al volver
    // del player). Solo en TV; en teléfono WatchNextSync ignora el intento.
    val context = LocalContext.current
    val resumeCards = state.rows.firstOrNull { it.id == "resume" }?.cards
    LaunchedEffect(resumeCards) {
        if (!device.isTv) return@LaunchedEffect
        val entries = (resumeCards ?: emptyList()).mapNotNull { c ->
            val durMs = ((c.runtimeTicks ?: 0L) / 10_000L).toInt()
            if (durMs <= 0) return@mapNotNull null
            cl.baske.tv.tv.WatchNextEntry(
                id = c.id,
                title = c.title,
                description = c.subtitle,
                posterUri = c.imageUrl,
                durationMs = durMs,
                positionMs = (c.progress.coerceIn(0f, 1f) * durMs).toInt(),
                isEpisode = c.type == "Episode",
            )
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            cl.baske.tv.tv.WatchNextSync.sync(context, entries)
        }
    }

    // Al enfocar CUALQUIER botón del banner, scrollear del todo arriba para que se
    // vea el hero completo. Scroll INSTANTÁNEO (no animado): la animación la
    // cancela a mitad el bringIntoView automático del foco → quedaba inestable.
    // Además se reafirma en el frame siguiente para ganarle a ese bringIntoView de
    // forma determinista. Una vez arriba, el botón ya está visible y el
    // bringIntoView no vuelve a mover nada.
    val scope = rememberCoroutineScope()
    val scrollHeroTop: () -> Unit = {
        scope.launch {
            runCatching {
                listState.scrollToItem(0)
                withFrameNanos { }
                listState.scrollToItem(0)
            }
        }
    }

    // Carrusel: rota los destacados cada 6s. Se pausa solo con el foco en
    // Reproducir (ahí estás "usando" el banner); en Continuar viendo sigue rotando.
    LaunchedEffect(featuredList.size, heroPlayFocused) {
        if (featuredList.size <= 1 || heroPlayFocused) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(6000)
            heroIndex = (heroIndex + 1) % featuredList.size
        }
    }

    // Foco inicial (solo Tv, solo en la tab Inicio): al VOLVER de un item restaura
    // la card desde la que se navegó; en el primer arranque va al Reproducir del
    // hero (o la 1ra card). En Descubrir/TV el foco lo maneja cada tab.
    LaunchedEffect(hasContent, activeTab) {
        if (hasContent && device.isTv && activeTab == Tab.Home) {
            if (restoreCardId != null) {
                // Esperar un frame a que el LazyColumn restaure scroll y coloque la card.
                withFrameNanos { }
                runCatching { restoreFocus.requestFocus() }
            } else {
                (if (hasHero) heroPlayFocus else firstCardFocus).requestIfTv(device)
            }
        }
    }
    // Target hacia el que baja el header (Reproducir si hay hero, si no la card).
    val belowHeaderFocus = if (hasHero) heroPlayFocus else firstCardFocus
    // Al subir desde la primera fila: al Reproducir del hero, o directo a los tabs.
    val firstCardUpTarget = if (hasHero) heroPlayFocus else navHomeFocus

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF080808))) {
        val heroHeight = maxHeight * metrics.heroFraction

        // El header va DENTRO del scroll (no fijo): se oculta al bajar y, cuando
        // hay hero, se dibuja SOBRE la imagen (full-bleed) como en la web.
        // En teléfono la navegación va en una barra inferior (como la web), así
        // que el header de arriba es mínimo (logo + ajustes) y no mete los tabs
        // ni los íconos que apretaban la fila en una pantalla angosta.
        val header: @Composable (Modifier, FocusRequester?) -> Unit = { mod, down ->
            if (device.isPhone) {
                HomeHeaderPhone(accent = accent, metrics = metrics, avatarUrl = avatarUrl, userInitial = userInitial, onOpenSettings = { profileMenuOpen = true }, modifier = mod)
            } else {
                HomeHeader(
                    accent = accent,
                    metrics = metrics,
                    activeTab = activeTab,
                    onTabFocused = { activeTab = it },
                    gearFocus = gearFocus,
                    navHomeFocus = navHomeFocus,
                    downFocus = down,
                    avatarUrl = avatarUrl,
                    userInitial = userInitial,
                    onOpenSettings = { profileMenuOpen = true },
                    onOpenSearch = onOpenSearch,
                    modifier = mod,
                )
            }
        }

        when {
            state.loading && state.rows.isEmpty() -> CenterMessage("Cargando…")
            state.rows.isEmpty() && state.error != null -> CenterMessage(state.error!!)
            else -> {
                val firstRowId = state.rows.firstOrNull()?.id
                // Con hero el contenido arranca en y=0 (hero full-bleed bajo el
                // header fijo); sin hero se reserva el alto del header para que la
                // primera fila no quede tapada.
                val hasHeroTop = activeTab == Tab.Home && effectiveHero != null
                val topPad = if (hasHeroTop) 0.dp else with(density) { headerHeightPx.toDp() }
                // Reserva abajo = alto real de la barra inferior (ya incluye el
                // inset de gestos) + un pequeño respiro. Antes de medirla, un
                // fallback holgado para que nunca tape la última fila.
                val bottomReserve = when {
                    !device.isPhone -> 32.dp
                    bottomBarHeightPx > 0 -> with(density) { bottomBarHeightPx.toDp() } + 12.dp
                    else -> 96.dp + navBottom
                }
                if (activeTab == Tab.Descubrir) {
                    val topInset = with(density) { headerHeightPx.toDp() }
                    // hazeSource acá también, para que el header/bottom difuminen
                    // el contenido de esta tab (si no, las barras quedan sin fondo).
                    Box(modifier = if (blurBars) Modifier.hazeSource(hazeState) else Modifier) {
                        // "Descubrir" (EmbySeerr) o "Buscar" (biblioteca Emby) según el flag.
                        if (BuildConfig.ENABLE_DISCOVER) {
                            DiscoverScreen(
                                topPadding = topInset,
                                bottomPadding = bottomReserve,
                                listState = discoverListState,
                                firstFocus = discoverFirstFocus,
                                onOpenLocal = onCardClick,
                                onOpenProvider = onOpenProvider,
                                onOpenAnime = onOpenAnime,
                                onOpenTmdb = onOpenTmdb,
                                onOpenSearch = onOpenSearch,
                            )
                        } else {
                            SearchScreen(topPadding = topInset, bottomPadding = bottomReserve, onOpenCard = onCardClick)
                        }
                    }
                } else if (activeTab == Tab.TV) {
                    val topInset = with(density) { headerHeightPx.toDp() }
                    Box(
                        modifier = (if (blurBars) Modifier.hazeSource(hazeState) else Modifier)
                            .focusRequester(tvFirstFocus)
                            .focusGroup(),
                    ) {
                        TvScreen(
                            topPadding = topInset,
                            bottomPadding = bottomReserve,
                            gridState = tvGridState,
                            onPlayChannel = { id ->
                                onPlayItem(HomeCard(id = id, title = "", subtitle = null, imageUrl = null, playDirect = true, navTarget = NavTarget.Player))
                            },
                        )
                    }
                } else LazyColumn(
                    state = listState,
                    modifier = (if (blurBars) Modifier.hazeSource(hazeState) else Modifier)
                        .focusRequester(homeContentFocus)
                        .focusGroup(),
                    contentPadding = PaddingValues(top = topPad, bottom = bottomReserve),
                    verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing),
                ) {
                    // Hero (solo en Inicio): va full-bleed bajo el header fijo.
                    if (hasHeroTop) {
                        item(key = "hero") {
                            val hero = effectiveHero!!
                            HomeHero(
                                card = hero,
                                height = heroHeight,
                                accent = accent,
                                metrics = metrics,
                                playFocus = heroPlayFocus,
                                upFocus = navHomeFocus,
                                downFocus = firstCardFocus,
                                onPlay = {
                                    // Reproducir: película/episodio directo; serie → NextUp o 1er episodio.
                                    viewModel.resolveHeroPlay(hero) { targetId ->
                                        onPlayItem(
                                            HomeCard(id = targetId, title = "", subtitle = null, imageUrl = null, playDirect = true, navTarget = NavTarget.Player),
                                        )
                                    }
                                },
                                onInfo = {
                                    // Info: siempre al detalle (serie del episodio si aplica).
                                    onCardClick(hero.copy(id = hero.seriesId ?: hero.id, navTarget = NavTarget.Detail))
                                },
                                onPlayFocused = { heroPlayFocused = it },
                                onHeroFocused = scrollHeroTop,
                                dotCount = featuredList.size,
                                dotIndex = heroIndex,
                            )
                        }
                    }
                    if (activeTab == Tab.Home) {
                        items(state.rows, key = { it.id }) { row ->
                            val isFirstRow = row.id == firstRowId
                            RowSection(
                                row = row,
                                metrics = metrics,
                                onCardClick = { card -> restoreCardId = card.id; onCardClick(card) },
                                onCardFocused = { },
                                onCardLongPress = { card ->
                                    // Bibliotecas sin permisos de admin no tienen acciones → no abras el menú vacío.
                                    if (!(card.isLibrary && !viewModel.isAdmin)) {
                                        menuCard = card
                                        menuIsResume = row.id == "resume"
                                    }
                                },
                                firstCardFocus = if (isFirstRow) firstCardFocus else null,
                                firstCardUp = if (isFirstRow) firstCardUpTarget else null,
                                restoreCardId = restoreCardId,
                                restoreFocus = restoreFocus,
                            )
                        }
                    } else {
                        item(key = "soon") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Próximamente", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Header FIJO arriba (no scrollea): siempre visible. Se mide su alto para
        // reservar espacio del contenido sin hero, y se vuelve opaco al bajar.
        // El D-pad baja de los tabs al primer enfocable de la tab activa.
        val headerDown = when (activeTab) {
            Tab.Home -> homeContentFocus.takeIf { hasContent }
            Tab.Descubrir -> if (BuildConfig.ENABLE_DISCOVER) discoverFirstFocus else null
            Tab.TV -> tvFirstFocus
            else -> null
        }
        // Gradiente negro SIEMPRE presente (legibilidad del logo arriba), un poco
        // más alto que el header para un degradé más largo hacia el contenido.
        val headerH = with(density) { headerHeightPx.toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(if (headerHeightPx > 0) headerH + 56.dp else 150.dp)
                .background(Brush.verticalGradient(listOf(Color(0xC2000000), Color(0x4D000000), Color.Transparent))),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height },
        ) {
            // Blur/fondo: entra con un fade corto al scrollear (SOLO esta capa).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = blurAlpha }
                    .frostedBar(if (blurBars) hazeState else null, scrolled = true),
            )
            // Contenido (logo/tabs/ajustes): SIEMPRE visible, sin fondo propio.
            header(Modifier, headerDown)
        }

        // Barra inferior de navegación (solo teléfono): reemplaza los tabs del
        // header. Tap para cambiar de sección; se posa sobre la barra de gestos.
        if (device.isPhone) {
            HomeBottomBar(
                activeTab = activeTab,
                onSelect = { activeTab = it },
                accent = accent,
                hazeState = if (blurBars) hazeState else null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomBarHeightPx = it.height },
            )
        }

        // Menú contextual "more" (depende del tipo de la card, como la web).
        menuCard?.let { card ->
            val dismiss = { menuCard = null }
            val items = buildHomeMenu(
                card = card,
                isResume = menuIsResume,
                isAdmin = viewModel.isAdmin,
                vm = viewModel,
                onGoToSeries = { seriesId ->
                    onCardClick(HomeCard(id = seriesId, title = "", subtitle = null, imageUrl = null, navTarget = NavTarget.Detail))
                },
                dismiss = dismiss,
            )
            HomeContextMenu(items = items, title = card.title, onDismiss = dismiss)
        }

        // Modal de perfil (avatar): por ahora Ajustes + Cerrar sesión.
        if (profileMenuOpen) {
            val dismiss = { profileMenuOpen = false }
            ModalSheet(title = session?.userName ?: "Cuenta", onDismiss = dismiss) {
                ModalOption("Ajustes", icon = Icons.Filled.Settings) { dismiss(); onOpenSettings() }
                ModalDivider()
                ModalOption("Cerrar sesión", icon = Icons.AutoMirrored.Filled.Logout, destructive = true) { dismiss(); onLogout() }
            }
        }
    }
}

/** Un ítem del menú contextual del Home. */
private data class HomeMenuItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean = false,
    val dividerBefore: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Arma las acciones del menú según el tipo de card (réplica de la web): ir a la
 * serie (episodios), marcar visto, favorito, quitar de continuar viendo, y —solo
 * admin— sincronizar archivos / refrescar metadatos / escanear biblioteca.
 * "Editar metadatos" se omite a propósito.
 */
private fun buildHomeMenu(
    card: HomeCard,
    isResume: Boolean,
    isAdmin: Boolean,
    vm: HomeViewModel,
    onGoToSeries: (String) -> Unit,
    dismiss: () -> Unit,
): List<HomeMenuItem> = buildList {
    val isPlayable = card.type == "Movie" || card.type == "Series" || card.type == "Episode"

    val seriesId = card.seriesId
    if (card.type == "Episode" && seriesId != null) {
        add(HomeMenuItem("Ir a la serie", Icons.Filled.Tv) { dismiss(); onGoToSeries(seriesId) })
    }
    if (isPlayable) {
        add(HomeMenuItem(
            label = if (card.played) "Marcar como no visto" else "Marcar como visto",
            icon = Icons.Filled.CheckCircle,
            selected = card.played,
        ) { dismiss(); vm.toggleWatched(card) })
    }
    if (isPlayable) {
        add(HomeMenuItem(
            label = if (card.isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
            icon = if (card.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            selected = card.isFavorite,
        ) { dismiss(); vm.toggleFavorite(card) })
    }
    if (isResume) {
        add(HomeMenuItem("Quitar de continuar viendo", Icons.Filled.Close) { dismiss(); vm.removeFromResume(card) })
    }
    if (isAdmin && card.isLibrary) {
        add(HomeMenuItem("Escanear biblioteca", Icons.Filled.Refresh, dividerBefore = isNotEmpty()) { dismiss(); vm.scanLibrary(card) })
    }
    if (isAdmin && isPlayable) {
        add(HomeMenuItem("Sincronizar archivos", Icons.Filled.Sync, dividerBefore = isNotEmpty()) { dismiss(); vm.syncFiles(card) })
        add(HomeMenuItem("Refrescar metadatos", Icons.Filled.Refresh) { dismiss(); vm.refreshMetadata(card) })
    }
}

@Composable
private fun HomeContextMenu(
    items: List<HomeMenuItem>,
    title: String,
    onDismiss: () -> Unit,
) {
    ModalSheet(title = title, onDismiss = onDismiss) {
        items.forEachIndexed { i, item ->
            if (i > 0) ModalDivider()
            ModalOption(label = item.label, selected = item.selected, icon = item.icon, onClick = item.onClick)
        }
    }
}

@Composable
private fun HomeBottomBar(
    activeTab: Tab,
    onSelect: (Tab) -> Unit,
    accent: Color,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .frostedBar(hazeState, scrolled = true)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarItem("Inicio", Icons.Filled.Home, activeTab == Tab.Home, accent) { onSelect(Tab.Home) }
        // Descubrir (EmbySeerr) o Buscar (biblioteca) según el flag de build.
        if (BuildConfig.ENABLE_DISCOVER) {
            BottomBarItem("Descubrir", Icons.Filled.Explore, activeTab == Tab.Descubrir, accent) { onSelect(Tab.Descubrir) }
        } else {
            BottomBarItem("Buscar", Icons.Filled.Search, activeTab == Tab.Descubrir, accent) { onSelect(Tab.Descubrir) }
        }
        BottomBarItem("TV", Icons.Filled.LiveTv, activeTab == Tab.TV, accent) { onSelect(Tab.TV) }
        // "Libros" oculto por ahora (aún sin contenido).
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint = if (active) accent else Color(0x99FFFFFF)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        // Pill de acento detrás del ícono cuando está activa (como la web).
        // Borde blanco al presionar (feedback consistente con el resto).
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                .then(if (pressed) Modifier.border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/**
 * Fondo de las barras (header/bottom): con `hazeState` = frosted glass (blur del
 * contenido que pasa por debajo); sin él = degradado/sólido de siempre (TV).
 */
@Composable
private fun Modifier.frostedBar(hazeState: HazeState?, scrolled: Boolean): Modifier =
    if (hazeState != null) {
        hazeEffect(state = hazeState) {
            backgroundColor = Color(0xFF0C0C0E)
            tints = listOf(HazeTint(if (scrolled) Color(0xC00C0C0E) else Color(0x730C0C0E)))
            blurRadius = 28.dp
        }
    } else {
        if (scrolled) background(Color(0xF20C0C0E))
        else background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
    }

@Composable
private fun HomeHeaderPhone(
    accent: Color,
    metrics: Metrics,
    avatarUrl: String?,
    userInitial: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = metrics.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.height(24.dp)) {
            Image(
                painterResource(R.drawable.logo_symbol),
                contentDescription = "BaskeTV",
                modifier = Modifier.height(24.dp),
                colorFilter = ColorFilter.tint(accent),
            )
            Image(painterResource(R.drawable.logo_text), contentDescription = null, modifier = Modifier.height(24.dp))
        }
        Spacer(Modifier.weight(1f))
        UserAvatarButton(avatarUrl = avatarUrl, userInitial = userInitial, size = 36.dp, accent = accent, onClick = onOpenSettings)
    }
}

/**
 * Botón de perfil: avatar del usuario logueado. Si no hay imagen (o falla la
 * carga), muestra un círculo con el color de acento y la inicial del nombre.
 */
@Composable
private fun UserAvatarButton(
    avatarUrl: String?,
    userInitial: String,
    size: Dp,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Focusable(onClick = onClick, modifier = modifier) { highlighted ->
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (highlighted) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarUrl != null) {
                SubcomposeAsyncImage(
                    model = avatarUrl,
                    contentDescription = "Perfil",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    error = { AvatarInitial(userInitial, accent, size) },
                    loading = { AvatarInitial(userInitial, accent, size) },
                )
            } else {
                AvatarInitial(userInitial, accent, size)
            }
        }
    }
}

/** Círculo con el color de acento + la inicial del nombre (fallback sin foto). */
@Composable
private fun AvatarInitial(initial: String, accent: Color, size: Dp) {
    Box(
        modifier = Modifier.fillMaxSize().clip(CircleShape).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

@Composable
private fun HomeHeader(
    accent: Color,
    metrics: Metrics,
    activeTab: Tab,
    onTabFocused: (Tab) -> Unit,
    gearFocus: FocusRequester,
    navHomeFocus: FocusRequester,
    downFocus: FocusRequester?,
    avatarUrl: String?,
    userInitial: String,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downMod = if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = metrics.gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.height(26.dp)) {
            Image(
                painterResource(R.drawable.logo_symbol),
                contentDescription = "BaskeTV",
                modifier = Modifier.height(26.dp),
                colorFilter = ColorFilter.tint(accent),
            )
            Image(painterResource(R.drawable.logo_text), contentDescription = null, modifier = Modifier.height(26.dp))
        }

        Spacer(Modifier.weight(1f))

        // Tabs enfocables (Inicio activo; TV/Libros aún sin contenido).
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0x1AFFFFFF))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NavPill("Inicio", active = activeTab == Tab.Home, accent = accent, onFocused = { onTabFocused(Tab.Home) }, modifier = Modifier.focusRequester(navHomeFocus).then(downMod))
            NavPill(if (BuildConfig.ENABLE_DISCOVER) "Descubrir" else "Buscar", active = activeTab == Tab.Descubrir, accent = accent, onFocused = { onTabFocused(Tab.Descubrir) }, modifier = downMod)
            NavPill("TV", active = activeTab == Tab.TV, accent = accent, onFocused = { onTabFocused(Tab.TV) }, modifier = downMod)
        }
        Spacer(Modifier.width(10.dp))
        Focusable(onClick = onOpenSearch) { highlighted ->
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (highlighted) accent else Color(0x14FFFFFF))
                    .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = if (highlighted) Color.Black else Color(0x99FFFFFF), modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        UserAvatarButton(
            avatarUrl = avatarUrl,
            userInitial = userInitial,
            size = 38.dp,
            accent = accent,
            modifier = Modifier.focusRequester(gearFocus).then(downMod),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun NavPill(label: String, active: Boolean, accent: Color, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(onClick = onFocused, modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() }) { highlighted ->
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) accent else if (highlighted) Color(0x24FFFFFF) else Color.Transparent)
                .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                label,
                color = if (active || highlighted) Color.White else Color(0x8CFFFFFF),
                fontSize = MaterialTheme.typography.labelLarge.fontSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeHero(
    card: HomeCard,
    height: Dp,
    accent: Color,
    metrics: Metrics,
    playFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    onPlay: () -> Unit,
    onInfo: () -> Unit = {},
    onPlayFocused: (Boolean) -> Unit = {},
    onHeroFocused: () -> Unit = {},
    dotCount: Int = 0,
    dotIndex: Int = 0,
) {
    val device = LocalDevice.current
    // heightIn(min): la caja crece si el contenido es más alto que `height`,
    // así el texto nunca se desborda hacia arriba tapándose con el header.
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = height)) {
        AsyncImage(
            model = card.backdropUrl ?: card.imageUrl,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().background(Color(0xFF141414)),
        )
        // El degradado horizontal (oscuro a la izquierda) es para el hero
        // alineado a la izquierda de TV/tablet; en teléfono el contenido va
        // centrado abajo, así que solo se usa el degradado vertical inferior.
        if (!device.isPhone) {
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF2080808), Color(0x66080808), Color.Transparent))))
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    // Degradé más largo: oscurece más arriba y sobre todo abajo
                    // (el negro empieza antes → transición más grande hacia el contenido).
                    colorStops = arrayOf(
                        0f to Color(0x80080808),
                        0.28f to Color.Transparent,
                        0.58f to Color(0x73080808),
                        1f to Color(0xFF080808),
                    ),
                ),
            ),
        )

        val isPhone = device.isPhone
        Column(
            modifier = Modifier
                .align(if (isPhone) Alignment.BottomCenter else Alignment.BottomStart)
                .padding(start = metrics.gutter, end = metrics.gutter, bottom = if (isPhone) 22.dp else 18.dp)
                .then(if (isPhone) Modifier.fillMaxWidth() else Modifier.widthIn(max = metrics.heroTextMaxWidth)),
            horizontalAlignment = if (isPhone) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            card.kicker?.let {
                Text(
                    it + "  ·  RECIÉN AÑADIDO",
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    textAlign = if (isPhone) TextAlign.Center else TextAlign.Start,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                card.title, color = Color.White, fontSize = metrics.heroTitleSize, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                textAlign = if (isPhone) TextAlign.Center else TextAlign.Start,
            )
            if (isPhone) {
                // Meta compacta como la web mobile: "24m · 14 · ★ 8.5" (sin overview).
                val runtimeMin = card.runtimeTicks?.let { (it / 600_000_000L).toInt() }?.takeIf { it > 0 }
                if (runtimeMin != null || card.officialRating != null || card.rating != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        runtimeMin?.let {
                            Text("${it}m", color = Color(0xB3FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        card.officialRating?.let {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(it, color = Color(0xCCFFFFFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        card.rating?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(formatRating(it), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
                card.rating?.let {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatRating(it), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                card.overview?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xCCFFFFFF), fontSize = 15.sp, maxLines = metrics.heroOverviewLines, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Focusable(
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        // Al enfocar Reproducir, subir del todo para ver el banner completo.
                        .onFocusChanged { onPlayFocused(it.isFocused); if (it.isFocused) onHeroFocused() }
                        .then(if (device.isTv) Modifier.focusProperties { up = upFocus; down = downFocus } else Modifier),
                ) { highlighted ->
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
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reproducir", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Focusable(
                    onClick = onInfo,
                    // Al enfocar Info también subir del todo para ver el banner completo.
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onHeroFocused() },
                ) { highlighted ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x24FFFFFF))
                            .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = "Información", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            // En teléfono los dots van ABAJO de los botones (centrados), como la web mobile.
            if (isPhone && dotCount > 1) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(dotCount) { i ->
                        val activeDot = i == dotIndex
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (activeDot) 22.dp else 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (activeDot) Color.White else Color(0x55FFFFFF)),
                        )
                    }
                }
            }
        }

        // Dots del carrusel (TV/tablet): abajo a la derecha, a la altura de los botones.
        if (!device.isPhone && dotCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = metrics.gutter, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(dotCount) { i ->
                    val activeDot = i == dotIndex
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (activeDot) 22.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (activeDot) Color.White else Color(0x55FFFFFF)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowSection(
    row: HomeRow,
    metrics: Metrics,
    onCardClick: (HomeCard) -> Unit,
    onCardFocused: (HomeCard) -> Unit,
    firstCardFocus: FocusRequester?,
    firstCardUp: FocusRequester?,
    restoreCardId: String? = null,
    restoreFocus: FocusRequester? = null,
    onCardLongPress: (HomeCard) -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
) {
    Column(modifier = Modifier.onFocusChanged { onFocusChanged(it.hasFocus) }) {
        Text(
            text = row.title,
            fontSize = metrics.sectionTitleSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = metrics.gutter, end = metrics.gutter, bottom = 12.dp),
        )
        // Al entrar (con D-pad) a esta fila, caer en el primer elemento la primera
        // vez y en el último enfocado al re-entrar.
        val rowFirst = remember { FocusRequester() }
        LazyRow(
            modifier = Modifier.rowFocus(rowFirst),
            contentPadding = PaddingValues(horizontal = metrics.gutter),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
        ) {
            val firstCardId = row.cards.first().id
            items(row.cards, key = { it.id }) { card ->
                val isFirst = card.id == firstCardId
                MediaCard(
                    card = card,
                    portrait = row.portrait,
                    metrics = metrics,
                    onClick = { onCardClick(card) },
                    onFocused = { onCardFocused(card) },
                    onLongPress = { onCardLongPress(card) },
                    focusRequester = firstCardFocus?.takeIf { isFirst },
                    rowFocusRequester = rowFirst.takeIf { isFirst },
                    restoreRequester = restoreFocus?.takeIf { card.id == restoreCardId },
                    upTarget = firstCardUp?.takeIf { isFirst },
                )
            }
        }
    }
}

@Composable
private fun MediaCard(
    card: HomeCard,
    portrait: Boolean,
    metrics: Metrics,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester?,
    upTarget: FocusRequester?,
    rowFocusRequester: FocusRequester? = null,
    restoreRequester: FocusRequester? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val device = LocalDevice.current
    val accent = LocalAccent.current
    val cardWidth = if (portrait) metrics.portraitCardWidth else metrics.landscapeCardWidth
    val ratio = if (portrait) 2f / 3f else 16f / 9f

    Column(modifier = Modifier.width(cardWidth)) {
        Focusable(
            onClick = onClick,
            onLongClick = onLongPress,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused() }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
                .then(if (restoreRequester != null) Modifier.focusRequester(restoreRequester) else Modifier)
                .then(if (upTarget != null && device.isTv) Modifier.focusProperties { up = upTarget } else Modifier),
        ) { highlighted ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (highlighted) Modifier.border(3.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier),
            ) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                )
                if (card.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(card.progress)
                            .height(4.dp)
                            .background(accent),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(card.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        card.subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8B8B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun formatRating(r: Double): String {
    val rounded = (r * 10).toInt() / 10.0
    return rounded.toString()
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}
