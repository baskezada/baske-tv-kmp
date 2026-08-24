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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.remote.SeerrApi
import cl.baske.tv.data.remote.SeerrResult
import cl.baske.tv.ui.home.HomeCard
import cl.baske.tv.ui.home.NavTarget
import cl.baske.tv.ui.platform.Focusable
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.compose.koinInject

/** Objetivo del browse: una plataforma (providerId) o el anime de temporada (null). */
data class ProviderBrowse(val providerId: Int?, val title: String)

/**
 * Pantalla de browse por plataforma / anime de temporada. Reutiliza las cards y
 * el detalle de Descubrir. Va como pantalla propia (RootApp), no como overlay.
 */
@Composable
fun ProviderScreen(
    browse: ProviderBrowse,
    onBack: () -> Unit,
    onOpenLocal: (HomeCard) -> Unit,
    onOpenTmdb: (SeerrResult) -> Unit,
) {
    val client = koinInject<HttpClient>()
    val sessionStore = koinInject<SessionStore>()
    val api = remember { SeerrApi(client, sessionStore) }

    var items by remember(browse) { mutableStateOf<List<SeerrResult>>(emptyList()) }
    var loading by remember(browse) { mutableStateOf(true) }

    BackHandler { onBack() }
    LaunchedEffect(browse) {
        loading = true
        items = runCatching {
            val id = browse.providerId
            if (id != null) coroutineScope {
                val m = async { runCatching { api.moviesByProvider(id).results }.getOrDefault(emptyList()) }
                val t = async { runCatching { api.tvByProvider(id).results }.getOrDefault(emptyList()) }
                m.await() + t.await()
            } else api.seasonalAnime().results
        }.getOrDefault(emptyList()).filterNot { it.isPerson }.filter { it.posterUrl != null }
        loading = false
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
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
                Spacer(Modifier.width(16.dp))
                Text(browse.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Sin resultados", color = Color(0x99FFFFFF)) }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(items, key = { "${it.mediaType}-${it.id}" }) { item ->
                        DiscoverCard(item, fillWidth = true) {
                            val eid = item.embyId
                            if (eid != null) onOpenLocal(HomeCard(id = eid, title = item.displayTitle, subtitle = null, imageUrl = null, navTarget = NavTarget.Detail))
                            else onOpenTmdb(item)
                        }
                    }
                }
            }
        }
    }
}
