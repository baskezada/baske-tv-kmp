package cl.baske.tv.data.remote

import cl.baske.tv.data.SessionStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cliente del plugin EmbySeerr (proxy a Overseerr/Jellyseerr). Solo se referencia
 * desde la pantalla Descubrir (androidApp) bajo `BuildConfig.ENABLE_DISCOVER`;
 * cuando ese flag está off, R8 elimina Descubrir → esta clase queda sin usar y
 * también se elimina del bundle. Autentica con X-Emby-Token; las rutas cuelgan
 * del base de Emby ({serverUrl}/EmbySeerr/...). Devuelve JSON estilo TMDB.
 */
class SeerrApi(
    private val client: HttpClient,
    private val sessionStore: SessionStore,
) {
    private fun base(): String = sessionStore.session.value?.serverUrl?.trimEnd('/') ?: ""
    private fun token(): String = sessionStore.session.value?.accessToken ?: ""

    private suspend inline fun <reified T> get(path: String): T =
        client.get(base() + path) { header("X-Emby-Token", token()) }.body()

    suspend fun trending(page: Int = 1): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Trending?page=$page")

    suspend fun popularMovies(): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Movies?sortBy=popularity.desc&page=1")

    suspend fun popularTv(): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Tv?sortBy=popularity.desc&page=1")

    suspend fun upcomingMovies(): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Movies/Upcoming")

    suspend fun upcomingTv(): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Tv/Upcoming")

    suspend fun seasonalAnime(): SeerrDiscoverResponse =
        get("/EmbySeerr/Anime/Seasonal")

    suspend fun moviesByProvider(providerId: Int, region: String = "US"): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Movies?watchProviders=$providerId&watchRegion=$region&sortBy=popularity.desc")

    suspend fun tvByProvider(providerId: Int, region: String = "US"): SeerrDiscoverResponse =
        get("/EmbySeerr/Discover/Tv?watchProviders=$providerId&watchRegion=$region&sortBy=popularity.desc")

    suspend fun search(query: String, page: Int = 1): SeerrDiscoverResponse =
        get("/EmbySeerr/Search?query=${query.encodeURLParameter()}&page=$page")

    /** Números de temporada de una serie (para solicitar todas). */
    suspend fun tvSeasonNumbers(tmdbId: Int): List<Int> =
        tvSeasons(tmdbId).mapNotNull { it.seasonNumber }

    /**
     * Info de estreno de una película (release_dates de TMDB, prioriza US) para
     * la nota "Solo en cines… / Fecha de estreno…". null si no hay datos.
     */
    suspend fun movieReleaseInfo(tmdbId: Int): MovieReleaseInfo? {
        val results = runCatching { get<SeerrMovieDetail>("/EmbySeerr/Movie/$tmdbId").releases?.results }
            .getOrNull().orEmpty().ifEmpty { return null }
        val us = results.firstOrNull { it.iso == "US" }
        val pools = if (us != null) listOf(us) + results.filter { it !== us } else results
        fun pick(type: Int): String? = pools.firstNotNullOfOrNull { c ->
            c.releaseDates.firstOrNull { it.type == type && !it.date.isNullOrBlank() }?.date
        }
        val digital = pick(4); val physical = pick(5); val theatrical = pick(3) ?: pick(2)
        if (digital == null && physical == null && theatrical == null) return null
        return MovieReleaseInfo(digital, physical, theatrical)
    }

    /** Episodios de una temporada (TMDB) para la lista del detalle. */
    suspend fun seasonEpisodes(tmdbId: Int, seasonNumber: Int): List<SeerrEpisode> =
        runCatching {
            get<SeerrSeasonDetail>("/EmbySeerr/Tv/$tmdbId/Season/$seasonNumber").episodes ?: emptyList()
        }.getOrDefault(emptyList())

    /** Temporadas (num > 0, sin "especiales") de una serie, para el selector. */
    suspend fun tvSeasons(tmdbId: Int): List<SeerrSeason> =
        runCatching {
            get<SeerrTvDetail>("/EmbySeerr/Tv/$tmdbId").seasons
                ?.filter { (it.seasonNumber ?: 0) > 0 }
                ?: emptyList()
        }.getOrDefault(emptyList())

    /** Crea una solicitud en Seerr. Lanza si falla (p. ej. usuario no vinculado). */
    suspend fun requestMedia(mediaType: String, mediaId: Int, seasons: List<Int>? = null) {
        client.post(base() + "/EmbySeerr/Request") {
            header("X-Emby-Token", token())
            contentType(ContentType.Application.Json)
            setBody(SeerrRequestBody(mediaType, mediaId, seasons))
        }
    }
}

