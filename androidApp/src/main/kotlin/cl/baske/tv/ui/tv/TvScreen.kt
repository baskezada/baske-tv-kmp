package cl.baske.tv.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.core.embyImageUrl
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import coil3.compose.AsyncImage
import org.koin.compose.koinInject

/** Tab "TV": canales de Live TV del servidor. Tocar un canal lo reproduce en vivo. */
@Composable
fun TvScreen(
    topPadding: Dp,
    bottomPadding: Dp,
    gridState: LazyGridState = rememberLazyGridState(),
    upFocus: FocusRequester? = null,
    onPlayChannel: (String) -> Unit,
) {
    // Índice hasta el cual las cards están en la PRIMERA fila (para que "arriba" las
    // lleve al header, no a la fila de abajo).
    val firstRowCount by remember {
        derivedStateOf { gridState.layoutInfo.visibleItemsInfo.count { it.row == 0 }.coerceAtLeast(1) }
    }
    val api = koinInject<EmbyApi>()
    val sessionStore = koinInject<SessionStore>()
    val session by sessionStore.session.collectAsStateWithLifecycle()
    val gutter = LocalDevice.current.metrics.gutter

    var channels by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        val uid = session?.userId
        channels = if (uid != null) runCatching { api.getLiveTvChannels(uid).items }.getOrDefault(emptyList()) else emptyList()
        loading = false
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            channels.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay canales de TV", color = Color(0x99FFFFFF))
            }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(start = gutter, end = gutter, top = topPadding + 8.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(channels, key = { _, it -> it.id }) { index, ch ->
                    ChannelCard(
                        ch,
                        session?.serverUrl ?: "",
                        upFocus = upFocus?.takeIf { index < firstRowCount },
                    ) { onPlayChannel(ch.id) }
                }
            }
        }
    }
}

@Composable
private fun ChannelCard(
    channel: BaseItemDto,
    serverUrl: String,
    upFocus: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val device = LocalDevice.current
    val tag = channel.imageTags?.get("Primary")
    val url = embyImageUrl(serverUrl, channel.id, "Primary", tag, 320)
    Column {
        Focusable(
            onClick = onClick,
            modifier = if (upFocus != null && device.isTv) Modifier.focusProperties { up = upFocus } else Modifier,
        ) { highlighted ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF16161A))
                    .then(if (highlighted) Modifier.border(3.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (tag != null) {
                    // Logo del canal: Fit (los logos suelen tener transparencia/relación propia).
                    AsyncImage(
                        model = url,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                } else {
                    Text(channel.name ?: "", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                }
            }
        }
        Box(Modifier.height(6.dp))
        Text(channel.name ?: "", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
