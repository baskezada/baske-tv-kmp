package cl.baske.tv.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.remote.SeerrApi
import cl.baske.tv.data.remote.SeerrEpisode
import cl.baske.tv.data.remote.SeerrResult
import cl.baske.tv.data.remote.SeerrSeason
import cl.baske.tv.ui.components.ModalSheet
import cl.baske.tv.ui.platform.Focusable
import cl.baske.tv.ui.platform.LocalDevice
import cl.baske.tv.ui.theme.LocalAccent
import coil3.compose.AsyncImage
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private enum class ReqModal { None, Movie, Seasons }

/**
 * Detalle de un título del catálogo (TMDB/Seerr) que no está en la biblioteca.
 * Imita la pantalla de detalle: póster centrado, meta, sinopsis y —en series—
 * selector de temporada + lista de episodios. La solicitud abre un modal
 * (confirmación para películas, selector de temporadas para series).
 */
@Composable
fun TmdbDetailScreen(item: SeerrResult, onBack: () -> Unit) {
    val client = koinInject<HttpClient>()
    val sessionStore = koinInject<SessionStore>()
    val api = remember { SeerrApi(client, sessionStore) }
    val accent = LocalAccent.current
    val scope = rememberCoroutineScope()

    var seasons by remember { mutableStateOf<List<SeerrSeason>>(emptyList()) }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    var episodes by remember { mutableStateOf<List<SeerrEpisode>>(emptyList()) }
    var modal by remember { mutableStateOf(ReqModal.None) }
    var seasonPickerOpen by remember { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    var requested by remember { mutableStateOf(item.availabilityLabel != null) }
    var error by remember { mutableStateOf(false) }
    var releaseNote by remember { mutableStateOf<String?>(null) }

    // Back del sistema/control (en TV no hay botón táctil): sin esto el back se
    // propagaba a la actividad y CERRABA la app. Los modales, al componerse
    // después, interceptan el back primero (LIFO) y solo se cierran ellos.
    BackHandler { onBack() }

    LaunchedEffect(item.id) {
        if (item.isTv) {
            val s = runCatching { api.tvSeasons(item.id) }.getOrDefault(emptyList())
            seasons = s
            selectedSeason = s.firstOrNull()?.seasonNumber
        } else {
            val info = runCatching { api.movieReleaseInfo(item.id) }.getOrNull()
            releaseNote = info?.let {
                when {
                    it.digital != null || it.physical != null -> "Fecha de estreno: ${fmtReleaseDate(it.digital ?: it.physical!!)}"
                    it.theatrical != null -> "Solo en cines desde el ${fmtReleaseDate(it.theatrical!!)}, estreno digital sin fecha programada"
                    else -> null
                }
            }
        }
    }
    LaunchedEffect(selectedSeason) {
        val n = selectedSeason ?: return@LaunchedEffect
        episodes = runCatching { api.seasonEpisodes(item.id, n) }.getOrDefault(emptyList())
    }

    fun request(nums: List<Int>?) {
        scope.launch {
            requesting = true; error = false
            val ok = runCatching { api.requestMedia(if (item.isTv) "tv" else "movie", item.id, nums) }.isSuccess
            requesting = false
            modal = ReqModal.None
            if (ok) requested = true else error = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF080808))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Cabecera: backdrop tenue + póster centrado + meta + acción (como el detalle).
            Box(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = item.backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF141414)),
                )
                Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(Color(0xB3080808), Color(0xFF080808)))))

                Column(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.displayTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1A1A1A)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(item.displayTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val seasonsLabel = if (item.isTv && seasons.isNotEmpty()) (if (seasons.size == 1) "1 temp." else "${seasons.size} temps.") else null
                        listOfNotNull(item.year, seasonsLabel).forEachIndexed { i, t ->
                            if (i > 0) Text("·", color = Color(0x66FFFFFF), fontSize = 13.sp)
                            Text(t, color = Color(0xCCFFFFFF), fontSize = 13.sp)
                        }
                        item.voteAverage?.takeIf { it > 0 }?.let {
                            Text("·", color = Color(0x66FFFFFF), fontSize = 13.sp)
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(13.dp))
                            Text(((it * 10).toInt() / 10.0).toString(), color = Color(0xCCFFFFFF), fontSize = 13.sp)
                        }
                    }
                    item.overview?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xB3FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    releaseNote?.let {
                        Spacer(Modifier.height(16.dp))
                        Text(it, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(18.dp))

                    when {
                        requested || item.inLibrary -> StatusPill(item)
                        else -> SolicitarButton(
                            label = if (item.isTv) "Solicitar serie" else "Solicitar película",
                            loading = requesting,
                            onClick = { modal = if (item.isTv) ReqModal.Seasons else ReqModal.Movie },
                        )
                    }
                    if (error) {
                        Spacer(Modifier.height(8.dp))
                        Text("No se pudo solicitar. ¿Tu cuenta está vinculada en Seerr?", color = Color(0xFFFF8080), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            // Series: selector de temporada + episodios (como el detalle de una serie).
            if (item.isTv && seasons.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SeasonSelector(seasons, selectedSeason, onOpen = { seasonPickerOpen = true })
                Spacer(Modifier.height(4.dp))
                if (episodes.isNotEmpty()) {
                    Text("${episodes.size} episodios", color = Color(0x80FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 8.dp))
                }
                episodes.forEach { ep -> TmdbEpisodeRow(ep) }
            }
            Spacer(Modifier.height(28.dp))
        }

        // Back
        Box(Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp)) {
            Focusable(onClick = onBack) { hi ->
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0x66000000))
                        .then(if (hi) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }

        if (seasonPickerOpen) {
            SeasonPickerSheet(seasons, selectedSeason, onSelect = { selectedSeason = it }, onDismiss = { seasonPickerOpen = false })
        }

        when (modal) {
            ReqModal.Movie -> MovieRequestModal(accent, requesting, onDismiss = { modal = ReqModal.None }, onConfirm = { request(null) })
            ReqModal.Seasons -> SeasonsRequestModal(seasons, accent, onDismiss = { modal = ReqModal.None }, onRequest = { request(it) })
            ReqModal.None -> {}
        }
    }
}

