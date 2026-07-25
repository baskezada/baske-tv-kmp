@file:OptIn(ExperimentalTvMaterial3Api::class)

package cl.baske.tv.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import cl.baske.tv.R
import cl.baske.tv.data.HomeMode
import cl.baske.tv.data.PrefsStore
import cl.baske.tv.ui.theme.LocalAccent
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private enum class Tab { Home, TV, Libros }

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit = {},
    onCardClick: (HomeCard) -> Unit = {},
    onPlayItem: (HomeCard) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefsStore = koinInject<PrefsStore>()
    val prefs by prefsStore.prefs.collectAsStateWithLifecycle()
    val accent = LocalAccent.current

    val firstCardFocus = remember { FocusRequester() }
    val gearFocus = remember { FocusRequester() }
    val heroPlayFocus = remember { FocusRequester() }
    val navHomeFocus = remember { FocusRequester() }
    var heroCard by remember { mutableStateOf<HomeCard?>(null) }
    var activeTab by remember { mutableStateOf(Tab.Home) }

    val hasContent = state.rows.isNotEmpty()
    val featured = state.rows.firstOrNull()?.cards?.firstOrNull()
    val effectiveHero = when (prefs.homeMode) {
        HomeMode.Vitrina -> heroCard ?: featured
        HomeMode.Banner -> featured
        HomeMode.SinBanner -> null
    }
    val hasHero = effectiveHero != null

    // Foco inicial en la primera fila (NO en Reproducir): así al cargar el
    // hero no se scrollea hacia arriba y el banner se ve completo.
    LaunchedEffect(hasContent) {
        if (hasContent) runCatching { firstCardFocus.requestFocus() }
    }
    // Target hacia el que baja el header (Reproducir si hay hero, si no la card).
    val belowHeaderFocus = if (hasHero) heroPlayFocus else firstCardFocus
    // Al subir desde la primera fila: al Reproducir del hero, o directo a los tabs.
    val firstCardUpTarget = if (hasHero) heroPlayFocus else navHomeFocus

    MaterialTheme(colorScheme = darkColorScheme()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF080808))) {
            // Reservamos la altura del header para que el hero arranque debajo y
            // se vea completo (antes el hero full-bleed quedaba tapado arriba).
            val headerHeight = 72.dp
            val heroHeight = (maxHeight - headerHeight) * 0.58f

            when {
                activeTab != Tab.Home -> CenterMessage("Próximamente")
                state.loading && state.rows.isEmpty() -> CenterMessage("Cargando…")
                state.rows.isEmpty() && state.error != null -> CenterMessage(state.error!!)
                else -> {
                    val firstRowId = state.rows.first().id
                    LazyColumn(
                        contentPadding = PaddingValues(top = headerHeight, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        effectiveHero?.let { hero ->
                            item(key = "hero") {
                                HomeHero(
                                    card = hero,
                                    height = heroHeight,
                                    accent = accent,
                                    playFocus = heroPlayFocus,
                                    upFocus = navHomeFocus,
                                    downFocus = firstCardFocus,
                                    onPlay = { onPlayItem(hero) },
                                )
                            }
                        }
                        items(state.rows, key = { it.id }) { row ->
                            val isFirstRow = row.id == firstRowId
                            RowSection(
                                row = row,
                                onCardClick = onCardClick,
                                onCardFocused = { card ->
                                    if (prefs.homeMode == HomeMode.Vitrina) heroCard = card
                                },
                                firstCardFocus = if (isFirstRow) firstCardFocus else null,
                                firstCardUp = if (isFirstRow) firstCardUpTarget else null,
                            )
                        }
                    }
                }
            }

            HomeHeader(
                accent = accent,
                activeTab = activeTab,
                onTabFocused = { activeTab = it },
                gearFocus = gearFocus,
                navHomeFocus = navHomeFocus,
                downFocus = belowHeaderFocus.takeIf { hasContent && activeTab == Tab.Home },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

@Composable
private fun HomeHeader(
    accent: Color,
    activeTab: Tab,
    onTabFocused: (Tab) -> Unit,
    gearFocus: FocusRequester,
    navHomeFocus: FocusRequester,
    downFocus: FocusRequester?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downMod = if (downFocus != null) Modifier.focusProperties { down = downFocus } else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .padding(horizontal = 40.dp, vertical = 14.dp),
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
            NavPill("TV", active = activeTab == Tab.TV, accent = accent, onFocused = { onTabFocused(Tab.TV) }, modifier = downMod)
            NavPill("Libros", active = activeTab == Tab.Libros, accent = accent, onFocused = { onTabFocused(Tab.Libros) }, modifier = downMod)
        }
        Spacer(Modifier.width(10.dp))
        DecorCircle(Icons.Filled.Search)

        Spacer(Modifier.weight(1f))

        DecorCircle(Icons.Filled.FavoriteBorder)
        Spacer(Modifier.width(10.dp))
        DecorCircle(Icons.Filled.NotificationsNone)
        Spacer(Modifier.width(10.dp))
        FocusableBox(
            onClick = onOpenSettings,
            modifier = Modifier.focusRequester(gearFocus).then(downMod),
        ) { focused ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (focused) accent else Color(0x17FFFFFF))
                    .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun NavPill(label: String, active: Boolean, accent: Color, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    FocusableBox(onClick = onFocused, modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() }) { focused ->
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) accent else if (focused) Color(0x24FFFFFF) else Color.Transparent)
                .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 18.dp, vertical = 7.dp),
        ) {
            Text(
                label,
                color = if (active) Color.Black else if (focused) Color.White else Color(0x8CFFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DecorCircle(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0x14FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun HomeHero(
    card: HomeCard,
    height: Dp,
    accent: Color,
    playFocus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    onPlay: () -> Unit,
) {
    // heightIn(min): la caja crece si el contenido es más alto que `height`,
    // así el texto nunca se desborda hacia arriba tapándose con el header.
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = height)) {
        AsyncImage(
            model = card.backdropUrl ?: card.imageUrl,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().background(Color(0xFF141414)),
        )
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF2080808), Color(0x66080808), Color.Transparent))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x66080808), Color.Transparent, Color(0xFF080808)))))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 18.dp)
                .widthIn(max = 640.dp),
        ) {
            card.kicker?.let {
                Text(it + "  ·  RECIÉN AÑADIDO", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
            }
            if (card.logoUrl != null) {
                AsyncImage(
                    model = card.logoUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 100.dp).widthIn(max = 440.dp),
                )
            } else {
                Text(card.title, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
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
                Text(it, color = Color(0xCCFFFFFF), fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusableBox(
                    onClick = onPlay,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        .focusProperties { up = upFocus; down = downFocus },
                ) { focused ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .then(if (focused) Modifier.border(3.dp, accent, RoundedCornerShape(50)) else Modifier)
                            .padding(horizontal = 26.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reproducir", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(Color(0x24FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun RowSection(
    row: HomeRow,
    onCardClick: (HomeCard) -> Unit,
    onCardFocused: (HomeCard) -> Unit,
    firstCardFocus: FocusRequester?,
    firstCardUp: FocusRequester?,
) {
    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(start = 40.dp, bottom = 12.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val firstCardId = row.cards.first().id
            items(row.cards, key = { it.id }) { card ->
                val isFirst = card.id == firstCardId
                MediaCard(
                    card = card,
                    portrait = row.portrait,
                    onClick = { onCardClick(card) },
                    onFocused = { onCardFocused(card) },
                    focusRequester = firstCardFocus?.takeIf { isFirst },
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
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester?,
    upTarget: FocusRequester?,
) {
    val accent = LocalAccent.current
    val cardWidth = if (portrait) 130.dp else 250.dp
    val ratio = if (portrait) 2f / 3f else 16f / 9f

    Column(modifier = Modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (it.isFocused) onFocused() }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
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

@Composable
private fun FocusableBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier = modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        content(focused)
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
