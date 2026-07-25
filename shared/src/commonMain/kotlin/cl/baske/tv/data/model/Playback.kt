package cl.baske.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Body de los reportes de reproducción (`/Sessions/Playing[/Progress|/Stopped]`).
 * Un único tipo sirve para los tres: Emby ignora los campos que sobran. Esto
 * es lo que mantiene vivo "Continuar viendo" y guarda la posición de resume.
 */
/**
 * Respuesta de `POST /Items/{id}/PlaybackInfo`. Da el `Id` real del media
 * source (ej. `mediasource_217`, NO el id del ítem) y un `PlaySessionId` —
 * ambos obligatorios para armar la URL de stream y reportar. Mandar el id del
 * ítem como mediaSourceId da HTTP 400.
 */
@Serializable
data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfo> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
)

@Serializable
data class MediaSourceInfo(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList(),
)

@Serializable
data class MediaStream(
    @SerialName("Index") val index: Int,
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
)

@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("PlayMethod") val playMethod: String = "DirectPlay",
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("IsPaused") val isPaused: Boolean = false,
)
