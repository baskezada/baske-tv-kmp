package cl.baske.tv.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.platform.requestIfTv
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

    BackHandler { onBack() }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty()) firstFocus.requestIfTv(device)
    }
    // Paginación: cuando el último visible se acerca al final, pedir más.
    LaunchedEffect(gridState) {
        snapshotLastIndex(gridState).collect { last ->
            if (last >= state.items.size - 12) viewModel.loadMore()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error!!, color = Color.White) }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = metrics.gridCellMin),
                contentPadding = PaddingValues(
                    start = metrics.gutter,
                    end = metrics.gutter,
                    top = metrics.headerHeight + 8.dp,
                    bottom = 40.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
                verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing),
            ) {
                itemsIndexed(state.items, key = { _, c -> c.id }) { index, card ->
                    LibraryCard(
                        card = card,
                        onClick = { onOpenCard(card) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }

        Text(
            state.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE6080808))
                .padding(horizontal = metrics.gutter, vertical = 12.dp),
        )
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
            Text(card.title, color = if (highlighted) Color.White else Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun snapshotLastIndex(gridState: androidx.compose.foundation.lazy.grid.LazyGridState) =
    androidx.compose.runtime.snapshotFlow {
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
    }.distinctUntilChanged().map { it }
