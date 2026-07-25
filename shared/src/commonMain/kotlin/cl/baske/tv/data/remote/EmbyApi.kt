package cl.baske.tv.data.remote

import cl.baske.tv.core.DeviceInfo
import cl.baske.tv.core.buildEmbyAuthHeader
import cl.baske.tv.data.SessionStore
import cl.baske.tv.data.model.AuthenticateByNameRequest
import cl.baske.tv.data.model.AuthenticationResult
import cl.baske.tv.data.model.BaseItemDto
import cl.baske.tv.data.model.ItemsResponse
import cl.baske.tv.data.model.PlaybackInfoResponse
import cl.baske.tv.data.model.PlaybackReport
import cl.baske.tv.data.model.PublicSystemInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

    /** Próximo episodio a ver de una serie (para el botón Reproducir/Continuar). */
    suspend fun getSeriesNextUp(userId: String, seriesId: String): ItemsResponse =
        authGet("/Shows/NextUp") {
            parameter("UserId", userId)
            parameter("SeriesId", seriesId)
            parameter("Limit", 1)
            parameter("Fields", "IndexNumber,ParentIndexNumber,UserData,RunTimeTicks")
        }

    /** Ítems de una biblioteca, paginado. */
    suspend fun getLibraryItems(
        userId: String,
        parentId: String,
        includeItemTypes: String,
        startIndex: Int,
        limit: Int,
    ): ItemsResponse =
        authGet("/Users/$userId/Items") {
            parameter("ParentId", parentId)
            parameter("Recursive", true)
            if (includeItemTypes.isNotEmpty()) parameter("IncludeItemTypes", includeItemTypes)
            parameter("SortBy", "SortName")
            parameter("SortOrder", "Ascending")
            parameter("Limit", limit)
            parameter("StartIndex", startIndex)
            parameter("Fields", HOME_FIELDS)
            parameter("EnableImageTypes", "Primary,Backdrop,Thumb")
        }

    // ---- Player (requieren sesión) ----

    /** Detalle del ítem: da título, RunTimeTicks y la posición de resume. */
    suspend fun getItem(userId: String, itemId: String): BaseItemDto =
        authGet("/Users/$userId/Items/$itemId")

    /**
     * PlaybackInfo: da el `mediaSourceId` real (mediasource_XXX) y el
     * `PlaySessionId`. Body vacío `{}` — sin DeviceProfile Emby igual habilita
     * direct-play, que es lo que queremos porque libVLC decodifica todo.
     */
    suspend fun getPlaybackInfo(itemId: String, userId: String): PlaybackInfoResponse {
        val session = sessionStore.session.value ?: error("No hay sesión activa")
        return client.post("${session.serverUrl}/Items/$itemId/PlaybackInfo") {
            header("X-Emby-Token", session.accessToken)
            header("X-Emby-Authorization", buildEmbyAuthHeader(device, session.accessToken))
            parameter("UserId", userId)
            contentType(ContentType.Application.Json)
            setBody(emptyMap<String, String>())
        }.body()
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