@Serializable
private data class SeerrRequestBody(
    @SerialName("MediaType") val mediaType: String,
    @SerialName("MediaId") val mediaId: Int,
    @SerialName("Seasons") val seasons: List<Int>? = null,
)

@Serializable
data class SeerrTvDetail(
    @SerialName("seasons") val seasons: List<SeerrSeason>? = null,
)

@Serializable
data class SeerrSeason(
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("episodeCount") val episodeCount: Int? = null,
)

data class MovieReleaseInfo(val digital: String?, val physical: String?, val theatrical: String?)

@Serializable
data class SeerrMovieDetail(
    @SerialName("releases") val releases: SeerrReleases? = null,
)

@Serializable
data class SeerrReleases(
    @SerialName("results") val results: List<SeerrReleaseCountry> = emptyList(),
)

@Serializable
data class SeerrReleaseCountry(
    @SerialName("iso_3166_1") val iso: String? = null,
    @SerialName("release_dates") val releaseDates: List<SeerrReleaseDate> = emptyList(),
)

@Serializable
data class SeerrReleaseDate(
    @SerialName("type") val type: Int = 0,
    @SerialName("release_date") val date: String? = null,
)

@Serializable
data class SeerrSeasonDetail(
    @SerialName("episodes") val episodes: List<SeerrEpisode>? = null,
)

@Serializable
data class SeerrEpisode(
    @SerialName("episodeNumber") val episodeNumber: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("stillPath") val stillPath: String? = null,
) {
    val stillUrl: String? get() = stillPath?.let { if (it.startsWith("http")) it else "https://image.tmdb.org/t/p/w300$it" }
}

@Serializable
data class SeerrDiscoverResponse(
    @SerialName("page") val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 0,
    @SerialName("results") val results: List<SeerrResult> = emptyList(),
)

@Serializable
data class SeerrResult(
    @SerialName("id") val id: Int = 0,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfo? = null,
) {
    val displayTitle: String get() = title ?: name ?: ""
    val year: String? get() = (releaseDate ?: firstAirDate)?.take(4)?.ifBlank { null }
    val posterUrl: String? get() = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
    val backdropUrl: String? get() = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
    val isPerson: Boolean get() = mediaType == "person"
    val isTv: Boolean get() = mediaType == "tv"

    /** Id del ítem en Emby si ya está en la biblioteca (para abrir su detalle). */
    val embyId: String? get() = mediaInfo?.jellyfinMediaId?.takeIf { it.isNotBlank() }
    /** true si ya está (al menos parcialmente) en la biblioteca. */
    val inLibrary: Boolean get() = embyId != null || mediaInfo?.status == 5 || mediaInfo?.status == 4

    /** Estado en Seerr, si ya está en el catálogo. null = se puede solicitar. */
    val availabilityLabel: String? get() = when (mediaInfo?.status) {
        5 -> "Disponible"
        4 -> "Parcialmente disponible"
        3 -> "Procesando"
        2 -> "Solicitado"
        else -> null
    }
}

@Serializable
data class SeerrMediaInfo(
    @SerialName("status") val status: Int? = null,
    @SerialName("jellyfinMediaId") val jellyfinMediaId: String? = null,
)
