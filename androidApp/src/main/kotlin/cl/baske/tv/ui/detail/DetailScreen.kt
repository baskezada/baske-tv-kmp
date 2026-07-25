package cl.baske.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.ui.theme.LocalAccent
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DetailScreen(itemId: String, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val viewModel = koinViewModel<DetailViewModel>(key = itemId) { parametersOf(itemId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val accent = LocalAccent.current
    val playFocus = remember { FocusRequester() }

    BackHandler { onBack() }
    LaunchedEffect(state.loading, state.playTargetId) {
        if (!state.loading && state.playTargetId != null) runCatching { playFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error!!, color = Color.White) }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                item(key = "header") { DetailHeader(state, accent, playFocus, onPlay) }
                if (state.kind == DetailViewModel.Kind.Series) {
                    item(key = "seasons") { SeasonChips(state, accent) { viewModel.selectSeason(it) } }
                    if (state.episodesLoading) {
                        item(key = "epsLoading") {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    } else {
                        items(state.episodes, key = { it.id }) { ep -> EpisodeRow(ep, accent) { onPlay(ep.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(
    state: DetailViewModel.UiState,
    accent: Color,
    playFocus: FocusRequester,
    onPlay: (String) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        AsyncImage(
            model = state.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color(0xFF141414)),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99080808), Color(0xFF080808)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6080808), Color.Transparent))))

        Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, bottom = 24.dp)) {
            // Poster
            AsyncImage(
                model = state.posterUrl,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A)),
            )
            Spacer(Modifier.width(28.dp))
            Column(modifier = Modifier.width(640.dp)) {
                Text(state.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.meta.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.meta, color = Color(0xCCFFFFFF), fontSize = 14.sp)
                }
                state.genres?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                state.overview?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Color(0xB3FFFFFF), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                if (state.playTargetId != null) {
                    Spacer(Modifier.height(18.dp))
                    Focusable(onClick = { onPlay(state.playTargetId!!) }, modifier = Modifier.focusRequester(playFocus)) { focused ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .then(if (focused) Modifier.border(3.dp, accent, RoundedCornerShape(50)) else Modifier)
                                .padding(horizontal = 26.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(state.playLabel, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChips(state: DetailViewModel.UiState, accent: Color, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.seasons.forEach { season ->
            val selected = season.id == state.selectedSeasonId
            Focusable(onClick = { onSelect(season.id) }) { focused ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) accent else Color(0x1FFFFFFF))
                        .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(season.name, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: DetailViewModel.EpisodeItem, accent: Color, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 4.dp)) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (focused) Color(0x14FFFFFF) else Color.Transparent)
                .then(if (focused) Modifier.border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(10.dp)) else Modifier)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1A1A))) {
                AsyncImage(model = ep.imageUrl, contentDescription = ep.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (ep.progress > 0f) {
                    Box(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(ep.progress).height(3.dp).background(accent),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
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

@Composable
private fun Focusable(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(modifier = modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)) {
        content(focused)
    }
}
