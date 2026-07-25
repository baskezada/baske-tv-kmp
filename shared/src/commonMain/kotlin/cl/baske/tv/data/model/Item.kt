package cl.baske.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Envoltorio de la mayoría de endpoints de listado de Emby. */
@Serializable
data class ItemsResponse(
    @SerialName("Items") val items: List<BaseItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int? = null,
)

/**
 * Subconjunto de `BaseItemDto` que el Home necesita. Json tolerante ignora
 * el resto de campos que manda Emby.
 */
@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("Genres") val genres: List<String>? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerialName("UserData") val userData: UserItemDataDto? = null,
)

@Serializable
data class UserItemDataDto(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
)
