package cl.baske.tv.core

import cl.baske.tv.data.model.CodecProfile
import cl.baske.tv.data.model.DeviceProfile
import cl.baske.tv.data.model.DirectPlayProfile
import cl.baske.tv.data.model.ProfileCondition
import cl.baske.tv.data.model.SubtitleProfile
import cl.baske.tv.data.model.TranscodingProfile

/**
 * Provee el DeviceProfile para PlaybackInfo. Cada plataforma lo arma según sus
 * capacidades reales (Android consulta MediaCodec; iOS usa el default amplio).
 */
interface DeviceProfileProvider {
    fun deviceProfile(): DeviceProfile
}

/** Contenedores que libVLC abre sin problema (direct-play). */
private const val VIDEO_CONTAINERS =
    "mp4,m4v,mkv,webm,mov,avi,ts,mpegts,m2ts,flv,3gp,ogv,ogm,mpg,mpeg,wmv,asf,vob,divx"

/** Audio: libVLC decodifica todo esto en software barato → nunca forzamos transcode de audio. */
private const val AUDIO_CODECS =
    "aac,mp3,mp2,ac3,eac3,dts,dca,truehd,flac,alac,opus,vorbis,pcm,pcm_s16le,wmav2,wmapro,ape"

/** Salida de transcode: H264+AAC en HLS es lo universalmente reproducible. */
private const val TRANSCODE_VIDEO = "h264"
private const val TRANSCODE_AUDIO = "aac,mp3"

/** Subtítulos externos de texto (los rasteriza el cliente); imágenes también External para que el server no los queme. */
private val SUBTITLE_FORMATS = listOf("ass", "ssa", "srt", "subrip", "vtt", "sub", "pgssub", "pgs", "dvdsub", "dvbsub", "idx")

/**
 * Arma un DeviceProfile a partir de los códecs de video que el dispositivo puede
 * reproducir directo y (opcional) su resolución máxima por códec. Los códecs
 * fuera de [directVideoCodecs] no se declaran → Emby los transcodifica a H264.
 * Un [resolutionCaps] por códec fuerza transcode si el archivo supera esa
 * resolución (ej. 4K HEVC en un equipo que solo decodifica HEVC 1080p).
 */
fun buildDeviceProfile(
    directVideoCodecs: List<String>,
    resolutionCaps: Map<String, Pair<Int, Int>> = emptyMap(),
): DeviceProfile {
    val videoCodecs = directVideoCodecs.ifEmpty { listOf("h264") }
    val codecProfiles = resolutionCaps.mapNotNull { (codec, cap) ->
        if (codec !in videoCodecs) return@mapNotNull null
        val (w, h) = cap
        CodecProfile(
            type = "Video",
            codec = codec,
            conditions = listOf(
                ProfileCondition("LessThanEqual", "Width", w.toString(), isRequired = true),
                ProfileCondition("LessThanEqual", "Height", h.toString(), isRequired = true),
            ),
        )
    }
    return DeviceProfile(
        directPlayProfiles = listOf(
            DirectPlayProfile(
                container = VIDEO_CONTAINERS,
                videoCodec = videoCodecs.joinToString(","),
                audioCodec = AUDIO_CODECS,
            ),
        ),
        transcodingProfiles = listOf(
            TranscodingProfile(
                container = "ts",
                videoCodec = TRANSCODE_VIDEO,
                audioCodec = TRANSCODE_AUDIO,
                protocol = "hls",
            ),
        ),
        codecProfiles = codecProfiles,
        subtitleProfiles = SUBTITLE_FORMATS.map { SubtitleProfile(it, "External") },
    )
}

/**
 * Default amplio (iOS y fallback): declara direct-play para todos los códecs
 * comunes. Mantiene el comportamiento "libVLC decodifica todo" pero con la red
 * de seguridad del transcode cuando el server marca la fuente como no soportada.
 */
class DefaultDeviceProfileProvider : DeviceProfileProvider {
    override fun deviceProfile(): DeviceProfile =
        buildDeviceProfile(listOf("h264", "hevc", "vp9", "av1", "mpeg4", "mpeg2video", "vc1", "vp8"))
}
