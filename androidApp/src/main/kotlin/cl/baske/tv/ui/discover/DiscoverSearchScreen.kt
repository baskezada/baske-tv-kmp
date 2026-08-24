package cl.baske.tv.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.remote.EmbyApi
import cl.baske.tv.data.remote.SeerrApi
import cl.baske.tv.data.remote.SeerrResult
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.NavTarget
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.theme.LocalAccent
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Pantalla de búsqueda (overlay). La abren el ícono de búsqueda del header y la
 * barra de Descubrir. Vacía muestra Tendencias; al escribir, resultados de la
 * biblioteca local (Emby) primero y luego el catálogo (Seerr).
 */
@Composable
fun DiscoverSearchScreen(onBack: () -> Unit, onOpenLocal: (HomeCard) -> Unit, onOpenTmdb: (SeerrResult) -> Unit) {
    val client = koinInject<HttpClient>()
    val sessionStore = koinInject<SessionStore>()
    val embyApi = koinInject<EmbyApi>()
    val api = remember { SeerrApi(client, sessionStore) }
    val session by sessionStore.session.collectAsStateWithLifecycle()
    val serverUrl = session?.serverUrl ?: ""
    val gutter = LocalDevice.current.metrics.gutter
    val focus = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var trending by remember { mutableStateOf<List<SeerrResult>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<SeerrResult>>(emptyList()) }
    var localResults by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    fun openItem(item: SeerrResult) {
        val eid = item.embyId
        if (eid != null) onOpenLocal(HomeCard(id = eid, title = item.displayTitle, subtitle = null, imageUrl = null, navTarget = NavTarget.Detail))
        else onOpenTmdb(item)
    }

    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        trending = runCatching { api.trending().results }.getOrDefault(emptyList())
            .filterNot { it.isPerson }.filter { it.posterUrl != null }
    }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { searchResults = emptyList(); localResults = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(350)
        coroutineScope {
            val uid = session?.userId
            val local = async { if (uid != null) runCatching { embyApi.searchItems(uid, q).items }.getOrDefault(emptyList()) else emptyList() }
            val seerr = async { runCatching { api.search(q).results }.getOrDefault(emptyList()).filterNot { it.isPerson }.filter { it.posterUrl != null } }
            localResults = local.await()
            searchResults = seerr.await()
        }
        searching = false
    }

    val isSearching = query.trim().length >= 2

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Barra: back + campo de búsqueda.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Focusable(onClick = onBack) { hi ->
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x1FFFFFFF))
                            .then(if (hi) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(20.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Buscar películas y series…", color = Color(0x80FFFFFF), fontSize = 15.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                            cursorBrush = SolidColor(LocalAccent.current),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                }
            }

            Box(Modifier.weight(1f)) {
                when {
                    isSearching -> {
                        val catalog = searchResults.filterNot { it.inLibrary }
                        val nothing = localResults.isEmpty() && catalog.isEmpty()
                        when {
                            searching && nothing -> Loader()
                            nothing -> Empty("Sin resultados")
                            else -> ResultGrid(gutter) {
                                if (localResults.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { SearchSectionHeader("En tu biblioteca") }
                                    gridItems(localResults, key = { "L-${it.id}" }) { it2 ->
                                        LocalResultCard(it2, serverUrl) { onOpenLocal(HomeCard(id = it2.id, title = it2.name ?: "", subtitle = null, imageUrl = null, navTarget = NavTarget.Detail)) }
                                    }
                                }
                                if (catalog.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) { SearchSectionHeader("Descubrir") }
                                    gridItems(catalog, key = { "S-${it.id}" }) { it2 -> DiscoverCard(it2, fillWidth = true) { openItem(it2) } }
                                }
                            }
                        }
                    }
                    trending.isEmpty() -> Loader()
                    else -> ResultGrid(gutter) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SearchSectionHeader("Tendencias") }
                        gridItems(trending, key = { "T-${it.id}" }) { it2 -> DiscoverCard(it2, fillWidth = true) { openItem(it2) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultGrid(gutter: androidx.compose.ui.unit.Dp, content: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
private fun Loader() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = Color(0x99FFFFFF)) }
}
