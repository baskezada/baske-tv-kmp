package cl.baske.tv.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.platform.requestIfTv
import cl.baske.tv.ui.theme.LocalAccent
import coil3.compose.AsyncImage
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

    BackHandler { onBack() }
    LaunchedEffect(state.loading, state.playTargetId) {
        if (!state.loading && state.playTargetId != null) playFocus.requestIfTv(device)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(state.error!!, color = Color.White) }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                item(key = "header") { DetailHeader(state, accent, metrics, playFocus, onPlay) }
                if (state.kind == DetailViewModel.Kind.Series) {
                    item(key = "seasons") { SeasonChips(state, accent, metrics) { viewModel.selectSeason(it) } }
                    if (state.episodesLoading) {
                        item(key = "epsLoading") {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    } else {
                        items(state.episodes, key = { it.id }) { ep -> EpisodeRow(ep, accent, metrics) { onPlay(ep.id) } }
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
    metrics: cl.baske.tv.ui.platform.Metrics,
    playFocus: FocusRequester,
    onPlay: (String) -> Unit,
) {
    val device = LocalDevice.current
    val headerHeight = if (device.isTv) 420.dp else 320.dp
    Box(Modifier.fillMaxWidth().height(headerHeight)) {
        AsyncImage(
            model = state.backdropUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color(0xFF141414)),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99080808), Color(0xFF080808)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE6080808), Color.Transparent))))

        Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = metrics.gutter, end = metrics.gutter, bottom = 24.dp)) {
            // Poster
            AsyncImage(
                model = state.posterUrl,
                contentDescription = state.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(metrics.detailPosterWidth).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A)),
            )
            Spacer(Modifier.width(if (device.isTv) 28.dp else 16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(state.title, color = Color.White, fontSize = metrics.detailTitleSize, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    Focusable(onClick = { onPlay(state.playTargetId!!) }, modifier = Modifier.focusRequester(playFocus)) { highlighted ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .then(if (highlighted) Modifier.border(3.dp, accent, RoundedCornerShape(50)) else Modifier)
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
private fun SeasonChips(state: DetailViewModel.UiState, accent: Color, metrics: cl.baske.tv.ui.platform.Metrics, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = metrics.gutter, end = metrics.gutter, top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.seasons.forEach { season ->
            val selected = season.id == state.selectedSeasonId
            Focusable(onClick = { onSelect(season.id) }) { highlighted ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) accent else Color(0x1FFFFFFF))
                        .then(if (highlighted) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                ) {
                    Text(season.name, color = if (selected) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: DetailViewModel.EpisodeItem, accent: Color, metrics: cl.baske.tv.ui.platform.Metrics, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.gutter, vertical = 4.dp)) { highlighted ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (highlighted) Color(0x14FFFFFF) else Color.Transparent)
                .then(if (highlighted) Modifier.border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(10.dp)) else Modifier)
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
