package cl.baske.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeviceProfile que se manda en `POST /Items/{id}/PlaybackInfo`. Le dice a Emby
 * qué puede reproducir directo este dispositivo y qué debe transcodificar. La
 * estructura replica la del cliente web (probada en vivo contra el mismo Emby);
 * lo que cambia son los códecs, que en Android se arman según lo que el hardware
 * realmente decodifica (ver AndroidDeviceProfileProvider).
 *
 * Sin perfil (o con uno que declare todo direct-play) Emby entrega el archivo
 * crudo y confía en que el cliente lo decodifique — que es lo que fallaba en
 * dispositivos débiles (Chromecast) con códecs pesados: se quedaba buffereando.
 */
@Serializable
data class DeviceProfile(
    @SerialName("MaxStaticBitrate") val maxStaticBitrate: Long = 120_000_000,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long = 120_000_000,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<DirectPlayProfile> = emptyList(),
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<TranscodingProfile> = emptyList(),
    @SerialName("ContainerProfiles") val containerProfiles: List<ProfileEntry> = emptyList(),
    @SerialName("CodecProfiles") val codecProfiles: List<CodecProfile> = emptyList(),
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<SubtitleProfile> = emptyList(),
)

@Serializable
data class DirectPlayProfile(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String = "Video",
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
)

@Serializable
data class TranscodingProfile(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String = "Video",
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String = "hls",
)

@Serializable
data class CodecProfile(
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String,
    @SerialName("Conditions") val conditions: List<ProfileCondition> = emptyList(),
)

@Serializable
data class ProfileCondition(
    @SerialName("Condition") val condition: String,
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val isRequired: Boolean = false,
)

@Serializable
data class SubtitleProfile(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String,
)

/** Placeholder para ContainerProfiles (vacío, pero tipado para serializar). */
@Serializable
class ProfileEntry

/**
 * Body de `POST /Items/{id}/PlaybackInfo` (misma forma que usa el cliente web).
 * Con el DeviceProfile incluido, Emby decide por MediaSource si entrega
 * direct-play (SupportsDirectPlay) o una TranscodingUrl (HLS).
 */
@Serializable
data class PlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("DeviceId") val deviceId: String,
    @SerialName("IsPlayback") val isPlayback: Boolean = true,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean = true,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean = true,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean = true,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long = 120_000_000,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean = true,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean = true,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean = true,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int = -1,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfile,
)
