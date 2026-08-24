package cl.baske.tv.data.remote

import cl.baske.tv.core.DeviceInfo
import cl.baske.tv.core.DeviceProfileProvider
import cl.baske.tv.core.buildEmbyAuthHeader
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.AuthenticateByNameRequest
import cl.baske.tv.data.model.AuthenticationResult
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.model.ItemsResponse
import cl.baske.tv.data.model.PlaybackInfoRequest
import cl.baske.tv.data.model.PlaybackInfoResponse
import cl.baske.tv.data.model.TranscodingProfile
import cl.baske.tv.data.model.PlaybackReport
import cl.baske.tv.data.model.PublicSystemInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Superficie REST de Emby. Los métodos sin sesión (público/login) reciben la
 * `baseUrl`; los autenticados la leen de la sesión activa junto con el token.
 */
class EmbyApi(
    private val client: HttpClient,
    private val device: DeviceInfo,
    private val sessionStore: SessionStore,
    private val profileProvider: DeviceProfileProvider,
) {
    // ---- Sin autenticación (flujo de conexión) ----

    suspend fun getPublicSystemInfo(baseUrl: String): PublicSystemInfo =
        client.get("$baseUrl/System/Info/Public").body()

    suspend fun authenticateByName(
        baseUrl: String,
        username: String,
        password: String,
    ): AuthenticationResult =
        client.post("$baseUrl/Users/AuthenticateByName") {
            header("X-Emby-Authorization", buildEmbyAuthHeader(device))
            contentType(ContentType.Application.Json)
            setBody(AuthenticateByNameRequest(username, password))
        }.body()

    // ---- Home (requieren sesión) ----

    /** Bibliotecas del usuario. */
    suspend fun getViews(userId: String): ItemsResponse =
        authGet("/Users/$userId/Views")

    /** Continuar viendo. */
    suspend fun getResume(userId: String): ItemsResponse =
        authGet("/Users/$userId/Items/Resume") {
            parameter("Limit", 18)
            parameter("Recursive", true)
            parameter("MediaTypes", "Video")
            parameter("Fields", HOME_FIELDS)
            parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            parameter("ImageTypeLimit", 1)
        }

    /** Siguiente episodio de series empezadas. */
    suspend fun getNextUp(userId: String): ItemsResponse =
        authGet("/Shows/NextUp") {
            parameter("UserId", userId)
            parameter("Limit", 24)
            parameter("Fields", HOME_FIELDS)
            parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            parameter("ImageTypeLimit", 1)
        }

    /** Recién agregado de una biblioteca concreta (array plano). */
    suspend fun getLatestForLibrary(userId: String, parentId: String): List<BaseItemDto> =
        authGet("/Users/$userId/Items/Latest") {
            parameter("ParentId", parentId)
            parameter("Limit", 16)
            parameter("Fields", HOME_FIELDS)
            parameter("EnableImageTypes", "Primary,Backdrop,Thumb,Logo")
            parameter("ImageTypeLimit", 1)
            parameter("GroupItems", true)
        }

    // ---- Detalle / Biblioteca (requieren sesión) ----

    /** Ficha de un ítem (película/serie/libro). */
    suspend fun getItemDetail(userId: String, itemId: String): BaseItemDto =
        authGet("/Users/$userId/Items/$itemId") {
            parameter("Fields", DETAIL_FIELDS)
            parameter("EnableImageTypes", "Primary,Backdrop,Logo,Thumb")
        }

    /** Temporadas de una serie. */
    suspend fun getSeasons(seriesId: String, userId: String): ItemsResponse =
        authGet("/Shows/$seriesId/Seasons") {
            parameter("UserId", userId)
            parameter("Fields", "ChildCount")
        }

    /** Episodios de una temporada. */
    suspend fun getEpisodes(seriesId: String, seasonId: String, userId: String): ItemsResponse =
        authGet("/Shows/$seriesId/Episodes") {
            parameter("SeasonId", seasonId)
            parameter("UserId", userId)
            parameter("Fields", "Overview,RunTimeTicks,UserData,PremiereDate,CommunityRating,IndexNumber,ParentIndexNumber")
            parameter("EnableImageTypes", "Primary,Backdrop,Thumb")
        }

    /**
     * Todos los episodios de una serie, planos y en orden (temporada→episodio),
     * omitiendo SeasonId. Se usa para el AUTO-SIGUIENTE del player: se ubica el
     * episodio actual por id y el siguiente es el que va después, cruzando el
     * límite de temporada solo. Incluye watched aunque ya se hayan visto (a
     * diferencia de NextUp, que devuelve el próximo NO visto).
     */
    suspend fun getSeriesEpisodes(seriesId: String, userId: String): ItemsResponse =
        authGet("/Shows/$seriesId/Episodes") {
            parameter("UserId", userId)
            parameter("Fields", "IndexNumber,ParentIndexNumber")
        }

    /** Próximo episodio a ver de una serie (para el botón Reproducir/Continuar). */
    suspend fun getSeriesNextUp(userId: String, seriesId: String): ItemsResponse =
        authGet("/Shows/NextUp") {
            parameter("UserId", userId)
            parameter("SeriesId", seriesId)
            parameter("Limit", 1)
            parameter("Fields", "IndexNumber,ParentIndexNumber,UserData,RunTimeTicks")
        }

    /** Ítems de una biblioteca, paginado, con orden y filtro por letra opcionales. */
    suspend fun getLibraryItems(
        userId: String,
        parentId: String,
        includeItemTypes: String,
        startIndex: Int,
        limit: Int,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        nameStartsWith: String? = null,
        nameLessThan: String? = null,
    ): ItemsResponse =
        authGet("/Users/$userId/Items") {
            parameter("ParentId", parentId)
            parameter("Recursive", true)
            if (includeItemTypes.isNotEmpty()) parameter("IncludeItemTypes", includeItemTypes)
            parameter("SortBy", sortBy)
            parameter("SortOrder", sortOrder)
            parameter("Limit", limit)
            parameter("StartIndex", startIndex)
            // Filtro A-Z server-side (igual que la web): una letra exacta, o
            // NameLessThan 'A' para el grupo "#" (números/símbolos).
            nameStartsWith?.let { parameter("NameStartsWith", it) }
            nameLessThan?.let { parameter("NameLessThan", it) }
            parameter("Fields", HOME_FIELDS)
            parameter("EnableImageTypes", "Primary,Backdrop,Thumb")
        }

    // ---- Player (requieren sesión) ----

    /** Canales de Live TV del servidor Emby (con logos). */
    suspend fun getLiveTvChannels(userId: String): ItemsResponse =
        authGet("/LiveTv/Channels") {
            parameter("UserId", userId)
            parameter("EnableImages", true)
            parameter("EnableUserData", false)
            parameter("Limit", 200)
        }

    /** Búsqueda en la biblioteca de Emby (películas + series). Siempre disponible. */
    suspend fun searchItems(userId: String, query: String): ItemsResponse =
        authGet("/Users/$userId/Items") {
            parameter("SearchTerm", query)
            parameter("Recursive", true)
            parameter("IncludeItemTypes", "Movie,Series")
            parameter("Limit", 48)
            parameter("Fields", "ProductionYear,PrimaryImageAspectRatio")
            parameter("ImageTypeLimit", 1)
            parameter("EnableTotalRecordCount", false)
        }

    /** Detalle del ítem: título, RunTimeTicks, posición de resume y capítulos
     *  (incluye marcadores IntroStart/IntroEnd para el botón "Saltar intro"). */
    suspend fun getItem(userId: String, itemId: String): BaseItemDto =
        authGet("/Users/$userId/Items/$itemId") { parameter("Fields", "Chapters") }

    /**
     * PlaybackInfo: da el `mediaSourceId` real (mediasource_XXX) y el
     * `PlaySessionId`. Ahora manda un DeviceProfile (armado según lo que el
     * equipo decodifica): Emby decide por MediaSource si entrega direct-play
     * (SupportsDirectPlay) o una `TranscodingUrl` (HLS) para lo que no soporta.
     */
    suspend fun getPlaybackInfo(
        itemId: String,
        userId: String,
        live: Boolean = false,
        forceTranscode: Boolean = false,
        maxBitrate: Int? = null,
    ): PlaybackInfoResponse {
        val session = sessionStore.session.value ?: error("No hay sesión activa")
        val baseProfile = profileProvider.deviceProfile()
        // Live TV: pedir transcode PROGRESIVO (.ts continuo por HTTP) en vez de HLS.
        // El playlist HLS (.m3u8) llega comprimido (gzip/deflate) y el cliente
        // HTTP/2 de libVLC no lo descomprime → URLs de segmentos corruptas → negro.
        // El .ts progresivo es un stream único sin playlist, así que no le afecta.
        // VOD sigue con HLS (protocol=hls del perfil) para conservar el seek.
        // forceTranscode: vacía direct-play → Emby SIEMPRE transcodifica. Se usa
        // como fallback cuando el direct-play falló (equipo que declara un decoder
        // que en realidad no puede con el archivo, p.ej. HEVC 10-bit en tablets).
        val profile = when {
            live -> baseProfile.copy(
                transcodingProfiles = listOf(
                    TranscodingProfile(container = "ts", videoCodec = "h264", audioCodec = "aac,mp3", protocol = "http"),
                ),
            )
            // Calidad manual (maxBitrate) o fallback: forzar transcode. Con maxBitrate,
            // Emby transcodifica al bitrate/resolución que pida el selector de calidad.
            forceTranscode || maxBitrate != null -> baseProfile.copy(directPlayProfiles = emptyList())
            else -> baseProfile
        }
        return client.post("${session.serverUrl}/Items/$itemId/PlaybackInfo") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            parameter("UserId", userId)
            // Live TV: los canales son streams que hay que ABRIR. AutoOpenLiveStream
            // hace que Emby abra el stream y devuelva la TranscodingUrl lista.
            // Para VOD es no-op, así que se manda siempre.
            parameter("AutoOpenLiveStream", true)
            if (maxBitrate != null) parameter("MaxStreamingBitrate", maxBitrate)
            contentType(ContentType.Application.Json)
            setBody(
                PlaybackInfoRequest(
                    userId = userId,
                    deviceId = device.deviceId,
                    deviceProfile = profile,
                ),
            )
        }.body()
    }

    /**
     * Convierte la `TranscodingUrl` (relativa) que devuelve PlaybackInfo en una
     * URL absoluta que libVLC puede abrir (HLS). Ya trae api_key embebido.
     */
    fun resolveTranscodeUrl(transcodingUrl: String): String {
        if (transcodingUrl.startsWith("http", ignoreCase = true)) return transcodingUrl
        val session = sessionStore.session.value ?: return transcodingUrl
        return session.serverUrl.trimEnd('/') + transcodingUrl
    }

    /**
     * Descarga un playlist HLS de transcode y lo devuelve como texto plano con las
     * URLs de segmento ABSOLUTAS, para guardarlo local y pasárselo a VLC como
     * archivo. Necesario porque el cliente HTTP/2 de libVLC NO descomprime el
     * `.m3u8` (Emby lo manda gzip/deflate) → lo lee corrupto → negro. Ktor (OkHttp)
     * sí lo descomprime. Baja master → variante y reescribe los segmentos. Los
     * segmentos `.ts` son binarios (nunca comprimidos), así que VLC los baja bien.
     * Devuelve null si algo falla → el caller cae a la URL remota como estaba.
     */
    suspend fun rewriteHlsPlaylist(masterUrl: String): String? = runCatching {
        val masterText = client.get(masterUrl).bodyAsText()
        val firstUri = masterText.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        // master → variante (main.m3u8). Si la 1ra URI ya es un segmento, el
        // "master" ES el playlist de medios (no hay nivel de variantes).
        val mediaUrl: String
        val mediaText: String
        if (firstUri != null && firstUri.contains(".m3u8", ignoreCase = true)) {
            mediaUrl = resolveRelative(masterUrl, firstUri)
            mediaText = client.get(mediaUrl).bodyAsText()
        } else {
            mediaUrl = masterUrl
            mediaText = masterText
        }
        buildString {
            mediaText.lineSequence().forEach { line ->
                val t = line.trim()
                if (t.isNotEmpty() && !t.startsWith("#")) append(resolveRelative(mediaUrl, t)) else append(line)
                append('\n')
            }
        }
    }.getOrNull()

    /** Resuelve una URL relativa de un playlist HLS contra la absoluta del playlist. */
    private fun resolveRelative(base: String, ref: String): String {
        if (ref.startsWith("http://", true) || ref.startsWith("https://", true)) return ref
        val schemeSep = base.indexOf("://")
        val hostEnd = if (schemeSep >= 0) base.indexOf('/', schemeSep + 3) else -1
        val origin = if (hostEnd >= 0) base.substring(0, hostEnd) else base
        if (ref.startsWith("/")) return origin + ref
        val dir = base.substringBefore('?').substringBeforeLast('/', origin)
        return "$dir/$ref"
    }

    /** Cierra un live stream abierto (libera el tuner del canal). Best-effort. */
    suspend fun closeLiveStream(liveStreamId: String) {
        val session = sessionStore.session.value ?: return
        client.post("${session.serverUrl}/LiveStreams/Close") {
            header("X-Emby-Token", session.accessToken)
            parameter("LiveStreamId", liveStreamId)
        }
    }

    /**
     * URL de reproducción directa (Static), sin transcodificar. Ojo: los params
     * van en minúscula y el `mediaSourceId` es el de PlaybackInfo, no el itemId
     * (mandar el itemId da HTTP 400).
     */
    fun buildStreamUrl(itemId: String, mediaSourceId: String, playSessionId: String): String {
        val session = sessionStore.session.value ?: error("No hay sesión activa")
        return "${session.serverUrl}/Videos/$itemId/stream" +
            "?static=true" +
            "&mediaSourceId=$mediaSourceId" +
            "&playSessionId=$playSessionId" +
            "&api_key=${session.accessToken}" +
            "&DeviceId=${device.deviceId}"
    }

    /**
     * URL de un subtítulo externo. Emby lo convierte on-the-fly según la
     * extensión (`Stream.ass` preserva estilos ASS). El `mediaSourceId` es el
     * de PlaybackInfo, y el `index` el del MediaStream del subtítulo.
     */
    fun buildSubtitleUrl(itemId: String, mediaSourceId: String, index: Int, format: String): String {
        val session = sessionStore.session.value ?: error("No hay sesión activa")
        return "${session.serverUrl}/Videos/$itemId/$mediaSourceId/Subtitles/$index/0/Stream.$format" +
            "?api_key=${session.accessToken}"
    }

    /** Marca/desmarca favorito (POST agrega, DELETE quita). */
    suspend fun setFavorite(userId: String, itemId: String, favorite: Boolean) {
        val session = sessionStore.session.value ?: return
        val url = "${session.serverUrl}/Users/$userId/FavoriteItems/$itemId"
        val h: HttpRequestBuilder.() -> Unit = {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
        }
        if (favorite) client.post(url, h) else client.delete(url, h)
    }

    /** "Refrescar metadatos" (admin): re-baja metadata + imágenes del ítem. Best-effort. */
    suspend fun refreshItem(itemId: String) {
        val session = sessionStore.session.value ?: return
        client.post("${session.serverUrl}/Items/$itemId/Refresh") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            parameter("Recursive", false)
            parameter("MetadataRefreshMode", "FullRefresh")
            parameter("ImageRefreshMode", "FullRefresh")
            parameter("ReplaceAllMetadata", true)
            parameter("ReplaceAllImages", false)
        }
    }

    /**
     * "Sincronizar archivos" (admin): a diferencia de refreshItem NO toca
     * metadata/imágenes (ValidationOnly), solo hace que Emby vuelva a mirar el
     * disco por si se agregaron/movieron/renombraron archivos de este ítem.
     */
    suspend fun syncItemFiles(itemId: String) {
        val session = sessionStore.session.value ?: return
        client.post("${session.serverUrl}/Items/$itemId/Refresh") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            parameter("Recursive", false)
            parameter("MetadataRefreshMode", "ValidationOnly")
            parameter("ImageRefreshMode", "ValidationOnly")
            parameter("ReplaceAllMetadata", false)
            parameter("ReplaceAllImages", false)
        }
    }

    /**
     * "Escanear biblioteca" (admin): el mismo Refresh pero Recursive=true, que
     * baja por todo el árbol de la carpeta de biblioteca (igual que "Scan
     * library files" del panel admin). `libraryId` = Id de la CollectionFolder.
     */
    suspend fun scanLibrary(libraryId: String) {
        val session = sessionStore.session.value ?: return
        client.post("${session.serverUrl}/Items/$libraryId/Refresh") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            parameter("Recursive", true)
            parameter("MetadataRefreshMode", "Default")
            parameter("ImageRefreshMode", "Default")
            parameter("ReplaceAllMetadata", false)
            parameter("ReplaceAllImages", false)
        }
    }

    /**
     * "Quitar de continuar viendo": Emby tiene un endpoint dedicado
     * (HideFromResume) que oculta el ítem de esa fila sin tocar la posición ni
     * el estado visto. Responde 204 sin body.
     */
    suspend fun hideFromResume(userId: String, itemId: String) {
        val session = sessionStore.session.value ?: return
        client.post("${session.serverUrl}/Users/$userId/Items/$itemId/HideFromResume") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            parameter("Hide", true)
        }
    }

    /** Marca/desmarca como visto (POST agrega a PlayedItems, DELETE quita). */
    suspend fun setPlayed(userId: String, itemId: String, played: Boolean) {
        val session = sessionStore.session.value ?: return
        val url = "${session.serverUrl}/Users/$userId/PlayedItems/$itemId"
        val h: HttpRequestBuilder.() -> Unit = {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
        }
        if (played) client.post(url, h) else client.delete(url, h)
    }

    /** Reporte de reproducción hacia `/Sessions/Playing[/Progress|/Stopped]`. */
    suspend fun reportPlayback(path: String, body: PlaybackReport) {
        val session = sessionStore.session.value ?: return
        client.post("${session.serverUrl}$path") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    // ---- Helper autenticado ----

    private suspend inline fun <reified T> authGet(
        path: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val session = sessionStore.session.value
            ?: error("No hay sesión activa")
        return client.get("${session.serverUrl}$path") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            block()
        }.body()
    }

    companion object {
        const val HOME_FIELDS =
            "PrimaryImageAspectRatio,ProductionYear,Status,SeriesName,IndexNumber,ParentIndexNumber,RunTimeTicks,Overview,Genres,OfficialRating,CommunityRating"

        const val DETAIL_FIELDS =
            "Overview,Genres,ProductionYear,OfficialRating,RunTimeTicks,CommunityRating,PremiereDate,Status,SeriesName,IndexNumber,ParentIndexNumber,ChildCount"
    }
}