@Composable
private fun SolicitarButton(label: String, loading: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Focusable(onClick = { if (!loading) onClick() }) { hi ->
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .then(if (hi) Modifier.border(3.dp, accent, RoundedCornerShape(50)) else Modifier)
                .padding(horizontal = 30.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusPill(item: SeerrResult) {
    val available = item.inLibrary
    val label = when {
        available -> "Disponible en la biblioteca"
        else -> "Solicitada, será incorporada apenas se pueda"
    }
    val icon = if (available) Icons.Filled.Check else Icons.Filled.Schedule
    Row(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (available) Color(0xFF22C55E) else Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SeasonSelector(seasons: List<SeerrSeason>, selected: Int?, onOpen: () -> Unit) {
    val current = seasons.firstOrNull { it.seasonNumber == selected } ?: seasons.firstOrNull()
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Focusable(onClick = onOpen) { hi ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                    .then(if (hi) Modifier.border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current?.let { it.name?.takeIf { n -> n.isNotBlank() } ?: "Temporada ${it.seasonNumber}" } ?: "Temporada", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color(0x99FFFFFF), modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Modal de selección de temporada (para ver episodios), estilo detalle. */
@Composable
private fun SeasonPickerSheet(seasons: List<SeerrSeason>, selected: Int?, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val accent = LocalAccent.current
    ModalSheet(title = "Temporadas", onDismiss = onDismiss) {
        seasons.forEach { s ->
            val n = s.seasonNumber ?: return@forEach
            Focusable(onClick = { onSelect(n); onDismiss() }, modifier = Modifier.fillMaxWidth()) { hi ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (hi) Color(0x26FFFFFF) else Color(0x14FFFFFF)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(if (n == selected) accent else Color.Transparent))
                    Spacer(Modifier.width(14.dp))
                    Text(s.name?.takeIf { it.isNotBlank() } ?: "Temporada $n", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    s.episodeCount?.let {
                        Text("$it ep.", color = Color(0x80FFFFFF), fontSize = 12.sp)
                        Spacer(Modifier.width(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// "d MMM yyyy" en español desde un ISO ("2026-08-21T..." → "21 ago 2026").
private fun fmtReleaseDate(iso: String): String {
    val parts = iso.take(10).split("-")
    if (parts.size != 3) return iso.take(10)
    val months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    val m = parts[1].toIntOrNull() ?: return iso.take(10)
    val d = parts[2].toIntOrNull() ?: return iso.take(10)
    return "$d ${months.getOrElse(m - 1) { parts[1] }} ${parts[0]}"
}

@Composable
private fun TmdbEpisodeRow(ep: SeerrEpisode) {
    // Episodios NO disponibles (no están en la biblioteca) → atenuados/deshabilitados.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).alpha(0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
            AsyncImage(model = ep.stillUrl, contentDescription = ep.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("${ep.episodeNumber ?: ""}. ${ep.name ?: ""}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ep.airDate?.take(10)?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, color = Color(0x80FFFFFF), fontSize = 12.sp)
            }
        }
    }
}

// ---- Modales de solicitud (scrim + card con back + título, como el web) ----

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun RequestModalScaffold(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val device = LocalDevice.current
    // En TV el foco del D-pad debe saltar al modal apenas abre y quedar atrapado
    // adentro (si no, sigue detrás del scrim, invisible).
    val cardFocus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    LaunchedEffect(Unit) { if (device.isTv) runCatching { cardFocus.requestFocus() } }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xB3000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp).widthIn(max = 440.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF17171B))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .focusRequester(cardFocus)
                .focusProperties { exit = { if (device.isTv) FocusRequester.Cancel else FocusRequester.Default } }
                .focusGroup()
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Focusable(onClick = onDismiss) { hi ->
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0x1FFFFFFF))
                            .then(if (hi) Modifier.border(2.dp, Color(0xB3FFFFFF), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun MovieRequestModal(accent: Color, requesting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    RequestModalScaffold(title = "Solicitar película", onDismiss = onDismiss) {
        InfoRow(Icons.Filled.Add, "Se enviará tu solicitud")
        Spacer(Modifier.height(10.dp))
        InfoRow(Icons.Filled.Schedule, "Pasará por un proceso de aprobación")
        Spacer(Modifier.height(10.dp))
        InfoRow(Icons.Filled.Downloading, "Se descargará cuando esté disponible")
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModalBtn("Cancelar", filledColor = Color(0x1FFFFFFF), textColor = Color.White, modifier = Modifier.weight(1f), onClick = onDismiss)
            ModalBtn(if (requesting) "Solicitando…" else "Confirmar solicitud", filledColor = accent, textColor = Color.Black, modifier = Modifier.weight(1f), enabled = !requesting, onClick = onConfirm)
        }
    }
}

@Composable
private fun SeasonsRequestModal(seasons: List<SeerrSeason>, accent: Color, onDismiss: () -> Unit, onRequest: (List<Int>) -> Unit) {
    val nums = seasons.mapNotNull { it.seasonNumber }
    RequestModalScaffold(title = "Solicitar temporadas", onDismiss = onDismiss) {
        // Solo la primera temporada.
        nums.firstOrNull()?.let { first ->
            OptionCard(
                icon = Icons.Filled.PlayArrow, iconTint = accent,
                title = "Solo temporada $first: quiero empezar a ver esta serie",
                onClick = { onRequest(listOf(first)) },
            )
            Spacer(Modifier.height(10.dp))
        }
        // Toda la serie.
        OptionCard(
            icon = Icons.Filled.Downloading, iconTint = accent,
            title = "Solicitar toda la serie",
            subtitle = "${nums.size} temporadas disponibles",
            onClick = { onRequest(nums) },
        )
        Spacer(Modifier.height(18.dp))
        Text("TEMPORADAS DISPONIBLES", color = Color(0x66FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        // Grilla simple (3 por fila) de tiles de temporada.
        seasons.chunked(3).forEach { rowSeasons ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                rowSeasons.forEach { s ->
                    val n = s.seasonNumber ?: 0
                    Focusable(onClick = { onRequest(listOf(n)) }, modifier = Modifier.weight(1f)) { hi ->
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                                .then(if (hi) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("$n", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("TEMP.", color = Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            s.episodeCount?.let { Text("$it ep", color = Color(0x66FFFFFF), fontSize = 11.sp) }
                        }
                    }
                }
                repeat(3 - rowSeasons.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x1F3B82F6)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF6FA8FF), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color(0xE6FFFFFF), fontSize = 14.sp)
    }
}

@Composable
private fun OptionCard(icon: ImageVector, iconTint: Color, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth()) { hi ->
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))
                .then(if (hi) Modifier.border(2.dp, Color(0x66FFFFFF), RoundedCornerShape(12.dp)) else Modifier)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, color = Color(0x80FFFFFF), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ModalBtn(label: String, filledColor: Color, textColor: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Focusable(onClick = { if (enabled) onClick() }, modifier = modifier) { hi ->
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(filledColor)
                .then(if (hi) Modifier.border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(50)) else Modifier)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}
