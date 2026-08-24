package cl.baske.tv.ui.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.core.embyImageUrl
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.NavTarget
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * "Buscar": búsqueda en la biblioteca de Emby. Es la tab que reemplaza a
 * "Descubrir" cuando el plugin/flag está desactivado (no usa EmbySeerr).
 */
@Composable
fun SearchScreen(topPadding: Dp, bottomPadding: Dp, onOpenCard: (HomeCard) -> Unit) {
    val api = koinInject<EmbyApi>()
    val sessionStore = koinInject<SessionStore>()
    val session by sessionStore.session.collectAsStateWithLifecycle()
    val gutter = LocalDevice.current.metrics.gutter

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // Búsqueda con debounce: espera a que el usuario deje de tipear.
    androidx.compose.runtime.LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(350)
        val uid = session?.userId
        results = if (uid != null) runCatching { api.searchItems(uid, q).items }.getOrDefault(emptyList()) else emptyList()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(top = topPadding + 8.dp),
    ) {
        // Barra de búsqueda.
        Row(
            modifier = Modifier
                .padding(horizontal = gutter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x1FFFFFFF))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.width(22.dp).height(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Buscar películas y series…", color = Color(0x80FFFFFF), fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(start = gutter, end = gutter, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(results, key = { it.id }) { item ->
                SearchCard(item, session?.serverUrl ?: "") {
                    onOpenCard(HomeCard(id = item.id, title = item.name ?: "", subtitle = null, imageUrl = null, navTarget = NavTarget.Detail))
                }
            }
        }
    }
}

@Composable
private fun SearchCard(item: BaseItemDto, serverUrl: String, onClick: () -> Unit) {
    val tag = item.imageTags?.get("Primary")
    val url = embyImageUrl(serverUrl, item.id, "Primary", tag, 320)
    Column {
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
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name ?: "", color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.productionYear?.let { Text(it.toString(), color = Color(0x80FFFFFF), fontSize = 11.sp) }
    }
}
