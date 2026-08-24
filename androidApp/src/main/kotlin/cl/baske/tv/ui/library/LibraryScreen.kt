package cl.baske.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.components.ModalOption
import cl.baske.tv.ui.components.ModalSheet
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.platform.Metrics
import cl.baske.tv.ui.platform.requestIfTv
import cl.baske.tv.ui.theme.LocalAccent
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LibraryScreen(libraryId: String, onBack: () -> Unit, onOpenCard: (HomeCard) -> Unit) {
    val viewModel = koinViewModel<LibraryViewModel>(key = libraryId) { parametersOf(libraryId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val firstFocus = remember { FocusRequester() }
    val device = LocalDevice.current
    val metrics = device.metrics
    // Restaurar foco al volver de un item (ver HomeScreen): guardamos el id al
    // navegar y lo re-enfocamos al reaparecer.
    var restoreCardId by rememberSaveable { mutableStateOf<String?>(null) }
    val restoreFocus = remember { FocusRequester() }

    BackHandler { onBack() }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty() && device.isTv) {
            if (restoreCardId != null) {
                withFrameNanos { }
                runCatching { restoreFocus.requestFocus() }
            } else {
                firstFocus.requestIfTv(device)
            }
        }
    }
    LaunchedEffect(gridState) {
        snapshotLastIndex(gridState).collect { last ->
            // Solo paginar cuando ya hay ítems (si no, el grid vacío dispara
            // loadMore al instante y pisa la carga inicial — ver LibraryViewModel).
            if (state.items.isNotEmpty() && last >= state.items.size - 12) viewModel.loadMore()
        }
    }

    var modal by remember { mutableStateOf(LibModal.None) }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = metrics.gridCellMin),
            contentPadding = PaddingValues(
                start = metrics.gutter,
                end = metrics.gutter,
                top = 8.dp,
                bottom = 40.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryHeader(
                    state = state,
                    onBack = onBack,
                    onOpenLetter = { modal = LibModal.Letter },
                    onOpenSort = { modal = LibModal.Sort },
                )
            }
            if (state.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            } else if (state.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text(state.error!!, color = Color.White)
                    }
                }
            } else {
                itemsIndexed(state.items, key = { _, c -> c.id }) { index, card ->
                    LibraryCard(
                        card = card,
                        onClick = { restoreCardId = card.id; onOpenCard(card) },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .then(if (card.id == restoreCardId) Modifier.focusRequester(restoreFocus) else Modifier),
                    )
                }
            }
        }

        when (modal) {
            LibModal.Letter -> ModalSheet("Filtrar por letra", onDismiss = { modal = LibModal.None }) {
                LetterGrid(
                    selected = state.letter,
                    onPick = { l -> viewModel.setLetter(l); modal = LibModal.None },
                )
            }
            LibModal.Sort -> ModalSheet("Ordenar por", onDismiss = { modal = LibModal.None }) {
                LibraryViewModel.SORT_OPTIONS.forEach { opt ->
                    ModalOption(opt.label, selected = opt.label == state.sortLabel) {
                        viewModel.setSort(opt); modal = LibModal.None
                    }
                }
            }
            LibModal.None -> {}
        }
    }
}

private enum class LibModal { None, Letter, Sort }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LetterGrid(selected: String?, onPick: (String?) -> Unit) {
    val accent = LocalAccent.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val entries = listOf<Pair<String, String?>>("Todas" to null) + LibraryViewModel.LETTERS.map { it to it }
        entries.forEach { (label, value) ->
            val active = value == selected
            Focusable(onClick = { onPick(value) }) { highlighted ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) accent else if (highlighted) Color(0x33FFFFFF) else Color(0x14FFFFFF))
                        .then(if (highlighted && !active) Modifier.border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(10.dp)) else Modifier)
                        .padding(horizontal = if (label.length > 1) 14.dp else 0.dp, vertical = 10.dp)
                        .then(if (label.length > 1) Modifier else Modifier.width(40.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (active) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    state: LibraryViewModel.UiState,
    onBack: () -> Unit,
    onOpenLetter: () -> Unit,
    onOpenSort: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, bottom = 8.dp)) {
        Focusable(onClick = onBack) { highlighted ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .then(if (highlighted) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(state.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(label = state.letter?.let { "Letra: $it" } ?: "Filtrar por letra", active = state.letter != null, onClick = onOpenLetter)
            PillButton(label = "Ordenar por", active = false, onClick = onOpenSort)
        }
        Spacer(Modifier.height(10.dp))
        Text("${state.total} elementos", color = Color(0x80FFFFFF), fontSize = 12.sp)
    }
}

@Composable
private fun PillButton(label: String, active: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Focusable(onClick = onClick) { highlighted ->
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) accent.copy(alpha = 0.22f) else Color(0x1FFFFFFF))
                .then(if (highlighted) Modifier.border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (active) accent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = if (active) accent else Color(0x99FFFFFF), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LibraryCard(card: HomeCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(onClick = onClick, modifier = modifier) { highlighted ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A1A))
                    .then(if (highlighted) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier),
            ) {
                AsyncImage(model = card.imageUrl, contentDescription = card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(6.dp))
            Text(card.title, color = if (highlighted) Color.White else Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            card.subtitle?.let {
                Text(it, color = Color(0x80FFFFFF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun snapshotLastIndex(gridState: androidx.compose.foundation.lazy.grid.LazyGridState) =
    androidx.compose.runtime.snapshotFlow {
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }.distinctUntilChanged().map { it }
